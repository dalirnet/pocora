package ir.pocora.agent

import android.Manifest
import android.app.AppOpsManager
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import ir.pocora.debug.FileLogger
import ir.pocora.model.AppDay
import java.time.LocalDate
import java.time.ZoneId

// Screen time and data per app, read from Android's own history. Both need the usage access the child grants in setup.
class UsageReader(
    private val context: Context,
) {
    companion object {
        private const val TAG = "UsageReader"

        // ACTIVITY_RESUMED and ACTIVITY_PAUSED from Android 10. Android 9 sends the same numbers under older names.
        private const val EVENT_RESUMED = 1
        private const val EVENT_PAUSED = 2

        // The network kinds the data history is kept under: ConnectivityManager's TYPE_WIFI and TYPE_MOBILE.
        private const val NETWORK_WIFI = 1
        private const val NETWORK_MOBILE = 0
    }

    private val usageStats = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    private val networkStats = context.getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager
    private val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager

    fun hasAccess(): Boolean {
        val mode =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
            }
        // Some phones leave the switch at "default" and grant the permission instead, which Android counts the same.
        return when (mode) {
            AppOpsManager.MODE_ALLOWED -> {
                true
            }

            AppOpsManager.MODE_DEFAULT -> {
                context.checkSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS) ==
                    PackageManager.PERMISSION_GRANTED
            }

            else -> {
                false
            }
        }
    }

    // Screen time and data for every app and every day from the first date to today.
    fun days(
        first: LocalDate,
        today: LocalDate,
        now: Long,
    ): List<AppDay> {
        if (!hasAccess()) return emptyList()
        val result = mutableMapOf<Pair<String, Long>, AppDay>()
        var date = first
        while (!date.isAfter(today)) {
            val start = startOf(date)
            val end = minOf(startOf(date.plusDays(1)), now)
            for ((app, milliseconds) in screenTime(start, end)) {
                val key = app to date.toEpochDay()
                result[key] = (result[key] ?: AppDay(app, date.toEpochDay())).copy(screenMilliseconds = milliseconds)
            }
            for ((app, bytes) in data(start, end)) {
                val key = app to date.toEpochDay()
                result[key] = (result[key] ?: AppDay(app, date.toEpochDay())).let { it.copy(bytes = it.bytes + bytes) }
            }
            date = date.plusDays(1)
        }
        return result.values.toList()
    }

    // The apps that came to the screen in this time, with when. For the watch list.
    fun opened(
        start: Long,
        end: Long,
    ): List<Pair<String, Long>> {
        if (!hasAccess()) return emptyList()
        val opened = mutableListOf<Pair<String, Long>>()
        val events = usageStats.queryEvents(start, end)
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == EVENT_RESUMED) opened += event.packageName to event.timeStamp
        }
        return opened
    }

    // Time on screen, from each app's foreground and background events. An app still open counts up to the end.
    private fun screenTime(
        start: Long,
        end: Long,
    ): Map<String, Long> {
        val totals = mutableMapOf<String, Long>()
        val since = mutableMapOf<String, Long>()
        val events = usageStats.queryEvents(start, end)
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            when (event.eventType) {
                EVENT_RESUMED -> {
                    since.putIfAbsent(event.packageName, event.timeStamp)
                }

                EVENT_PAUSED -> {
                    val opened = since.remove(event.packageName) ?: start
                    totals.merge(event.packageName, event.timeStamp - opened, Long::plus)
                }
            }
        }
        for ((app, opened) in since) totals.merge(app, end - opened, Long::plus)
        return totals.filterValues { it > 0 }
    }

    // Data per app over Wi-Fi and mobile, from the system's per-app counters.
    private fun data(
        start: Long,
        end: Long,
    ): Map<String, Long> {
        val byUid = mutableMapOf<Int, Long>()
        for (type in listOf(NETWORK_WIFI, NETWORK_MOBILE)) {
            try {
                val stats = networkStats.querySummary(type, null, start, end)
                try {
                    val bucket = NetworkStats.Bucket()
                    while (stats.hasNextBucket()) {
                        stats.getNextBucket(bucket)
                        byUid.merge(bucket.uid, bucket.rxBytes + bucket.txBytes, Long::plus)
                    }
                } finally {
                    stats.close()
                }
            } catch (error: SecurityException) {
                FileLogger.w(TAG, "No access to data history", error)
            } catch (error: RuntimeException) {
                FileLogger.w(TAG, "Data history failed", error)
            }
        }
        val packageManager = context.packageManager
        val byApp = mutableMapOf<String, Long>()
        for ((uid, bytes) in byUid) {
            // Apps that share a uid share its data. The first one shows it.
            val app = packageManager.getPackagesForUid(uid)?.firstOrNull() ?: continue
            byApp.merge(app, bytes, Long::plus)
        }
        return byApp
    }

    private fun startOf(date: LocalDate): Long = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
}
