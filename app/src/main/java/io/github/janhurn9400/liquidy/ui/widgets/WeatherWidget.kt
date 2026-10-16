package io.github.janhurn9400.liquidy.ui.widgets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.janhurn9400.liquidy.R
import io.github.janhurn9400.liquidy.data.WeatherRepository
import io.github.janhurn9400.liquidy.ui.Inter
import kotlinx.coroutines.delay
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JTextStyle
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/**
 * Weather (small 2×2, medium 4×2, large 4×4) after the iPhone screenshots 10-11 16:35 (docs/reference/widgets/01–03):
 * sky gradient, location + arrow, large temperature, condition + H/L; medium with 6 hours, large additionally 5 days with
 * temperature bars. All dimensions in pt of the iPhone widget (small 162.5 pt tall) and scaled with the real height.
 */
@Composable
internal fun WeatherWidget(cols: Int, rows: Int, modifier: Modifier, place: WeatherRepository.Place? = null) {
    val context = LocalContext.current
    val here by WeatherRepository.state.collectAsState()
    val places by WeatherRepository.places.collectAsState()
    // fixed location (widget setting) or the current location
    val state = if (place != null) places[place.key] ?: WeatherRepository.State.Loading else here
    LaunchedEffect(place) {
        while (true) {
            if (place != null) {
                WeatherRepository.refreshPlace(place)
                delay(if (WeatherRepository.places.value[place.key] is WeatherRepository.State.Ready) 10 * 60_000L else 10_000L)
            } else {
                // without a location retry every 5 s (the permission may just have been granted), otherwise check every 10 min
                WeatherRepository.refresh(context)
                delay(if (WeatherRepository.state.value is WeatherRepository.State.Ready) 10 * 60_000L else 5_000L)
            }
        }
    }
    BoxWithConstraints(modifier.background(Brush.verticalGradient(*SKY_STOPS))) {
        val s = maxHeight.value / (if (rows >= 4) REF_LARGE_H else REF_SMALL_H)
        val w = (state as? WeatherRepository.State.Ready)?.w
        if (w == null) {
            Box(Modifier.fillMaxSize().padding((PAD * s).dp), contentAlignment = Alignment.BottomStart) {
                BasicText(stringResource(if (state is WeatherRepository.State.NoLocation) R.string.weather_no_location else R.string.weather_loading),
                    style = text(14f * s, FontWeight.SemiBold))
            }
            return@BoxWithConstraints
        }
        when {
            rows >= 4 -> WeatherLarge(w, s)
            cols >= 4 -> WeatherMedium(w, s)
            else -> WeatherSmall(w, s)
        }
    }
}

@Composable
private fun WeatherSmall(w: WeatherRepository.Weather, s: Float) {
    Column(Modifier.fillMaxSize().padding(horizontal = (PAD * s).dp, vertical = (PAD_V * s).dp)) {
        Place(w, s)
        BigTemp(w, s)
        Spacer(Modifier.weight(1f))
        WeatherIcon(w.code, w.day, null, (TOP_ICON * s).dp)
        Spacer(Modifier.height((2f * s).dp))
        BasicText(conditionText(w.code, w.day), maxLines = 1, overflow = TextOverflow.Ellipsis, style = text(SMALL_TEXT * s, FontWeight.SemiBold))
        BasicText(stringResource(R.string.weather_hl, w.high, w.low), maxLines = 1, style = text(SMALL_TEXT * s, FontWeight.SemiBold))
    }
}

@Composable
private fun TopRow(w: WeatherRepository.Weather, s: Float) {
    Row(Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) { Place(w, s); BigTemp(w, s) }
        Column(horizontalAlignment = Alignment.End) {
            WeatherIcon(w.code, w.day, null, (TOP_ICON * s).dp)
            Spacer(Modifier.height((6f * s).dp))
            BasicText(conditionText(w.code, w.day), maxLines = 1, style = text(SMALL_TEXT * s, FontWeight.SemiBold, TextAlign.End))
            BasicText(stringResource(R.string.weather_hl, w.high, w.low), maxLines = 1, style = text(SMALL_TEXT * s, FontWeight.SemiBold, TextAlign.End))
        }
    }
}

