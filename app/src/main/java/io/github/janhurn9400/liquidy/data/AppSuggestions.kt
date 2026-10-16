package io.github.janhurn9400.liquidy.data

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import org.json.JSONArray
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min

/**
 * App suggestions on the device: scores past app launches by recency, frequency and time of day
 * (whoever always opens the same app at 8 in the morning gets it suggested in the morning).
 *
 * Source: Android usage statistics if "Usage access" is allowed (also records launches outside
 * the launcher), otherwise our own launch log. Nothing leaves the device.
 */
class AppSuggestions(private val context: Context) {
    private val prefs = context.getSharedPreferences("suggestions", Context.MODE_PRIVATE)

    /** Own log: pair (app key, time), oldest first. */
    private var log: List<Pair<String, Long>> = load()

    fun record(key: String, now: Long = System.currentTimeMillis()) {
        log = (log + (key to now)).filter { now - it.second < WINDOW_MS }.takeLast(LOG_MAX)
        prefs.edit().putString(LOG, JSONArray(log.map { JSONArray().put(it.first).put(it.second) }).toString()).apply()
    }

    fun hasUsageAccess(): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java) ?: return false
        return ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) ==
            AppOpsManager.MODE_ALLOWED
    }

    /** App keys by probability of being opened now (best first). Blocks briefly — not on the UI thread. */
    fun rank(apps: Map<String, AppEntry>, now: Long = System.currentTimeMillis()): List<String> {
        val events = usageEvents(apps, now) ?: log
        if (events.isEmpty()) return emptyList()
        val nowCal = Calendar.getInstance().apply { timeInMillis = now }
        val nowHour = nowCal.get(Calendar.HOUR_OF_DAY) + nowCal.get(Calendar.MINUTE) / 60f
        val nowWeekend = nowCal.isWeekend()
        val cal = Calendar.getInstance()
        val score = HashMap<String, Float>()
        for ((key, t) in events) {
            if (key !in apps) continue
            val age = (now - t).coerceAtLeast(0L).toFloat()
            cal.timeInMillis = t
            val hour = cal.get(Calendar.HOUR_OF_DAY) + cal.get(Calendar.MINUTE) / 60f
            val dh = abs(hour - nowHour).let { min(it, 24f - it) }
            // Base weight: frequency, decaying over ~1 week
            var w = exp(-age / DECAY_MS)
            // Same time of day (±2 h) and same day type (weekday/weekend) count more
            w *= 1f + TIME_WEIGHT * (1f - dh / TIME_SPAN_H).coerceAtLeast(0f)
            if (cal.isWeekend() == nowWeekend) w *= DAYTYPE_WEIGHT
            // Just used: a short bonus so the last app is right back
            w += RECENT_WEIGHT * exp(-age / RECENT_MS)
            score[key] = (score[key] ?: 0f) + w
        }
        return score.entries.sortedByDescending { it.value }.map { it.key }
    }

    /** Foreground sessions from the usage statistics as (app key, time); null without access. */
    private fun usageEvents(apps: Map<String, AppEntry>, now: Long): List<Pair<String, Long>>? {
        if (!hasUsageAccess()) return null
        val usm = context.getSystemService(UsageStatsManager::class.java) ?: return null
        val me = Process.myUserHandle()
        // Package → key of the main app (first launchable activity of the current user)
        val byPackage = HashMap<String, String>()
        for (a in apps.values) if (a.user == me) byPackage.putIfAbsent(a.component.packageName, a.key)
        val out = ArrayList<Pair<String, Long>>()
        val ev = runCatching { usm.queryEvents(now - WINDOW_MS, now) }.getOrNull() ?: return null
        val e = UsageEvents.Event()
        var lastPkg: String? = null
        var lastTime = 0L
        while (ev.hasNextEvent()) {
            ev.getNextEvent(e)
            if (e.eventType != UsageEvents.Event.ACTIVITY_RESUMED) continue
            val pkg = e.packageName
            // Activity changes within the same app = one session
            if (pkg == lastPkg && e.timeStamp - lastTime < SESSION_GAP_MS) { lastTime = e.timeStamp; continue }
            lastPkg = pkg; lastTime = e.timeStamp
            if (pkg == context.packageName) continue
            byPackage[pkg]?.let { out += it to e.timeStamp }
        }
        return out
    }

    private fun load(): List<Pair<String, Long>> = runCatching {
        val a = JSONArray(prefs.getString(LOG, "[]"))
        List(a.length()) { a.getJSONArray(it).let { p -> p.getString(0) to p.getLong(1) } }
    }.getOrDefault(emptyList())

    private fun Calendar.isWeekend() = get(Calendar.DAY_OF_WEEK).let { it == Calendar.SATURDAY || it == Calendar.SUNDAY }

    private companion object {
        const val LOG = "log_v1"
        const val LOG_MAX = 600
        const val WINDOW_MS = 21L * 24 * 3600 * 1000
        const val DECAY_MS = 7f * 24 * 3600 * 1000
        const val RECENT_MS = 20f * 60 * 1000
        const val SESSION_GAP_MS = 60_000L
        const val TIME_SPAN_H = 2f
        const val TIME_WEIGHT = 2f
        const val DAYTYPE_WEIGHT = 1.3f
        const val RECENT_WEIGHT = 3f
    }
}
