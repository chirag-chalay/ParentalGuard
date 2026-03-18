package com.parentalguard.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import com.parentalguard.utils.AppConfig
import com.parentalguard.utils.FirebaseHelper

/**
 * ============================================================
 * LocationTrackingService.kt
 * Package: com.parentalguard.services
 *
 * PURPOSE:
 *   A background Service that continuously requests GPS location
 *   every 5 minutes and sends the coordinates to Firebase.
 *
 * WHY A FOREGROUND SERVICE?
 *   Android aggressively kills background processes to save battery.
 *   A "Foreground Service" is protected from being killed because it
 *   shows a persistent notification to the user — telling them something
 *   is running. This is an Android requirement, not optional.
 *
 * HOW GPS WORKS HERE:
 *   We use FusedLocationProviderClient (from Google Play Services)
 *   instead of raw Android LocationManager because it:
 *     - Automatically chooses the best signal source (GPS, Wi-Fi, cell)
 *     - Is much more battery efficient
 *     - Provides accuracy estimates
 *
 * LIFECYCLE:
 *   onCreate()    → set up notification channel + location client
 *   onStartCommand() → called each time startService() is called;
 *                      starts location updates
 *   onDestroy()   → remove location callbacks; restart self
 * ============================================================
 */
class LocationTrackingService : Service() {

    companion object {
        private const val TAG = "LocationTrackingService"
    }

    // FusedLocationProviderClient is the Google Play Services API
    // for getting device location efficiently.
    // lateinit = we'll initialize this in onCreate(), not at declaration.
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    // LocationCallback is the object that receives each new location.
    // We store it as a field so we can unregister it in onDestroy().
    private lateinit var locationCallback: LocationCallback

    // ----------------------------------------------------------
    // SERVICE LIFECYCLE — onCreate()
    // Called ONCE when the service is first created.
    // ----------------------------------------------------------
    override fun onCreate() {
        super.onCreate()

        // STEP 1: Create the notification channel (required on Android 8.0+)
        createNotificationChannel()

        // STEP 2: Start as a Foreground Service with a persistent notification.
        // This MUST be called within 5 seconds of onCreate() on Android 12+,
        // or the system will throw an ANR (App Not Responding) exception.
        startForeground(
            AppConfig.NOTIFICATION_ID_LOCATION,
            buildNotification()
        )

        // STEP 3: Get the FusedLocationProviderClient instance
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // STEP 4: Set up the callback that fires whenever a location arrives
        setupLocationCallback()

        Log.d(TAG, "LocationTrackingService created")
    }

    // ----------------------------------------------------------
    // SERVICE LIFECYCLE — onStartCommand()
    // Called every time startService() or startForegroundService()
    // is called (including on device reboot via BootReceiver).
    // ----------------------------------------------------------
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Begin requesting location updates
        startLocationUpdates()