@Composable
private fun WeatherMedium(w: WeatherRepository.Weather, s: Float) {
    // Header at the top, hour row at the bottom. If the height isn't enough (Duo widgets are relatively flatter), the content
    // is shrunk uniformly instead of the temperatures being cut off at the bottom (user 10-15)
    androidx.compose.ui.layout.Layout(
        content = { TopRow(w, s); HourRow(w, s) },
        modifier = Modifier.fillMaxSize().padding(horizontal = (PAD * s).dp, vertical = (PAD_V * s).dp),
    ) { ms, c ->
        val loose = c.copy(minHeight = 0, maxHeight = androidx.compose.ui.unit.Constraints.Infinity)
        val top = ms[0].measure(loose); val hours = ms[1].measure(loose)
        val need = top.height + hours.height
        val k = if (need > c.maxHeight) c.maxHeight.toFloat() / need else 1f
        layout(c.maxWidth, c.maxHeight) {
            fun place(p: androidx.compose.ui.layout.Placeable, y: Int) = p.placeWithLayer(0, y) {
                scaleX = k; scaleY = k; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
            }
            place(top, 0)
            place(hours, c.maxHeight - (hours.height * k).toInt())
        }
    }
}

@Composable
private fun WeatherLarge(w: WeatherRepository.Weather, s: Float) {
    Column(Modifier.fillMaxSize().padding(horizontal = (PAD * s).dp, vertical = (PAD_V * s).dp)) {
        TopRow(w, s)
        Spacer(Modifier.height((LARGE_GAP1 * s).dp))
        Divider(s)
        Spacer(Modifier.height((LARGE_GAP2 * s).dp))
        HourRow(w, s)
        Spacer(Modifier.height((LARGE_GAP2 * s).dp))
        Divider(s)
        Spacer(Modifier.height((LARGE_GAP3 * s).dp))
        DayRows(w, s)
    }
}

@Composable
private fun Divider(s: Float) = Box(Modifier.fillMaxWidth().height((0.6f * s).dp).background(Color.White.copy(alpha = DIVIDER_ALPHA)))

@Composable
private fun Place(w: WeatherRepository.Weather, s: Float) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        BasicText(w.place.ifEmpty { "—" }, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
            style = text(PLACE_TEXT * s, FontWeight.SemiBold))
        if (w.current) {
            Spacer(Modifier.width((4f * s).dp))
            // location arrow (own shape)
            Canvas(Modifier.size((10f * s).dp)) {
                val p = Path().apply {
                    moveTo(size.width, 0f); lineTo(0f, size.height * 0.45f); lineTo(size.width * 0.45f, size.height * 0.55f)
                    lineTo(size.width * 0.55f, size.height); close()
                }
                drawPath(p, Color.White)
            }
        }
    }
}

@Composable
private fun BigTemp(w: WeatherRepository.Weather, s: Float) =
    BasicText("${w.temp}°", maxLines = 1, style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Light, fontSize = (BIG_TEMP * s).sp,
        lineHeight = (BIG_TEMP * 1.08f * s).sp, color = Color.White))

@Composable
private fun HourRow(w: WeatherRepository.Weather, s: Float) {
    val fmt = DateTimeFormatter.ofPattern(if (android.text.format.DateFormat.is24HourFormat(LocalContext.current)) "H" else "h a")
    val timeFmt = DateTimeFormatter.ofPattern(if (android.text.format.DateFormat.is24HourFormat(LocalContext.current)) "H:mm" else "h:mm")
    BoxWithConstraints(Modifier.fillMaxWidth().height((HOUR_ROW_H * s).dp)) {
        // Column centres: 28.8 pt from the widget edge, evenly spaced (margin to the inner area = PAD) [MEASURED 02]
        val first = (HOUR_EDGE - PAD) * s
        val pitch = (maxWidth.value - 2 * first) / 5f
        w.hours.take(6).forEachIndexed { i, h ->
            val cx = first + i * pitch
            Column(Modifier.offset(x = (cx - HOUR_COL_W * s / 2).dp).width((HOUR_COL_W * s).dp), horizontalAlignment = Alignment.CenterHorizontally) {
                BasicText(if (h.event != null) h.time.format(timeFmt) else h.time.format(fmt), maxLines = 1,
                    style = text(HOUR_LABEL * s, FontWeight.SemiBold, TextAlign.Center, Color.White.copy(alpha = HOUR_LABEL_ALPHA)))
                Spacer(Modifier.height((8f * s).dp))
                WeatherIcon(h.code, h.day, h.event, (ROW_ICON * s).dp)
                Spacer(Modifier.height((8f * s).dp))
                BasicText("${h.temp}°", maxLines = 1, style = text(SMALL_TEXT * s, FontWeight.SemiBold, TextAlign.Center))
            }
        }
    }
}

