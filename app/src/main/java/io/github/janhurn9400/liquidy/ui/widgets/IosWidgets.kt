package io.github.janhurn9400.liquidy.ui.widgets

import androidx.compose.ui.text.drawText
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
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
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

/** Own iOS-like widgets (no Apple asset; structure after recording6/7). */
object IosWidgetTypes {
    const val CLOCK = "ios:clock"
    const val CALENDAR = "ios:calendar"
    const val BATTERY = "ios:battery"
    const val WEATHER = "ios:weather"
    const val PHOTOS = "ios:photos"
    const val REMINDERS = "ios:reminders"
    val all = listOf(CLOCK, CALENDAR, BATTERY, WEATHER, PHOTOS, REMINDERS)
}

/** Tapping an own widget opens the matching app (iOS: Calendar → Calendar, Clock → Clock, Batteries → nothing). */
fun openWidgetApp(context: Context, type: String, from: android.graphics.RectF? = null, widgetKey: String? = null) {
    if (type == IosWidgetTypes.REMINDERS) { RemindersStore.sheetOpen = true; return } // own list instead of a third-party app
    val intent = when (type) {
        IosWidgetTypes.CALENDAR -> Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR)
        IosWidgetTypes.CLOCK -> Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS)
        IosWidgetTypes.PHOTOS -> Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_GALLERY)
        else -> return
    }
    // with the Animation Module: the app grows out of the widget (same card as for the icon, starting with widget corners)
    val opts = from?.let { io.github.janhurn9400.liquidy.data.AppLaunchTransition.optionsFor(it,
        io.github.janhurn9400.liquidy.design.DesignTokens.widgetCorner.value * io.github.janhurn9400.liquidy.design.UiScale.density(context), widgetKey) }
    widgetKey?.let { k ->
        context.packageManager.resolveActivity(intent, 0)?.activityInfo?.packageName?.let { io.github.janhurn9400.liquidy.data.AppLaunchTransition.widgetOrigins[it] = k }
    }
    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), opts?.toBundle()) }
}

/**
 * True while a widget is drawn in the Tinted icon style: widgets then paint their own background black, and [IosWidget]
 * maps the result by brightness from the tint plate (black) to the glyph colour (light content) — like iOS 27, where
 * widgets get the same opaque plate as the icons with light content (T-TINT, measured 10-16).
 */
internal val LocalWidgetTinted = androidx.compose.runtime.staticCompositionLocalOf { false }

/** Background of an own widget: [normal], or black in the Tinted style (becomes the tint plate). */
@Composable
internal fun widgetBackground(normal: Color): Color = if (LocalWidgetTinted.current) Color.Black else normal

@Composable
fun IosWidget(type: String, cols: Int, rows: Int = 2, modifier: Modifier = Modifier, config: String? = null) {
    val ia = io.github.janhurn9400.liquidy.design.IconAppearance
    // photos keep their real colours (a tint would falsify them); everything else gets the plate like the icons
    if (ia.effective != io.github.janhurn9400.liquidy.design.IconStyle.Tinted || ia.dark || type == IosWidgetTypes.PHOTOS) { IosWidgetContent(type, cols, rows, modifier, config); return }
    val plate = ia.tintPlate(); val glyph = ia.tintGlyph()
    val effect = androidx.compose.runtime.remember(plate, glyph) { tintEffect(plate, glyph) }
    androidx.compose.runtime.CompositionLocalProvider(LocalWidgetTinted provides true) {
        IosWidgetContent(type, cols, rows, modifier.graphicsLayer { renderEffect = effect }, config)
    }
}

/** Brightness → plate…glyph (black = plate, light content = glyph colour), see [IconAppearance.duotone]. */
private fun tintEffect(plate: Color, glyph: Color): androidx.compose.ui.graphics.RenderEffect {
    val m = android.graphics.ColorMatrix(io.github.janhurn9400.liquidy.design.IconAppearance.duotone(plate, glyph,
        io.github.janhurn9400.liquidy.design.DesignTokens.TINT_WIDGET_GAIN))
    return android.graphics.RenderEffect.createColorFilterEffect(android.graphics.ColorMatrixColorFilter(m)).asComposeRenderEffect()
}

@Composable
private fun IosWidgetContent(type: String, cols: Int, rows: Int, modifier: Modifier, config: String?) {
    when (type) {
        IosWidgetTypes.WEATHER -> WeatherWidget(cols, rows, modifier, io.github.janhurn9400.liquidy.data.WeatherRepository.Place.decode(config))
        IosWidgetTypes.PHOTOS -> PhotoWidget(modifier, config)
        IosWidgetTypes.REMINDERS -> RemindersWidget(cols, rows, modifier)
        IosWidgetTypes.CLOCK -> ClockWidget(modifier, io.github.janhurn9400.liquidy.data.WorldClock.City.decode(config))
        IosWidgetTypes.CALENDAR -> CalendarWidget(cols, modifier, config)
        IosWidgetTypes.BATTERY -> BatteryWidget(modifier)
        else -> Box(modifier.background(Color(0xFF1C1C1E)))
    }
}

