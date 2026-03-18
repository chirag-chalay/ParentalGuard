package com.parentalguard.utils

import android.util.Log
import com.google.firebase.database.FirebaseDatabase
import com.parentalguard.utils.AppConfig.DEVICE_ID
import com.parentalguard.utils.AppConfig.FB_PATH_CALL_LOGS
import com.parentalguard.utils.AppConfig.FB_PATH_CURRENT_LOCATION
import com.parentalguard.utils.AppConfig.FB_PATH_DEVICES
import com.parentalguard.utils.AppConfig.FB_PATH_DEVICE_INFO
import com.parentalguard.utils.AppConfig.FB_PATH_LOCATION_HISTORY
import com.parentalguard.utils.AppConfig.FB_PATH_SCREEN_TIME

/**
 * ============================================================
 * FirebaseHelper.kt
 * Package: com.parentalguard.utils
 *
 * PURPOSE:
 *   A helper "object" (Kotlin singleton) that contains every
 *   function which writes data to Firebase Realtime Database.
 *
 *   By keeping ALL Firebase calls here, the Service classes
 *   stay clean and simple — they just call helper functions
 *   instead of dealing with Firebase SDK details themselves.
 *
 * FIREBASE STRUCTURE THIS CLASS WRITES TO:
 *
 *   devices/
 *     daughters_phone/
 *       deviceInfo/
 *         model: "Samsung Galaxy A54"
 *         os: "14"
 *         lastSeen: "2024-01-15 14:30:00"
 *       currentLocation/
 *         latitude: 26.9124
 *         longitude: 75.7873
 *         accuracy: 15.0
 *         timestamp: "2024-01-15 14:30:00"
 *       locationHistory/
 *         -NxAbc123/         ← auto-generated key
 *           latitude: 26.9124
 *           ...
 *       callLogs/
 *         lastUpdated: "..."
 *         calls: [...]
 *       screenTime/
 *         lastUpdated: "..."
 *         apps: [...]
 * ============================================================
 */
object FirebaseHelper {

    // TAG is used for filtering log messages in Logcat
    private const val TAG = "FirebaseHelper"

    /**
     * Get a reference to the root of our device's data in Firebase.
     * All other functions use this as their starting point.
     *
     * Example path: "devices/daughters_phone"
     */
    private fun deviceRef() =
        FirebaseDatabase.getInstance().reference
            .child(FB_PATH_DEVICES)
            .child(DEVICE_ID)

    // ----------------------------------------------------------
    // DEVICE INFO
    // ----------------------------------------------------------

    /**
     * Saves basic information about the monitored phone.
     * Called once when the app first starts.
     *
     * @param model   e.g. "Samsung Galaxy A54"
     * @param osVersion e.g. "14"
     * @param appVersion e.g. "1.0.0"
     */
    fun saveDeviceInfo(model: String, osVersion: String, appVersion: String) {
        val timestamp = getCurrentTimestamp()

        val info = mapOf(
            "model"       to model,
            "os"          to osVersion,
            "appVersion"  to appVersion,
            "deviceId"    to DEVICE_ID,
            "lastSeen"    to timestamp
        )

        deviceRef()
            .child(FB_PATH_DEVICE_INFO)
            .setValue(info)
            .addOnSuccessListener {
                Log.d(TAG, "Device info saved successfully")
            }
            .addOnFailureListener { error ->
                // Failure usually means no internet connection.
                // The data will be sent next time there is connectivity
                // because Firebase queues writes while offline.
                Log.e(TAG, "Failed to save device info: ${error.message}")
            }
    }

    // ----------------------------------------------------------
    // LOCATION
    // ----------------------------------------------------------

    /**
     * Saves the device's CURRENT location (overwrites the previous value).
     * The parent dashboard shows this as the "live" location dot on the map.
     *
     * @param lat      GPS latitude
     * @param lng      GPS longitude
     * @param accuracy GPS accuracy in metres (lower = more precise)
     * @param speed    Current speed in m/s (0 if stationary)
     */
    fun saveCurrentLocation(lat: Double, lng: Double, accuracy: Float, speed: Float) {
        val timestamp = getCurrentTimestamp()

        // Build a Map — Firebase stores data as key-value pairs
        val locationData = mapOf(
            "latitude"  to lat,
            "longitude" to lng,
            "accuracy"  to accuracy,
            "speed"     to (speed * 3.6).toInt(),  // convert m/s → km/h
            "timestamp" to timestamp,
            "deviceId"  to DEVICE_ID
        )

        // Write to "devices/daughters_phone/currentLocation"
        // setValue() REPLACES the entire node each time
        deviceRef()
            .child(FB_PATH_CURRENT_LOCATION)
            .setValue(locationData)
            .addOnSuccessListener {
                Log.d(TAG, "Current location saved: $lat, $lng")
            }
            .addOnFailureListener { error ->
                Log.e(TAG, "Failed to save location: ${error.message}")
            }
    }