@Composable
private fun DayRows(w: WeatherRepository.Weather, s: Float) {
    val locale = Locale.getDefault()
    val lo = w.days.minOfOrNull { it.min } ?: 0; val hi = (w.days.maxOfOrNull { it.max } ?: 1).coerceAtLeast(lo + 1)
    Column {
        w.days.forEach { d ->
            Box(Modifier.fillMaxWidth().height((DAY_PITCH * s).dp)) {
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    BasicText(d.date.dayOfWeek.getDisplayName(JTextStyle.SHORT, locale).removeSuffix("."), maxLines = 1,
                        modifier = Modifier.width(((DAY_ICON_X - PAD) * s - ROW_ICON * s / 2).dp), style = text(SMALL_TEXT * s, FontWeight.SemiBold))
                    WeatherIcon(d.code, true, null, (ROW_ICON * s).dp)
                    BasicText("${d.min}°", maxLines = 1, modifier = Modifier.width(((DAY_LOW_RIGHT - DAY_ICON_X) * s - ROW_ICON * s / 2).dp),
                        style = text(SMALL_TEXT * s, FontWeight.SemiBold, TextAlign.End, Color.White.copy(alpha = LOW_ALPHA)))
                    // Bar: track over the week, filled from the low to the high, colour by temperature
                    Canvas(Modifier.weight(1f).height((DAY_PITCH * s).dp).padding(horizontal = (8f * s).dp)) {
                        val y = size.height / 2; val th = BAR_H * s * density
                        drawLine(Color.Black.copy(alpha = TRACK_ALPHA), Offset(0f, y), Offset(size.width, y), th, StrokeCap.Round)
                        val x0 = size.width * (d.min - lo) / (hi - lo).toFloat(); val x1 = size.width * (d.max - lo) / (hi - lo).toFloat()
                        drawLine(Brush.horizontalGradient(listOf(tempColor(d.min), tempColor(d.max)), x0, x1), Offset(x0, y), Offset(x1.coerceAtLeast(x0 + 1f), y), th, StrokeCap.Round)
                    }
                    BasicText("${d.max}°", maxLines = 1, modifier = Modifier.width((DAY_HIGH_W * s).dp),
                        style = text(SMALL_TEXT * s, FontWeight.SemiBold, TextAlign.End))
                }
            }
        }
    }
}

private fun text(size: Float, weight: FontWeight, align: TextAlign = TextAlign.Start, color: Color = Color.White) =
    TextStyle(fontFamily = Inter, fontWeight = weight, fontSize = size.sp, lineHeight = (size * 1.2f).sp, color = color, textAlign = align)

/** Bar colour: cold turquoise → mild green → warm yellow → hot orange [MEASURED 03: 2° (100,208,215), 16° (155,211,174), 19° (227,207,93)]. */
private fun tempColor(t: Int): Color {
    val stops = listOf(-10f to Color(0xFF5E9CFF), 5f to Color(0xFF64D0D7), 15f to Color(0xFF9BD3AE), 19f to Color(0xFFE3CF5D), 28f to Color(0xFFFF9A3C), 35f to Color(0xFFFF5A3C))
    val x = t.toFloat()
    if (x <= stops.first().first) return stops.first().second
    for (i in 1 until stops.size) {
        val (t1, c1) = stops[i]; val (t0, c0) = stops[i - 1]
        if (x <= t1) { val f = (x - t0) / (t1 - t0); return Color(c0.red + (c1.red - c0.red) * f, c0.green + (c1.green - c0.green) * f, c0.blue + (c1.blue - c0.blue) * f) }
    }
    return stops.last().second
}

