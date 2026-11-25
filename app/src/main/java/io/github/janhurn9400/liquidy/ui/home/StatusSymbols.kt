package io.github.janhurn9400.liquidy.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.drawText
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.sp
import io.github.janhurn9400.liquidy.data.StatusInfo
import io.github.janhurn9400.liquidy.ui.Inter
import kotlin.math.roundToInt

/**
 * iPhone Duo status display [MEASURED recording 19:18 (unfolded) / 19:07 (folded)]: no bar, but above
 * the dock column the time (centre 39 pt below the top), 36 pt below it an open ring (Ø 40 pt, gap at the bottom;
 * here = battery level) with a Wi-Fi glyph, below it 4 dots (here = cellular). If a camera cutout lies above the column
 * (folded), everything below moves down (clock 26 pt below its bottom edge). Everything relative to the display width/cutout.
 */
@Composable
fun DuoStatusSymbols(info: StatusInfo, columnCenterX: Float, density: Float, color: Color,
                     /** Control Center 0…1 (its module reveal) — null on the plain home screen. */
                     cc: (() -> Float)? = null,
                     /** true while the Control Center opens, false while it closes (the two look different, M-CC Duo). */
                     ccOpening: () -> Boolean = { true },
                     carrier: String = "") {
    val view = LocalView.current
    // Camera hole/notch (some foldables) only if it really lies above the clock (clock width ≈ 2 × CLOCK_HALF_W)
    // Position from the real cutout shape (path), not from the bounding rects: on some devices they're coarse
    // (emulator: hole centred above the right half, but the rect reported in the top-right corner)
    val cutout = view.rootWindowInsets?.displayCutout
    val areas = cutout?.cutoutPath?.let { path ->
        val b = android.graphics.RectF(); path.computeBounds(b, true)
        if (b.isEmpty) null else listOf(android.graphics.Rect(b.left.toInt(), b.top.toInt(), kotlin.math.ceil(b.right).toInt(), kotlin.math.ceil(b.bottom).toInt()))
    } ?: cutout?.boundingRects.orEmpty()
    val cutoutBottom = areas
        .filter { r -> r.top < (CLOCK_Y + CLOCK_HALF_H) * density &&
            r.right > columnCenterX - CLOCK_HALF_W * density && r.left < columnCenterX + CLOCK_HALF_W * density }
        .maxOfOrNull { it.bottom } ?: 0
    val clockY = maxOf(CLOCK_Y * density, cutoutBottom + CLOCK_BELOW_CUTOUT * density)
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val small = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = color)
    val label = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = color)
    Layout(content = {
        BasicText(info.time, style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = color))
    }, modifier = Modifier.graphicsLayer { alpha = cc?.let { f -> homeAlpha(f(), ccOpening()) } ?: 1f }) { ms, c ->
        val t = ms[0].measure(Constraints())
        layout(c.maxWidth, c.maxHeight) { t.place((columnCenterX - t.width / 2f).roundToInt(), (clockY - t.height / 2f).roundToInt()) }
    }
    Canvas(Modifier.fillMaxSize()) {
        val cy = clockY + RING_BELOW * density
        val k = cc?.invoke()?.coerceIn(0f, 1f) ?: 0f
        val opening = ccOpening()
        val home = if (cc == null) 1f else homeAlpha(k, opening)
        // Control Center group: opening = slides in from the right after the home symbols faded; closing = flows up
        // into the ring (which stays where it is — it is the same ring on both screens)
        val ccAlpha: Float; val dx: Float; val pull: Float
        if (cc == null) { ccAlpha = 0f; dx = 0f; pull = 1f }
        else if (opening) {
            // home symbols gone first, a short empty gap, then the column slides in from the right (simulator 9.44 s)
            val f = io.github.janhurn9400.liquidy.motion.Motion.ccStatusStart.value
            val s = ((k - f) / (1f - f)).coerceIn(0f, 1f)
            ccAlpha = s; dx = (1f - s) * (1f - s) * io.github.janhurn9400.liquidy.motion.Motion.ccStatusSlide.value * density; pull = 1f
        } else { ccAlpha = k; dx = 0f; pull = k }
        val r = RING_R * density; val sw = RING_STROKE * density
        fun ring(x: Float, a: Float) {
            if (a <= 0.001f) return
            val tl = Offset(x - r, cy - r); val sz = Size(2 * r, 2 * r)
            drawArc(color.copy(alpha = 0.3f * a), RING_START, RING_SWEEP, false, tl, sz, style = Stroke(sw, cap = StrokeCap.Round))
            drawArc((if (info.charging) CHARGE_GREEN else color).copy(alpha = a), RING_START, RING_SWEEP * info.battery, false, tl, sz, style = Stroke(sw, cap = StrokeCap.Round))
        }
        // home: ring + Wi-Fi inside + 4 cellular dots below
        val closingRing = cc != null && !opening
        ring(columnCenterX, if (closingRing) 1f else home)
        val homeGlyph = if (closingRing) 1f - k else home
        val c = Offset(columnCenterX, cy)
        if (homeGlyph > 0.001f) {
            val hc = color.copy(alpha = homeGlyph)
            if (info.airplane) airplane(c, r * 0.5f, hc)
            else wifi(Offset(c.x, c.y + r * 0.22f), r * 0.62f, if (info.wifi < 0) 0 else info.wifi + 1, hc, dimNone = info.wifi < 0)
            val dy = cy + DOTS_BELOW * density; val pitch = DOT_PITCH * density
            for (i in 0 until 4) drawCircle(color.copy(alpha = homeGlyph * if (i < info.cell) 1f else 0.3f), DOT_D / 2 * density,
                Offset(columnCenterX + (i - 1.5f) * pitch, dy))
        }
        if (ccAlpha <= 0.001f) return@Canvas
        val x = columnCenterX + dx
        if (!closingRing) ring(x, ccAlpha)
        fun at(off: Float) = cy + off * density * pull // closing: everything converges into the ring centre
        // battery glyph inside the ring
        val bw = r * 0.95f; val bh = r * 0.5f
        drawRoundRect(color.copy(alpha = ccAlpha), Offset(x - bw / 2, cy - bh / 2), Size(bw * 0.88f, bh), CornerRadius(bh / 2))
        drawRoundRect(color.copy(alpha = ccAlpha * 0.6f), Offset(x + bw * 0.42f, cy - bh * 0.2f), Size(bw * 0.08f, bh * 0.4f), CornerRadius(bw * 0.04f))
        fun text(t: String, style: TextStyle, y: Float, a: Float) {
            if (t.isEmpty() || a <= 0.001f) return
            val l = measurer.measure(t, style)
            drawText(l, color = color.copy(alpha = a), topLeft = Offset(x - l.size.width / 2f, y - l.size.height / 2f))
        }
        text("${(info.battery * 100).roundToInt()}%", small, at(DUO_CC_PERCENT), ccAlpha)
        wifi(Offset(x, at(DUO_CC_WIFI) + r * 0.22f), r * 0.62f, if (info.wifi < 0) 0 else info.wifi + 1, color.copy(alpha = ccAlpha), dimNone = info.wifi < 0)
        val by = at(DUO_CC_BARS); val bwid = 3f * density; val bpitch = 5f * density; val bhgt = 7f * density
        for (i in 0 until 4) drawRoundRect(color.copy(alpha = ccAlpha * if (i < info.cell) 1f else 0.35f),
            Offset(x + (i - 1.5f) * bpitch - bwid / 2, by - bhgt / 2), Size(bwid, bhgt), CornerRadius(bwid / 2))
        text(carrier, label, at(DUO_CC_CARRIER), ccAlpha)
    }
}

