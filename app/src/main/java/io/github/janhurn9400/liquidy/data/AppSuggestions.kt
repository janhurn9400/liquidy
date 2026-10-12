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
 * App-Vorschläge auf dem Gerät: bewertet vergangene App-Starts nach Aktualität, Häufigkeit und Tageszeit
 * (wer morgens um 8 immer dieselbe App öffnet, bekommt sie morgens vorgeschlagen).
 *
 * Quelle: Android-Nutzungsstatistik, wenn „Zugriff auf Nutzungsdaten“ erlaubt ist (erfasst auch Starts außerhalb
 * des Launchers), sonst das eigene Startprotokoll. Nichts verlässt das Gerät.
 */
class AppSuggestions(private val context: Context) {
    private val prefs = context.getSharedPreferences("suggestions", Context.MODE_PRIVATE)

    /** Eigenes Protokoll: Paar (App-Key, Zeit), älteste zuerst. */
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

    /** App-Keys nach Wahrscheinlichkeit, jetzt geöffnet zu werden (bestes zuerst). Blockiert kurz — nicht im UI-Thread. */
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
            // Grundgewicht: Häufigkeit, über ~1 Woche abklingend
            var w = exp(-age / DECAY_MS)
            // Gleiche Tageszeit (±2 h) und gleicher Tagestyp (Werktag/Wochenende) zählen mehr
            w *= 1f + TIME_WEIGHT * (1f - dh / TIME_SPAN_H).coerceAtLeast(0f)
            if (cal.isWeekend() == nowWeekend) w *= DAYTYPE_WEIGHT
            // Gerade benutzt: kurzer Bonus, damit die letzte App gleich wieder da ist
            w += RECENT_WEIGHT * exp(-age / RECENT_MS)
            score[key] = (score[key] ?: 0f) + w
        }
        return score.entries.sortedByDescending { it.value }.map { it.key }
    }

    /** Vordergrund-Sitzungen aus der Nutzungsstatistik als (App-Key, Zeit); null ohne Zugriff. */
    private fun usageEvents(apps: Map<String, AppEntry>, now: Long): List<Pair<String, Long>>? {
        if (!hasUsageAccess()) return null
        val usm = context.getSystemService(UsageStatsManager::class.java) ?: return null
        val me = Process.myUserHandle()
        // Paket → Key der Haupt-App (erste startbare Activity des aktuellen Nutzers)
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
            // Activity-Wechsel innerhalb derselben App = eine Sitzung
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
