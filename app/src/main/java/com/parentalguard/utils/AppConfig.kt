package com.parentalguard.utils

/**
 * ============================================================
 * AppConfig.kt
 * Package: com.parentalguard.utils
 *
 * PURPOSE:
 *   A single place to store all configurable values for the app.
 *   Instead of scattering magic numbers and strings across many
 *   files, we define them here so they're easy to find and change.
 *
 * HOW TO USE:
 *   Import this file in any class that needs these values:
 *   → import com.parentalguard.utils.AppConfig
 *   Then access values like: AppConfig.LOCATION_INTERVAL_MS
 * ============================================================
 */
object AppConfig {

    // ----------------------------------------------------------
    // DEVICE IDENTITY
    // A unique name for the device being monitored.
    // This becomes the key in your Firebase database, so each
    // phone you monitor needs a DIFFERENT value here.
    //
    // Example Firebase structure:
    //   devices/
    //     daughters_phone/   ← this is DEVICE_ID
    //       currentLocation/
    //       callLogs/
    //       screenTime/
    //
    // IMPORTANT: Change this before installing on a second device!
    // ----------------------------------------------------------
    const val DEVICE_ID = "daughters_phone"

    // ----------------------------------------------------------
    // LOCATION SETTINGS
    // ----------------------------------------------------------

    /**
     * How often (in milliseconds) to request a new GPS location.
     * 5 minutes = 5 × 60 × 1000 = 300,000 ms
     *
     * Increasing this value saves more battery but gives less
     * frequent location updates on the parent dashboard.
     */
    const val LOCATION_INTERVAL_MS = 5 * 60 * 1000L   // 5 minutes

    /**
     * The smallest acceptable update interval.
     * Even if the device CAN provide a location faster, we won't
     * request one more often than this.
     */
    const val LOCATION_FASTEST_INTERVAL_MS = 2 * 60 * 1000L  // 2 minutes

    /**
     * How many location history entries to store per day.
     * At 5-minute intervals: 60min/5 × 24h = 288 entries/day max.
     */
    const val MAX_HISTORY_ENTRIES_PER_DAY = 300

    // ----------------------------------------------------------
    // SYNC SETTINGS
    // ----------------------------------------------------------

    /**
     * How often (ms) the CallLogSyncService runs to send
     * call logs and screen time data to Firebase.
     * Matches the location interval for simplicity.
     */
    const val SYNC_INTERVAL_MS = 5 * 60 * 1000L   // 5 minutes

    /**
     * Maximum number of recent calls to sync each time.
     * Keeping this at 100 avoids sending too much data at once.
     */
    const val MAX_CALL_LOG_ENTRIES = 100

    /**
     * Maximum number of apps to include in screen time report.
     * Top 20 apps covers the vast majority of actual usage.
     */
    const val MAX_SCREEN_TIME_APPS = 20

    // ----------------------------------------------------------
    // NOTIFICATION IDs
    // Every ongoing (foreground) notification needs a unique ID.
    // Android uses these to identify which notification to update
    // or remove. Using constants prevents accidental duplicates.
    // ----------------------------------------------------------
    const val NOTIFICATION_ID_LOCATION   = 1001  // LocationTrackingService
    const val NOTIFICATION_ID_CALL_SYNC  = 1002  // CallLogSyncService

    // ----------------------------------------------------------
    // NOTIFICATION CHANNEL IDs
    // Android 8.0+ groups notifications into "channels".
    // Each channel can have its own sound, importance, and settings.
    // Users can disable individual channels in phone Settings.
    // ----------------------------------------------------------
    const val CHANNEL_ID_LOCATION  = "channel_location_tracking"
    const val CHANNEL_ID_SYNC      = "channel_data_sync"

    // ----------------------------------------------------------
    // FIREBASE DATABASE PATHS
    // These are the paths where data is written in Firebase.
    // Centralising them here means a path change only needs
    // to be made in one place.
    // ----------------------------------------------------------
    const val FB_PATH_DEVICES          = "devices"
    const val FB_PATH_CURRENT_LOCATION = "currentLocation"
    const val FB_PATH_LOCATION_HISTORY = "locationHistory"
    const val FB_PATH_CALL_LOGS        = "callLogs"
    const val FB_PATH_SCREEN_TIME      = "screenTime"
    const val FB_PATH_DEVICE_INFO      = "deviceInfo"
}