/** Home time/ring/dots while the Control Center moves: opening = gone quickly; closing = back as it finishes. */
private fun homeAlpha(k: Float, opening: Boolean): Float {
    val c = k.coerceIn(0f, 1f)
    return if (opening) (1f - c / io.github.janhurn9400.liquidy.motion.Motion.ccHomeStatusFade.value).coerceIn(0f, 1f)
    else ((1f - c - 0.4f) / 0.6f).coerceIn(0f, 1f)
}

private val DUO_CC_PERCENT = io.github.janhurn9400.liquidy.design.CcLayout.DUO_STATUS_PERCENT
private val DUO_CC_WIFI = io.github.janhurn9400.liquidy.design.CcLayout.DUO_STATUS_WIFI
private val DUO_CC_BARS = io.github.janhurn9400.liquidy.design.CcLayout.DUO_STATUS_BARS
private val DUO_CC_CARRIER = io.github.janhurn9400.liquidy.design.CcLayout.DUO_STATUS_CARRIER

/**
 * iOS-style status bar for normal phones: time on the left (centre at 19 % of the width), on the right cellular bars,
 * Wi-Fi fan, battery capsule (centre at 81 %). Vertically centred in the Android status bar height [height].
 */
@Composable
fun IosStatusBar(info: StatusInfo, height: Float, density: Float, color: Color) {
    Box(Modifier.fillMaxSize()) {
        Layout(content = {
            BasicText(info.time, style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = color))
        }) { ms, c ->
            val t = ms[0].measure(Constraints())
            layout(c.maxWidth, c.maxHeight) { t.place((c.maxWidth * SIDE_SHARE - t.width / 2f).roundToInt(), (height / 2 - t.height / 2f).roundToInt()) }
        }
        Canvas(Modifier.fillMaxSize()) {
            val u = density
            val cy = height / 2
            val bw = 25f * u; val bh = 12f * u
            val gap = 6f * u
            val cellW = if (info.cell >= 0 || info.airplane) 17f * u else 0f
            val wifiW = if (info.wifi >= 0) 16f * u else 0f
            val total = cellW + (if (cellW > 0) gap else 0f) + wifiW + (if (wifiW > 0) gap else 0f) + bw + 2.5f * u
            var x = size.width * (1f - SIDE_SHARE) - total / 2
            if (info.airplane) { airplane(Offset(x + cellW / 2, cy), 7f * u, color); x += cellW + gap }
            else if (cellW > 0) {
                for (k in 0 until 4) {
                    val h = (4.5f + 2.4f * k) * u
                    drawRoundRect(color.copy(alpha = if (k < info.cell) 1f else 0.3f), Offset(x + k * 4.6f * u, cy + 5.5f * u - h),
                        Size(3.1f * u, h), CornerRadius(1f * u))
                }
                x += cellW + gap
            }
            if (wifiW > 0) { wifi(Offset(x + wifiW / 2, cy + 4.5f * u), 8.5f * u, info.wifi + 1, color, dimNone = false); x += wifiW + gap }
            // Battery: outline semi-transparent, fill by level (charging green, low red), cap on the right
            drawRoundRect(color.copy(alpha = 0.4f), Offset(x, cy - bh / 2), Size(bw, bh), CornerRadius(4f * u), style = Stroke(1f * u))
            val fill = if (info.charging) CHARGE_GREEN else if (info.battery < 0.2f) LOW_RED else color
            drawRoundRect(fill, Offset(x + 2f * u, cy - bh / 2 + 2f * u), Size((bw - 4f * u) * info.battery.coerceAtLeast(0.05f), bh - 4f * u), CornerRadius(2.5f * u))
            drawRoundRect(color.copy(alpha = 0.4f), Offset(x + bw + 1f * u, cy - 2f * u), Size(1.5f * u, 4f * u), CornerRadius(1f * u))
        }
    }
}