@Composable
private fun conditionText(code: Int, day: Boolean): String = stringResource(when (code) {
    0 -> if (day) R.string.wx_sunny else R.string.wx_clear
    1 -> R.string.wx_mostly_clear
    2 -> R.string.wx_partly_cloudy
    3 -> R.string.wx_cloudy
    45, 48 -> R.string.wx_fog
    in 51..57 -> R.string.wx_drizzle
    in 61..67, in 80..82 -> R.string.wx_rain
    in 71..77, 85, 86 -> R.string.wx_snow
    in 95..99 -> R.string.wx_thunder
    else -> R.string.wx_cloudy
})

/** Own weather glyphs (no Apple glyphs): sun yellow, cloud white, rain light blue. */
@Composable
internal fun WeatherIcon(code: Int, day: Boolean, event: WeatherRepository.Event?, size: Dp) {
    Canvas(Modifier.size(size)) { drawWeather(code, day, event) }
}

private val SUN = Color(0xFFFFD60A)
private val DROP = Color(0xFF64D2FF)

private fun DrawScope.drawWeather(code: Int, day: Boolean, event: WeatherRepository.Event?) {
    val s = size.minDimension
    if (event != null) { // sunrise/sunset: half a sun above the horizon, arrow
        val c = Offset(s / 2, s * 0.72f)
        drawArc(SUN, 180f, 180f, true, Offset(c.x - s * 0.22f, c.y - s * 0.22f), Size(s * 0.44f, s * 0.44f))
        for (k in 0..4) {
            val a = Math.toRadians(180.0 + 22.5 + k * 33.75)
            drawLine(SUN, c + Offset((cos(a) * s * 0.3f).toFloat(), (sin(a) * s * 0.3f).toFloat()), c + Offset((cos(a) * s * 0.4f).toFloat(), (sin(a) * s * 0.4f).toFloat()), s * 0.05f, StrokeCap.Round)
        }
        drawLine(SUN, Offset(s * 0.08f, s * 0.8f), Offset(s * 0.92f, s * 0.8f), s * 0.05f, StrokeCap.Round)
        val down = event == WeatherRepository.Event.SUNSET
        val ay0 = s * 0.06f; val ay1 = s * 0.3f
        drawLine(Color.White, Offset(s / 2, ay0), Offset(s / 2, ay1), s * 0.05f, StrokeCap.Round)
        val tip = if (down) ay1 else ay0; val dir = if (down) -1f else 1f
        drawLine(Color.White, Offset(s / 2, tip), Offset(s * 0.42f, tip + dir * s * 0.08f), s * 0.05f, StrokeCap.Round)
        drawLine(Color.White, Offset(s / 2, tip), Offset(s * 0.58f, tip + dir * s * 0.08f), s * 0.05f, StrokeCap.Round)
        return
    }
    fun sun(c: Offset, r: Float) {
        drawCircle(SUN, r, c)
        for (k in 0 until 8) {
            val a = Math.toRadians(k * 45.0)
            drawLine(SUN, c + Offset((cos(a) * r * 1.45f).toFloat(), (sin(a) * r * 1.45f).toFloat()), c + Offset((cos(a) * r * 1.9f).toFloat(), (sin(a) * r * 1.9f).toFloat()), r * 0.28f, StrokeCap.Round)
        }
    }
    fun moon(c: Offset, r: Float) {
        val p = Path().apply {
            addOval(androidx.compose.ui.geometry.Rect(c.x - r, c.y - r, c.x + r, c.y + r))
            op(this, Path().apply { addOval(androidx.compose.ui.geometry.Rect(c.x - r * 0.35f, c.y - r * 1.25f, c.x + r * 1.65f, c.y + r * 0.75f)) }, androidx.compose.ui.graphics.PathOperation.Difference)
        }
        drawPath(p, Color.White)
    }
    fun cloud(cx: Float, cy: Float, w: Float, color: Color = Color.White) {
        val h = w * 0.5f
        drawRoundRect(color, Offset(cx - w / 2, cy - h * 0.1f), Size(w, h * 0.6f), androidx.compose.ui.geometry.CornerRadius(h * 0.3f))
        drawCircle(color, w * 0.22f, Offset(cx - w * 0.16f, cy))
        drawCircle(color, w * 0.28f, Offset(cx + w * 0.1f, cy - h * 0.12f))
    }
    when (code) {
        0 -> if (day) sun(Offset(s / 2, s / 2), s * 0.2f) else moon(Offset(s / 2, s / 2), s * 0.3f)
        1, 2 -> {
            if (day) sun(Offset(s * 0.66f, s * 0.36f), s * 0.13f) else moon(Offset(s * 0.66f, s * 0.34f), s * 0.18f)
            cloud(s * 0.42f, s * 0.6f, s * 0.62f)
        }
        45, 48 -> {
            cloud(s * 0.5f, s * 0.42f, s * 0.66f)
            for (k in 0..1) drawLine(Color.White.copy(alpha = 0.8f), Offset(s * 0.2f, s * (0.78f + k * 0.12f)), Offset(s * 0.8f, s * (0.78f + k * 0.12f)), s * 0.05f, StrokeCap.Round)
        }
        in 51..67, in 80..82 -> {
            cloud(s * 0.5f, s * 0.38f, s * 0.66f)
            for (k in 0..2) { val x = s * (0.32f + k * 0.18f); drawLine(DROP, Offset(x, s * 0.72f), Offset(x - s * 0.05f, s * 0.86f), s * 0.055f, StrokeCap.Round) }
        }
        in 71..77, 85, 86 -> {
            cloud(s * 0.5f, s * 0.38f, s * 0.66f)
            for (k in 0..2) drawCircle(Color.White, s * 0.04f, Offset(s * (0.32f + k * 0.18f), s * (0.78f + (k % 2) * 0.08f)))
        }
        in 95..99 -> {
            cloud(s * 0.5f, s * 0.36f, s * 0.66f)
            val p = Path().apply { moveTo(s * 0.54f, s * 0.6f); lineTo(s * 0.42f, s * 0.8f); lineTo(s * 0.52f, s * 0.8f); lineTo(s * 0.46f, s * 0.96f); lineTo(s * 0.64f, s * 0.74f); lineTo(s * 0.54f, s * 0.74f); close() }
            drawPath(p, SUN)
        }
        else -> cloud(s * 0.5f, s * 0.55f, s * 0.86f)
    }
}

