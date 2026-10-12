package io.github.janhurn9400.liquidy.ui.widgets

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.provider.CalendarContract
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import io.github.janhurn9400.liquidy.ui.Inter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle as JTextStyle
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/** Eigene iOS-artige Widgets (kein Apple-Asset; Aufbau nach recording6/7). */
object IosWidgetTypes {
    const val CLOCK = "ios:clock"
    const val CALENDAR = "ios:calendar"
    const val BATTERY = "ios:battery"
    const val WEATHER = "ios:weather"
    const val PHOTOS = "ios:photos"
    const val REMINDERS = "ios:reminders"
    val all = listOf(CLOCK, CALENDAR, BATTERY, WEATHER, PHOTOS, REMINDERS)
}

/** Antippen eines eigenen Widgets öffnet die passende App (iOS: Kalender → Kalender, Uhr → Uhr, Batterien → nichts). */
fun openWidgetApp(context: Context, type: String, from: android.graphics.RectF? = null, widgetKey: String? = null) {
    if (type == IosWidgetTypes.REMINDERS) { RemindersStore.sheetOpen = true; return } // eigene Liste statt fremder App
    val intent = when (type) {
        IosWidgetTypes.CALENDAR -> Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR)
        IosWidgetTypes.CLOCK -> Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS)
        IosWidgetTypes.PHOTOS -> Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_GALLERY)
        else -> return
    }
    // mit Magisk-Modul: App wächst aus dem Widget (gleiche Karte wie beim Icon, Start mit Widget-Ecken)
    val opts = from?.let { io.github.janhurn9400.liquidy.data.AppLaunchTransition.optionsFor(it,
        io.github.janhurn9400.liquidy.design.DesignTokens.widgetCorner.value * io.github.janhurn9400.liquidy.design.UiScale.density(context), widgetKey) }
    widgetKey?.let { k ->
        context.packageManager.resolveActivity(intent, 0)?.activityInfo?.packageName?.let { io.github.janhurn9400.liquidy.data.AppLaunchTransition.widgetOrigins[it] = k }
    }
    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), opts?.toBundle()) }
}

@Composable
fun IosWidget(type: String, cols: Int, rows: Int = 2, modifier: Modifier = Modifier, config: String? = null) {
    when (type) {
        IosWidgetTypes.WEATHER -> WeatherWidget(cols, rows, modifier, io.github.janhurn9400.liquidy.data.WeatherRepository.Place.decode(config))
        IosWidgetTypes.PHOTOS -> PhotoWidget(modifier, config)
        IosWidgetTypes.REMINDERS -> RemindersWidget(cols, rows, modifier)
        IosWidgetTypes.CLOCK -> ClockWidget(modifier)
        IosWidgetTypes.CALENDAR -> CalendarWidget(cols, modifier, config)
        IosWidgetTypes.BATTERY -> BatteryWidget(modifier)
        else -> Box(modifier.background(Color(0xFF1C1C1E)))
    }
}

// ── Uhr (Analog, weißes Ziffernblatt, rote Sekunde — „Clock · City“ im Picker, recording7 8,4 s) ────────────
@Composable
private fun ClockWidget(modifier: Modifier) {
    val now by produceState(LocalTime.now()) { while (true) { value = LocalTime.now(); delay(1000L - System.currentTimeMillis() % 1000) } }
    Box(modifier.background(Color(0xFF1C1C1E)), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(CLOCK_PAD.dp)) {
            val r = size.minDimension / 2
            val c = center
            drawCircle(Color.White, r, c)
            for (i in 0 until 60) {
                val a = Math.toRadians(i * 6.0)
                val long = i % 5 == 0
                val o = r * 0.94f; val inner = if (long) r * 0.84f else r * 0.89f
                drawLine(if (long) Color.Black else Color(0xFF9A9A9A),
                    Offset(c.x + (sin(a) * inner).toFloat(), c.y - (cos(a) * inner).toFloat()),
                    Offset(c.x + (sin(a) * o).toFloat(), c.y - (cos(a) * o).toFloat()),
                    if (long) r * 0.025f else r * 0.012f)
            }
            fun hand(deg: Float, len: Float, w: Float, color: Color, tail: Float = 0.08f) = rotate(deg, c) {
                drawLine(color, Offset(c.x, c.y + r * tail), Offset(c.x, c.y - r * len), w, StrokeCap.Round)
            }
            hand(((now.hour % 12) + now.minute / 60f) * 30f, 0.5f, r * 0.07f, Color.Black)
            hand((now.minute + now.second / 60f) * 6f, 0.78f, r * 0.05f, Color.Black)
            hand(now.second * 6f, 0.86f, r * 0.018f, Color(0xFFFF9500), tail = 0.18f)
            drawCircle(Color(0xFFFF9500), r * 0.045f, c)
            drawCircle(Color.White, r * 0.018f, c)
        }
    }
}