// ── Clock (analogue, white dial, red second hand — "Clock · City" in the picker, recording7 8.4 s) ────────────
@Composable
private fun ClockWidget(modifier: Modifier, city: io.github.janhurn9400.liquidy.data.WorldClock.City? = null) {
    // City chosen → its time zone; at night (18–6 h there) a dark dial like on iOS, city abbreviation on the dial
    val zone = city?.zoneId ?: java.time.ZoneId.systemDefault()
    val now by produceState(LocalTime.now(zone), zone) { while (true) { value = LocalTime.now(zone); delay(1000L - System.currentTimeMillis() % 1000) } }
    val night = city != null && (now.hour < CLOCK_DAY_FROM || now.hour >= CLOCK_NIGHT_FROM)
    val face = if (night) Color.Black else Color.White
    val ink = if (night || LocalWidgetTinted.current) Color.White else Color.Black
    val minorTick = if (night) Color(0xFF6E6E73) else Color(0xFF9A9A9A)
    val label = city?.name?.filter { it.isLetter() }?.take(3)?.uppercase()
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val tinted = LocalWidgetTinted.current
    Box(modifier.background(widgetBackground(Color(0xFF1C1C1E))), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(CLOCK_PAD.dp)) {
            val r = size.minDimension / 2
            val c = center
            if (!tinted) drawCircle(face, r, c) // tinted: the dial is part of the plate, ticks/hands light (iOS 27)
            if (label != null) {
                val t = measurer.measure(label, androidx.compose.ui.text.TextStyle(fontFamily = io.github.janhurn9400.liquidy.ui.Inter,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, fontSize = (r * 0.2f / density).sp, color = minorTick))
                drawText(t, topLeft = Offset(c.x - t.size.width / 2f, c.y - r * 0.5f - t.size.height / 2f))
            }
            for (i in 0 until 60) {
                val a = Math.toRadians(i * 6.0)
                val long = i % 5 == 0
                val o = r * 0.94f; val inner = if (long) r * 0.84f else r * 0.89f
                drawLine(if (long) ink else minorTick,
                    Offset(c.x + (sin(a) * inner).toFloat(), c.y - (cos(a) * inner).toFloat()),
                    Offset(c.x + (sin(a) * o).toFloat(), c.y - (cos(a) * o).toFloat()),
                    if (long) r * 0.025f else r * 0.012f)
            }
            fun hand(deg: Float, len: Float, w: Float, color: Color, tail: Float = 0.08f) = rotate(deg, c) {
                drawLine(color, Offset(c.x, c.y + r * tail), Offset(c.x, c.y - r * len), w, StrokeCap.Round)
            }
            hand(((now.hour % 12) + now.minute / 60f) * 30f, 0.5f, r * 0.07f, ink)
            hand((now.minute + now.second / 60f) * 6f, 0.78f, r * 0.05f, ink)
            hand(now.second * 6f, 0.86f, r * 0.018f, Color(0xFFFF9500), tail = 0.18f)
            drawCircle(Color(0xFFFF9500), r * 0.045f, c)
            drawCircle(face, r * 0.018f, c)
        }
    }
}

// ── Calendar (black, red weekday, large number, next events with colour bars — recording6 0.2 s) ─────────
private data class CalEvent(val title: String, val time: String, val color: Int)

@Composable
private fun CalendarWidget(cols: Int, modifier: Modifier, config: String? = null) {
    val context = LocalContext.current
    val today = LocalDate.now()
    val events by produceState(emptyList<CalEvent>(), today, config) { value = loadEvents(context, config) }
    val locale = Locale.getDefault()
    Row(modifier.background(widgetBackground(Color.Black)).padding(WIDGET_PAD.dp)) {
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

/** Events today + tomorrow (only with calendar permission; without it the list stays empty). */
/** The device's calendars (id, name, colour) for the widget setting "Calendars". */
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

/** [config] = selected calendar ids, comma-separated; null = all. */
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

// ── Batteries (ring + percent, iOS "Batteries") ────────────────────────────────────────────────
@Composable
private fun BatteryWidget(modifier: Modifier) {
    val context = LocalContext.current
    val level by produceState(batteryLevel(context)) { while (true) { value = batteryLevel(context); delay(60_000) } }
    Box(modifier, contentAlignment = Alignment.Center) {
        // Tinted glass like iOS "Batteries" (recording8 12.5 s: inside ≈ 35 % of the background brightness, colour stays)
        if (LocalWidgetTinted.current) Box(Modifier.fillMaxSize().background(Color.Black))
        else if (io.github.janhurn9400.liquidy.glass.LocalGlassBackdrop.current != null) io.github.janhurn9400.liquidy.glass.LiquidGlass(
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
private const val WIDGET_PAD = 16f   // [ESTIMATED] padding of iOS widgets
private const val MEDIUM_DATE_W = 110f
private const val CLOCK_PAD = 10f
private const val CLOCK_DAY_FROM = 6     // iOS: light dial from 6 h …
private const val CLOCK_NIGHT_FROM = 18  // … dark from 18 h (local time of the city) [ESTIMATED]
private const val BATTERY_PAD = 30f
private const val WIDGET_GLASS_BLUR = 24f
private const val WIDGET_GLASS_DARK = 0.22f // Duo [reference 10_today/01_home]: translucent glass, not opaque
