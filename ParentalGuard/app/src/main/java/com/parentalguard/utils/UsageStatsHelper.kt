package com.parentalguard.utils

import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.util.Log
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * ============================================================
 * UsageStatsHelper.kt
 * Package: com.parentalguard.utils
 *
 * PURPOSE:
 *   Reads "app usage statistics" from the Android system —
 *   i.e., which apps were used today and for how long.
 *
 * HOW IT WORKS:
 *   Android tracks app foreground time via UsageStatsManager.
 *   We query it for "today's" time window (midnight → now)
 *   and return the top apps sorted by usage, longest first.
 *
 * IMPORTANT:
 *   This requires the PACKAGE_USAGE_STATS permission, which
 *   CANNOT be granted at runtime like normal permissions.
 *   The user must manually enable it in:
 *   Settings → Digital Wellbeing → (or) Special App Access → Usage Access
 *
 *   Without this permission, getAppUsageStats() returns an empty list.
 * ============================================================
 */
object UsageStatsHelper {

    private const val TAG = "UsageStatsHelper"

    /**
     * Returns a list of apps used today, sorted by usage time (most used first).
     *
     * Each item in the list is a Map with these keys:
     *   "packageName"  → e.g. "com.instagram.android"
     *   "appName"      → e.g. "Instagram"
     *   "totalMinutes" → e.g. 87  (Long)
     *   "formattedTime"→ e.g. "1h 27m"
     *   "lastUsed"     → Unix timestamp (Long) of last interaction
     *
     * @param context Android Context (needed to access system services)
     * @return List of app usage maps, or empty list if permission is denied
     */
    fun getAppUsageStats(context: Context): List<Map<String, Any>> {

        // UsageStatsManager was introduced in API 21 (Android 5.0)
        // Our minSdk is 21 so this check is technically optional,
        // but it's good practice to be explicit.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            Log.w(TAG, "UsageStatsManager not available on this Android version")
            return emptyList()
        }

        // Get the UsageStatsManager system service.
        // The "as?" operator safely casts — returns null if the cast fails.
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE)
                as? UsageStatsManager
            ?: run {
                Log.e(TAG, "Could not get UsageStatsManager")
                return emptyList()
            }

        // ----------------------------------------------------------
        // Define the time window: midnight today → right now
        // ----------------------------------------------------------
        val calendar = Calendar.getInstance()
        val endTime = calendar.timeInMillis  // now

        // Set calendar back to midnight (00:00:00) of today
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startTime = calendar.timeInMillis  // midnight today

        // ----------------------------------------------------------
        // Query usage stats for the time window
        // INTERVAL_DAILY = pre-aggregated daily totals (most efficient)
        // ----------------------------------------------------------
        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startTime,
            endTime
        )

        if (stats == null || stats.isEmpty()) {
            Log.w(TAG, "No usage stats returned — permission may not be granted")
            return emptyList()
        }

        val packageManager = context.packageManager

        // ----------------------------------------------------------
        // Process the raw stats into a clean, readable format
        // ----------------------------------------------------------
        return stats
            // Filter out apps with zero foreground time (never opened today)
            .filter { it.totalTimeInForeground > 0 }

            // Sort so the most-used app comes first
            .sortedByDescending { it.totalTimeInForeground }

            // Only keep the top N apps (avoids cluttering the dashboard)
            .take(AppConfig.MAX_SCREEN_TIME_APPS)

            // Convert each UsageStats object into a readable Map.
            // mapNotNull = skip entries that return null (system apps we can't resolve)
            .mapNotNull { stat ->
                try {
                    // Look up the human-readable app name using its package name.
                    // This throws NameNotFoundException if the app was uninstalled.
                    val appInfo = packageManager.getApplicationInfo(stat.packageName, 0)
                    val appName = packageManager.getApplicationLabel(appInfo).toString()

                    // Convert milliseconds → minutes
                    val totalMinutes = TimeUnit.MILLISECONDS.toMinutes(stat.totalTimeInForeground)

                    // Format as "Xh Ym" or just "Ym" if under an hour
                    val hours = totalMinutes / 60
                    val minutes = totalMinutes % 60
                    val formattedTime = if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"

                    // Build the data map that will be sent to Firebase
                    mapOf(
                        "packageName"   to stat.packageName,
                        "appName"       to appName,
                        "totalMinutes"  to totalMinutes,
                        "formattedTime" to formattedTime,
                        "lastUsed"      to stat.lastTimeUsed  // Unix ms timestamp
                    )

                } catch (e: Exception) {
                    // Skip system packages or recently-uninstalled apps
                    // that packageManager can't resolve to a name
                    null
                }
            }
    }

    /**
     * Returns the TOTAL screen time across all apps today, in minutes.
     * Convenience wrapper around getAppUsageStats().
     *
     * @param context Android Context
     * @return Total minutes of screen time today
     */
    fun getTotalScreenTimeMinutes(context: Context): Long {
        return getAppUsageStats(context)
            .sumOf { (it["totalMinutes"] as? Long) ?: 0L }
    }
}