        // START_STICKY tells Android: if this service is killed to
        // free memory, recreate it as soon as resources are available.
        // The intent will be null when recreated this way.
        return START_STICKY
    }

    // ----------------------------------------------------------
    // onBind() — required override, but we don't use binding.
    // Services can be "bound" (connected to a UI) or "started"
    // (independent background tasks). We use "started" mode.
    // ----------------------------------------------------------
    override fun onBind(intent: Intent?): IBinder? = null

    // ----------------------------------------------------------
    // LOCATION SETUP
    // ----------------------------------------------------------

    /**
     * Creates the LocationCallback object.
     * This callback fires every time FusedLocationProvider
     * has a new location ready for us.
     */
    private fun setupLocationCallback() {
        locationCallback = object : LocationCallback() {
            /**
             * Called by the system when one or more locations are available.
             * In practice, result.lastLocation is almost always non-null.
             */
            override fun onLocationResult(result: LocationResult) {
                val location: Location = result.lastLocation ?: return

                Log.d(TAG, "New location: ${location.latitude}, ${location.longitude} " +
                        "(accuracy: ${location.accuracy}m)")

                // Send the location to Firebase (both current + history)
                sendLocationToFirebase(location)
            }
        }
    }

    /**
     * Registers with FusedLocationProviderClient to start receiving
     * location updates at the configured interval.
     */
    private fun startLocationUpdates() {
        // LocationRequest.Builder configures HOW we want location updates
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,   // Use GPS for best accuracy
            AppConfig.LOCATION_INTERVAL_MS     // Desired update interval (5 min)
        ).apply {
            // Don't update FASTER than this, even if the device moves a lot
            setMinUpdateIntervalMillis(AppConfig.LOCATION_FASTEST_INTERVAL_MS)

            // Don't wait for the most accurate fix if a decent one is available.
            // This avoids long delays when GPS is slow to acquire signal.
            setWaitForAccurateLocation(false)
        }.build()

        try {
            // Register for updates on the main thread's Looper.
            // The callback will fire on the main thread.
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
            Log.d(TAG, "Location updates started — interval: ${AppConfig.LOCATION_INTERVAL_MS / 1000}s")

        } catch (e: SecurityException) {
            // This happens if the user revoked the location permission
            // while the service was already running.
            Log.e(TAG, "Location permission was revoked: ${e.message}")
        }
    }

    // ----------------------------------------------------------
    // FIREBASE UPLOAD
    // ----------------------------------------------------------

    /**
     * Sends the location to Firebase — both the "current" location
     * (for the live map on the dashboard) and appends to history
     * (for the timeline/trail view).
     *
     * @param location The Location object from FusedLocationProvider
     */
    private fun sendLocationToFirebase(location: Location) {
        // Update the "live" location (overwrites previous value)
        FirebaseHelper.saveCurrentLocation(
            lat      = location.latitude,
            lng      = location.longitude,
            accuracy = location.accuracy,
            speed    = location.speed
        )

        // Append to location history (adds a new entry)
        FirebaseHelper.appendLocationHistory(
            lat      = location.latitude,
            lng      = location.longitude,
            accuracy = location.accuracy
        )
    }

    // ----------------------------------------------------------
    // NOTIFICATION (required to keep the service alive)
    // ----------------------------------------------------------

    /**
     * Creates a notification "channel" — a category for notifications.
     * Required on Android 8.0+ (API 26+). Without a channel, the
     * notification cannot be shown and the foreground service will crash.
     *
     * Channels only need to be created ONCE — Android caches them.
     * Calling this multiple times is safe (it's a no-op after the first time).
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                AppConfig.CHANNEL_ID_LOCATION,       // Unique channel ID
                "Location Tracking",                  // Name shown in Settings
                NotificationManager.IMPORTANCE_LOW    // Low = no sound, minimal intrusion
            ).apply {
                description = "Tracks location in the background"
                setShowBadge(false)  // Don't show a badge count on the app icon
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Builds the persistent notification that shows in the status bar
     * while this service is running.
     *
     * We keep this minimal and generic ("System Service") to avoid
     * drawing attention to the monitoring app.
     */
    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, AppConfig.CHANNEL_ID_LOCATION)
            .setContentTitle("System Service")    // Generic title
            .setContentText("Running...")          // Generic text
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)   // Prevent user from swiping it away
            .setSilent(true)    // No sound or vibration
            .build()
    }

    // ----------------------------------------------------------
    // SERVICE LIFECYCLE — onDestroy()
    // Called when the service is about to be destroyed.
    // (by the OS due to memory pressure, or by stopService())
    // ----------------------------------------------------------
    override fun onDestroy() {
        super.onDestroy()

        // Unregister the location callback to prevent memory leaks
        fusedLocationClient.removeLocationUpdates(locationCallback)

        Log.d(TAG, "LocationTrackingService destroyed — restarting...")

        // Self-restart: immediately start ourselves again.
        // This provides resilience against the OS killing the service.
        // Combined with START_STICKY and BootReceiver, this gives us
        // three layers of "keep alive" protection.
        startService(Intent(this, LocationTrackingService::class.java))
    }
}