    /**
     * Appends a location entry to the history list.
     * Unlike saveCurrentLocation(), this ADDS a new entry rather
     * than overwriting — building up a timeline of where the
     * phone has been throughout the day.
     *
     * Firebase auto-generates a unique key using push().key,
     * so entries don't overwrite each other.
     *
     * @param lat      GPS latitude
     * @param lng      GPS longitude
     * @param accuracy GPS accuracy in metres
     */
    fun appendLocationHistory(lat: Double, lng: Double, accuracy: Float) {
        val timestamp = getCurrentTimestamp()

        val historyEntry = mapOf(
            "latitude"  to lat,
            "longitude" to lng,
            "accuracy"  to accuracy,
            "timestamp" to timestamp
        )

        // push() creates a child node with a unique auto-generated key
        // This prevents overwriting existing history entries
        val historyRef = deviceRef().child(FB_PATH_LOCATION_HISTORY)
        val newEntryKey = historyRef.push().key

        if (newEntryKey != null) {
            historyRef.child(newEntryKey)
                .setValue(historyEntry)
                .addOnSuccessListener {
                    Log.d(TAG, "Location history entry added: $newEntryKey")
                }
                .addOnFailureListener { error ->
                    Log.e(TAG, "Failed to add history: ${error.message}")
                }
        } else {
            Log.e(TAG, "Could not generate Firebase push key — no internet?")
        }
    }

    // ----------------------------------------------------------
    // CALL LOGS
    // ----------------------------------------------------------

    /**
     * Uploads the full call log list to Firebase.
     * This REPLACES the previous call log data entirely each sync.
     *
     * @param calls A list of maps, where each map represents one call.
     *              Example map:
     *              {
     *                "number": "+91 98765 43210",
     *                "name": "Priya",
     *                "type": "Incoming",
     *                "date": "2024-01-15 14:30:00",
     *                "duration": "05:23",
     *                "durationSeconds": 323
     *              }
     */
    fun saveCallLogs(calls: List<Map<String, Any>>) {
        val timestamp = getCurrentTimestamp()

        val payload = mapOf(
            "calls"       to calls,
            "lastUpdated" to timestamp,
            "totalCalls"  to calls.size
        )

        deviceRef()
            .child(FB_PATH_CALL_LOGS)
            .setValue(payload)
            .addOnSuccessListener {
                Log.d(TAG, "Call logs saved: ${calls.size} entries")
            }
            .addOnFailureListener { error ->
                Log.e(TAG, "Failed to save call logs: ${error.message}")
            }
    }

    // ----------------------------------------------------------
    // SCREEN TIME
    // ----------------------------------------------------------

    /**
     * Uploads today's app usage statistics to Firebase.
     * This REPLACES the previous screen time data each sync.
     *
     * @param apps A list of maps, where each map is one app's usage.
     *             Example map:
     *             {
     *               "appName": "Instagram",
     *               "packageName": "com.instagram.android",
     *               "totalMinutes": 87,
     *               "formattedTime": "1h 27m",
     *               "lastUsed": 1705312200000  (Unix timestamp)
     *             }
     */
    fun saveScreenTime(apps: List<Map<String, Any>>) {
        val timestamp = getCurrentTimestamp()
        val todayDate = getTodayDate()

        // Calculate total minutes across all apps
        val totalMinutes = apps.sumOf { (it["totalMinutes"] as? Long) ?: 0L }

        val payload = mapOf(
            "apps"         to apps,
            "lastUpdated"  to timestamp,
            "date"         to todayDate,
            "totalMinutes" to totalMinutes
        )

        deviceRef()
            .child(FB_PATH_SCREEN_TIME)
            .setValue(payload)
            .addOnSuccessListener {
                Log.d(TAG, "Screen time saved: $totalMinutes total minutes across ${apps.size} apps")
            }
            .addOnFailureListener { error ->
                Log.e(TAG, "Failed to save screen time: ${error.message}")
            }
    }

    // ----------------------------------------------------------
    // DATE/TIME UTILITIES (private helpers)
    // ----------------------------------------------------------

    /**
     * Returns the current date and time as a readable string.
     * Example output: "2024-01-15 14:30:00"
     */
    private fun getCurrentTimestamp(): String {
        val formatter = java.text.SimpleDateFormat(
            "yyyy-MM-dd HH:mm:ss",
            java.util.Locale.getDefault()
        )
        return formatter.format(java.util.Date())
    }

    /**
     * Returns today's date only (no time).
     * Example output: "2024-01-15"
     * Used to label the screen time data with the date it was recorded.
     */
    private fun getTodayDate(): String {
        val formatter = java.text.SimpleDateFormat(
            "yyyy-MM-dd",
            java.util.Locale.getDefault()
        )
        return formatter.format(java.util.Date())
    }
}