// Sky gradient by height [MEASURED 01/03: 7 % (41,94,146), 25 % (59,108,159), 50 % (79,128,177), 76 % (102,146,188), 100 % (150,182,218)]
private val SKY_STOPS = arrayOf(
    0f to Color(0xFF285C91), 0.25f to Color(0xFF3B6C9F), 0.5f to Color(0xFF4F80B1), 0.76f to Color(0xFF6692BC), 1f to Color(0xFF96B6DA),
)
// Dimensions in pt of the iPhone widget (920 px = 402 pt) [MEASURED 01–03]
private const val REF_SMALL_H = 163f
private const val REF_LARGE_H = 363f
private const val PAD = 17.5f
private const val PAD_V = 14f
private const val PLACE_TEXT = 16f
private const val BIG_TEMP = 42f
private const val SMALL_TEXT = 15f
private const val TOP_ICON = 20f
private const val ROW_ICON = 22f
private const val HOUR_LABEL = 13f
private const val HOUR_LABEL_ALPHA = 0.8f
private const val HOUR_ROW_H = 72f // hour to temperature ≈ 70 pt [MEASURED 03: 898–1058 px]
private const val HOUR_EDGE = 28.8f
private const val HOUR_COL_W = 48f
private const val DAY_PITCH = 31f
private const val DAY_ICON_X = 88.7f
private const val DAY_LOW_RIGHT = 146f
private const val DAY_HIGH_W = 36f
private const val LOW_ALPHA = 0.55f
private const val TRACK_ALPHA = 0.16f
private const val BAR_H = 3.6f
private const val DIVIDER_ALPHA = 0.3f
private const val LARGE_GAP1 = 14f
private const val LARGE_GAP2 = 10f
private const val LARGE_GAP3 = 6f