// ── Kalender (schwarz, roter Wochentag, große Zahl, nächste Termine mit Farbbalken — recording6 0,2 s) ─────────
private data class CalEvent(val title: String, val time: String, val color: Int)

@Composable
private fun CalendarWidget(cols: Int, modifier: Modifier, config: String? = null) {
    val context = LocalContext.current
    val today = LocalDate.now()
    val events by produceState(emptyList<CalEvent>(), today, config) { value = loadEvents(context, config) }
    val locale = Locale.getDefault()
    Row(modifier.background(Color.Black).padding(WIDGET_PAD.dp)) {
        Column(Modifier.then(if (cols >= 4) Modifier.width(MEDIUM_DATE_W.dp) else Modifier.fillMaxWidth())) {
            BasicText(today.dayOfWeek.getDisplayName(JTextStyle.FULL, locale).uppercase(locale),
                style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = RED))
            BasicText(today.dayOfMonth.toString(),
                style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 32.sp, lineHeight = 34.sp, color = Color.White))
            if (cols < 4) {
                Spacer(Modifier.height(6.dp))
                EventList(events, max = 2)
            }
        }
        if (cols >= 4) EventList(events, max = 3, Modifier.fillMaxHeight())
    }
}

@Composable
private fun EventList(events: List<CalEvent>, max: Int, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (events.isEmpty()) {
            BasicText(androidx.compose.ui.res.stringResource(io.github.janhurn9400.liquidy.R.string.no_events),
                style = TextStyle(fontFamily = Inter, fontSize = 12.sp, color = Color(0x99FFFFFF)))
        }
        events.take(max).forEach { e ->
            Row(Modifier.fillMaxWidth().background(Color(e.color).copy(alpha = 0.22f), androidx.compose.foundation.shape.RoundedCornerShape(4.dp))) {
                Box(Modifier.width(3.dp).height(30.dp).background(Color(e.color)))
                Column(Modifier.padding(horizontal = 5.dp, vertical = 2.dp)) {
                    BasicText(e.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = Color(e.color).lighten()))
                    BasicText(e.time, maxLines = 1, style = TextStyle(fontFamily = Inter, fontSize = 10.sp, color = Color(e.color).lighten().copy(alpha = 0.8f)))
                }
            }
        }
    }
}

private fun Color.lighten() = Color(red + (1 - red) * 0.35f, green + (1 - green) * 0.35f, blue + (1 - blue) * 0.35f, alpha)

/** Termine heute + morgen (nur mit Kalender-Berechtigung; ohne sie bleibt die Liste leer). */
/** Kalender des Geräts (Id, Name, Farbe) für die Widget-Einstellung „Kalender“. */
internal fun deviceCalendars(context: Context): List<Triple<Long, String, Int>> {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) return emptyList()
    val out = ArrayList<Triple<Long, String, Int>>()
    runCatching {
        context.contentResolver.query(CalendarContract.Calendars.CONTENT_URI, arrayOf(CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, CalendarContract.Calendars.CALENDAR_COLOR), null, null, null)?.use { c ->
            while (c.moveToNext()) out += Triple(c.getLong(0), c.getString(1) ?: "", c.getInt(2) or (0xFF shl 24))
        }
    }
    return out
}

