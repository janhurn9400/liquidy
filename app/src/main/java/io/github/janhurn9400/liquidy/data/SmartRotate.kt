package io.github.janhurn9400.liquidy.data

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import io.github.janhurn9400.liquidy.ui.widgets.IosWidgetTypes
import java.util.Calendar

/**
 * Smart Rotate for widget stacks (iOS "Smart Rotate"): scores which widget in the stack is most useful right now
 * — an event soon → calendar, battery low or charging → batteries, morning/rain → weather, otherwise the app you often
 * open at this time (from [ranked], the app suggestions). Returns null if nothing is clearly better than what's shown.
 */
object SmartRotate {
    /** Index of the recommended widget or null (keep the current one). Blocks briefly (calendar) — not on the UI thread. */
    fun pick(context: Context, stack: List<WidgetSpec>, current: Int, ranked: List<String>): Int? {
        if (stack.size < 2) return null
        val scores = stack.map { score(context, it, ranked) }
        val best = scores.indices.maxBy { scores[it] }
        // only switch if the best one is clearly ahead (otherwise the stack jumps back and forth restlessly)
        return best.takeIf { it != current && scores[it] >= MIN_SCORE && scores[it] > scores.getOrElse(current) { 0f } + MARGIN }
    }

    private fun score(context: Context, w: WidgetSpec, ranked: List<String>): Float {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when (w.type) {
            IosWidgetTypes.CALENDAR -> minutesToNextEvent(context)?.let { m -> if (m <= 60) 10f - m / 10f else 2f } ?: 0.5f
            IosWidgetTypes.BATTERY -> battery(context).let { (level, charging) -> if (charging || level <= 20) 8f else 0.5f }
            IosWidgetTypes.WEATHER -> {
                val code = (WeatherRepository.state.value as? WeatherRepository.State.Ready)?.w?.hours?.firstOrNull()?.code ?: 0
                val rainSoon = code in 51..67 || code in 80..82 || code in 95..99
                (if (rainSoon) 6f else 0f) + (if (hour in 6..9) 4f else 1f)
            }
            IosWidgetTypes.PHOTOS -> if (hour in 19..22) 3f else 1f
            IosWidgetTypes.CLOCK -> 1f
            else -> {
                // Android widget: how in-demand its app is right now (rank in the app suggestions)
                val pkg = w.provider?.substringBefore('/') ?: return 0f
                val i = ranked.indexOfFirst { it.startsWith("$pkg/") }
                if (i < 0) 0f else 7f - i.coerceAtMost(6)
            }
        }
    }

    private fun battery(context: Context): Pair<Int, Boolean> {
        val i = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return 100 to false
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, 100) * 100 / i.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        return level to (status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL)
    }

    /** Minutes until the next (non-all-day) event in the next 3 h, null without an event/permission. */
    private fun minutesToNextEvent(context: Context): Long? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) return null
        val now = System.currentTimeMillis()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            android.content.ContentUris.appendId(it, now); android.content.ContentUris.appendId(it, now + 3 * 3600_000L)
        }.build()
        return runCatching {
            context.contentResolver.query(uri, arrayOf(CalendarContract.Instances.BEGIN, CalendarContract.Instances.END, CalendarContract.Instances.ALL_DAY),
                null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
                while (c.moveToNext()) {
                    if (c.getInt(2) == 1 || c.getLong(1) < now) continue
                    return@use ((c.getLong(0) - now) / 60_000L).coerceAtLeast(0)
                }
                null
            }
        }.getOrNull()
    }

    private const val MIN_SCORE = 3f
    private const val MARGIN = 1.5f
}
