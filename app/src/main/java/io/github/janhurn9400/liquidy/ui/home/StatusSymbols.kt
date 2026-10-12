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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.sp
import io.github.janhurn9400.liquidy.data.StatusInfo
import io.github.janhurn9400.liquidy.ui.Inter
import kotlin.math.roundToInt

/**
 * iPhone-Duo-Statusanzeige [GEMESSEN Aufnahme 19:18 (aufgeklappt) / 19:07 (zugeklappt)]: keine Leiste, sondern über
 * der Dock-Spalte die Uhrzeit (Mitte 39 pt unter der Oberkante), 36 pt darunter ein offener Ring (Ø 40 pt, Lücke unten;
 * hier = Akkustand) mit WLAN-Symbol, darunter 4 Punkte (hier = Mobilfunk). Liegt eine Kamera-Aussparung über der Spalte
 * (zugeklappt), rückt alles darunter (Uhr 26 pt unter ihre Unterkante). Alles relativ zur Displaybreite/Aussparung.
 */
@Composable
fun DuoStatusSymbols(info: StatusInfo, columnCenterX: Float, density: Float, color: Color) {
    val view = LocalView.current
    // Kamera-Loch/Notch (manche Foldables) nur, wenn es wirklich über der Uhr liegt (Uhrbreite ≈ 2 × CLOCK_HALF_W)
    // Lage aus der echten Ausschnitt-Form (Pfad), nicht aus den Begrenzungsrechtecken: die sind auf manchen Geräten grob
    // (Emulator: Loch mittig über der rechten Hälfte, Rechteck aber oben rechts in der Ecke gemeldet)
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
    Layout(content = {
        BasicText(info.time, style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = color))
    }) { ms, c ->
        val t = ms[0].measure(Constraints())
        layout(c.maxWidth, c.maxHeight) { t.place((columnCenterX - t.width / 2f).roundToInt(), (clockY - t.height / 2f).roundToInt()) }
    }
    Canvas(Modifier.fillMaxSize()) {
        val cy = clockY + RING_BELOW * density
        val c = Offset(columnCenterX, cy)
        val r = RING_R * density; val sw = RING_STROKE * density
        val tl = Offset(c.x - r, c.y - r); val sz = Size(2 * r, 2 * r)
        drawArc(color.copy(alpha = 0.3f), RING_START, RING_SWEEP, false, tl, sz, style = Stroke(sw, cap = StrokeCap.Round))
        drawArc(if (info.charging) CHARGE_GREEN else color, RING_START, RING_SWEEP * info.battery, false, tl, sz, style = Stroke(sw, cap = StrokeCap.Round))
        if (info.airplane) airplane(c, r * 0.5f, color)
        else wifi(Offset(c.x, c.y + r * 0.22f), r * 0.62f, if (info.wifi < 0) 0 else info.wifi + 1, color, dimNone = info.wifi < 0)
        val dy = cy + DOTS_BELOW * density; val pitch = DOT_PITCH * density
        for (k in 0 until 4) drawCircle(color.copy(alpha = if (k < info.cell) 1f else 0.3f), DOT_D / 2 * density,
            Offset(columnCenterX + (k - 1.5f) * pitch, dy))
    }
}

/**
 * Statusleiste im iOS-Stil für normale Handys: Uhrzeit links (Mitte bei 19 % der Breite), rechts Mobilfunk-Balken,
 * WLAN-Fächer, Akku-Kapsel (Mitte bei 81 %). Senkrecht in der Mitte der Android-Statusleistenhöhe [height].
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
            // Akku: Umriss halbtransparent, Füllung nach Stand (laden grün, niedrig rot), Kappe rechts
            drawRoundRect(color.copy(alpha = 0.4f), Offset(x, cy - bh / 2), Size(bw, bh), CornerRadius(4f * u), style = Stroke(1f * u))
            val fill = if (info.charging) CHARGE_GREEN else if (info.battery < 0.2f) LOW_RED else color
            drawRoundRect(fill, Offset(x + 2f * u, cy - bh / 2 + 2f * u), Size((bw - 4f * u) * info.battery.coerceAtLeast(0.05f), bh - 4f * u), CornerRadius(2.5f * u))
            drawRoundRect(color.copy(alpha = 0.4f), Offset(x + bw + 1f * u, cy - 2f * u), Size(1.5f * u, 4f * u), CornerRadius(1f * u))
        }
    }
}

/** WLAN-Fächer: Punkt + 2 Bögen, [bars] 0…4 (0 = alles blass). Mittelpunkt [c] = Fußpunkt des Fächers. */
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
private const val CLOCK_BELOW_CUTOUT = 15f // Uhr-Mitte unter dem Kamera-Loch (Abstand wie oben zum Rand)
private const val CLOCK_HALF_W = 22f
private const val CLOCK_HALF_H = 9f
private const val RING_BELOW = 36f
private const val RING_R = 19f
private const val RING_STROKE = 3f
private const val RING_START = 140f   // Lücke unten (90° = unten), ≈ 100° offen
private const val RING_SWEEP = 260f
private const val DOTS_BELOW = 19f
private const val DOT_PITCH = 7f
private const val DOT_D = 3.5f
private const val SIDE_SHARE = 0.19f
private val CHARGE_GREEN = Color(0xFF34C759)
private val LOW_RED = Color(0xFFFF3B30)