/** [config] = ausgewählte Kalender-Ids, kommagetrennt; null = alle. */
private suspend fun loadEvents(context: Context, config: String? = null): List<CalEvent> = withContext(Dispatchers.IO) {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) return@withContext emptyList()
    val now = System.currentTimeMillis()
    val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
        .let { ContentUrisCompat.appendRange(it, now, now + 2 * 24 * 3600_000L) }.build()
    val proj = arrayOf(CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.END,
        CalendarContract.Instances.DISPLAY_COLOR, CalendarContract.Instances.ALL_DAY, CalendarContract.Instances.CALENDAR_ID)
    val only = config?.split(',')?.mapNotNull { it.toLongOrNull() }?.toSet()
    val fmt = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT)
    val out = mutableListOf<CalEvent>()
    runCatching {
        context.contentResolver.query(uri, proj, null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
            while (c.moveToNext() && out.size < 4) {
                val end = c.getLong(2)
                if (end < now) continue
                if (only != null && c.getLong(5) !in only) continue
                val time = if (c.getInt(4) == 1) "" else fmt.format(java.util.Date(c.getLong(1))) + " – " + fmt.format(java.util.Date(end))
                out += CalEvent(c.getString(0) ?: "", time, c.getInt(3) or (0xFF shl 24))
            }
        }
    }
    out
}

private object ContentUrisCompat {
    fun appendRange(b: android.net.Uri.Builder, start: Long, end: Long): android.net.Uri.Builder {
        android.content.ContentUris.appendId(b, start); android.content.ContentUris.appendId(b, end); return b
    }
}

// ── Batterien (Ring + Prozent, iOS „Batteries“) ────────────────────────────────────────────────
@Composable
private fun BatteryWidget(modifier: Modifier) {
    val context = LocalContext.current
    val level by produceState(batteryLevel(context)) { while (true) { value = batteryLevel(context); delay(60_000) } }
    Box(modifier, contentAlignment = Alignment.Center) {
        // Getöntes Glas wie iOS „Batteries“ (recording8 12,5 s: innen ≈ 35 % der Hintergrundhelligkeit, Farbe bleibt)
        if (io.github.janhurn9400.liquidy.glass.LocalGlassBackdrop.current != null) io.github.janhurn9400.liquidy.glass.LiquidGlass(
            shape = io.github.janhurn9400.liquidy.glass.glassRect(io.github.janhurn9400.liquidy.design.DesignTokens.widgetCorner.dp),
            modifier = Modifier.fillMaxSize(), blur = WIDGET_GLASS_BLUR.dp, blackTint = WIDGET_GLASS_DARK, whiteTint = 0f,
        ) else Box(Modifier.fillMaxSize().background(Color(0xFF1C1C1E)))
        Canvas(Modifier.fillMaxSize().padding(BATTERY_PAD.dp)) {
            val sw = size.minDimension * 0.09f
            val r = size.minDimension / 2 - sw / 2
            drawCircle(Color(0xFF3A3A3C), r, style = Stroke(sw))
            val color = if (level <= 20) Color(0xFFFF3B30) else Color(0xFF34C759)
            drawArc(color, -90f, 360f * level / 100f, false, Offset(center.x - r, center.y - r),
                androidx.compose.ui.geometry.Size(2 * r, 2 * r), style = Stroke(sw, cap = StrokeCap.Round))
        }
        BasicText("$level %", style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, color = Color.White))
    }
}

private fun batteryLevel(context: Context): Int {
    val i = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return 0
    val l = i.getIntExtra(BatteryManager.EXTRA_LEVEL, 0); val s = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
    return (l * 100 / s.coerceAtLeast(1))
}

private val RED = Color(0xFFFF3B30)
private const val WIDGET_PAD = 16f   // [GESCHÄTZT] Innenabstand iOS-Widgets
private const val MEDIUM_DATE_W = 110f
private const val CLOCK_PAD = 10f
private const val BATTERY_PAD = 30f
private const val WIDGET_GLASS_BLUR = 24f
private const val WIDGET_GLASS_DARK = 0.22f // Duo [Referenz 10_today/01_home]: durchscheinendes Glas, nicht deckend
