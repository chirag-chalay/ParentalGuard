package com.parentalguard.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.parentalguard.services.CallLogSyncService
import com.parentalguard.services.LocationTrackingService

/**
 * ============================================================
 * BootReceiver.kt
 * Package: com.parentalguard.receivers
 *
 * PURPOSE:
 *   Automatically restarts our background services when the
 *   phone boots up (is turned on or restarted).
 *
 * HOW BROADCAST RECEIVERS WORK:
 *   A BroadcastReceiver is a component that listens for
 *   system-wide "broadcasts" — events published by the OS or
 *   other apps via an Intent.
 *
 *   When the device finishes booting, Android broadcasts:
 *     Intent.ACTION_BOOT_COMPLETED
 *
 *   Android finds all receivers registered for this action
 *   (in AndroidManifest.xml) and calls their onReceive() method.
 *
 *   Our receiver then restarts both monitoring services.
 *
 * WHY THIS IS NECESSARY:
 *   Background services do NOT survive a device reboot.
 *   Without this receiver, the monitoring would stop every time
 *   the phone is turned off and on again.
 *
 * PERMISSION REQUIRED:
 *   RECEIVE_BOOT_COMPLETED must be in AndroidManifest.xml,
 *   otherwise Android won't deliver the boot broadcast to us.
 *
 * IMPORTANT:
 *   onReceive() must complete VERY quickly (under 10 seconds).
 *   It runs on the main thread. Only start services here —
 *   never do heavy work like network calls inside onReceive().
 * ============================================================
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    /**
     * Called by the Android system when a broadcast matching our
     * intent-filter (in AndroidManifest.xml) is received.
     *
     * @param context The app's Context — needed to start services
     * @param intent  The broadcast intent that triggered this call.
     *                intent.action will be ACTION_BOOT_COMPLETED
     *                or ACTION_QUICKBOOT_POWERON (on some devices)
     */
    override fun onReceive(context: Context, intent: Intent) {

        // Safety check: make sure this is actually a boot event.
        // (Although our manifest filter should ensure this, it's
        // good defensive practice to verify the action.)
        val action = intent.action
        val isBootEvent = action == Intent.ACTION_BOOT_COMPLETED ||
                action == "android.intent.action.QUICKBOOT_POWERON"

        if (!isBootEvent) {
            Log.w(TAG, "Received unexpected action: $action — ignoring")
            return
        }

        Log.d(TAG, "Device boot detected — starting monitoring services")

        // Restart the GPS location tracking service
        startServiceCompat(context, Intent(context, LocationTrackingService::class.java))

        // Restart the call log + screen time sync service
        startServiceCompat(context, Intent(context, CallLogSyncService::class.java))

        Log.d(TAG, "Both services started successfully after boot")
    }

    /**
     * Helper function that starts a service using the correct method
     * depending on the Android version.
     *
     * On Android 8.0+ (API 26+):
     *   - Apps cannot start background services directly anymore.
     *   - Must use startForegroundService() instead, which gives
     *     the service 5 seconds to call startForeground() and show
     *     a notification before the OS kills it.
     *
     * On Android 7.1 and below:
     *   - Regular startService() works fine.
     *
     * @param context App context
     * @param intent  The service to start
     */
    private fun startServiceCompat(context: Context, intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }
}