/** Wi-Fi fan: dot + 2 arcs, [bars] 0…4 (0 = all pale). Centre [c] = base point of the fan. */
private fun DrawScope.wifi(c: Offset, r: Float, bars: Int, color: Color, dimNone: Boolean) {
    val sw = r * 0.2f
    drawCircle(color.copy(alpha = if (bars >= 1 && !dimNone) 1f else 0.3f), sw * 0.75f, Offset(c.x, c.y - sw * 0.6f))
    for (k in 1..2) {
        val rr = r * (0.42f + 0.38f * (k - 1)) + r * 0.12f
        drawArc(color.copy(alpha = if (bars >= k + 1 && !dimNone) 1f else 0.3f), 225f, 90f, false,
            Offset(c.x - rr, c.y - rr), Size(2 * rr, 2 * rr), style = Stroke(sw, cap = StrokeCap.Round))
    }
}

private fun DrawScope.airplane(c: Offset, r: Float, color: Color) {
    val sw = r * 0.22f
    drawLine(color, Offset(c.x, c.y - r), Offset(c.x, c.y + r), sw, StrokeCap.Round)
    drawLine(color, Offset(c.x - r, c.y + r * 0.1f), Offset(c.x + r, c.y + r * 0.1f), sw, StrokeCap.Round)
    drawLine(color, Offset(c.x - r * 0.45f, c.y + r * 0.85f), Offset(c.x + r * 0.45f, c.y + r * 0.85f), sw, StrokeCap.Round)
}

private const val CLOCK_Y = 39f
private const val CLOCK_BELOW_CUTOUT = 15f // clock centre below the camera hole (same gap as at the top to the edge)
private const val CLOCK_HALF_W = 22f
private const val CLOCK_HALF_H = 9f
private const val RING_BELOW = 36f
private const val RING_R = 19f
private const val RING_STROKE = 3f
private const val RING_START = 140f   // gap at the bottom (90° = bottom), ≈ 100° open
private const val RING_SWEEP = 260f
private const val DOTS_BELOW = 19f
private const val DOT_PITCH = 7f
private const val DOT_D = 3.5f
private const val SIDE_SHARE = 0.19f
private val CHARGE_GREEN = Color(0xFF34C759)
private val LOW_RED = Color(0xFFFF3B30)
