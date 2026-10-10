package io.github.janhurn9400.liquidy.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import io.github.janhurn9400.liquidy.glass.LiquidGlass
import io.github.janhurn9400.liquidy.glass.glassCapsule
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Lupenknopf (Duo): runder Liquid-Glass-Knopf in der rechten Spalte. Antippen → leuchtet weiß auf, verschwindet, während die
 * Suche offen ist, und ist nach dem Schließen sofort wieder da (Aufnahme 21:28, 41,15 / 41,22 / 42,54 s).
 */
@Composable
fun DuoSearchButton(model: HomeModel, geo: HomeGeometry, density: Float) {
    if (model.searchVisible && model.duoLupePress.value < 0.01f) return
    val r = geo.pillRect
    // Referenz (10:40, 13 s): fast klares Glas ohne Tönung und ohne Farbsaum, feiner Rand (oben/unten hell, seitlich
    // dunkle Haarlinie), Lupe weiß und dünn (≈ 1,4 pt Strich, Kreis ≈ 13 pt) leicht links oberhalb der Mitte
    LiquidGlass(
        shape = glassCapsule(),
        modifier = Modifier.offset { IntOffset(r.left.roundToInt(), r.top.roundToInt()) }.size(Dp(r.width / density)),
        whiteTint = 0.85f * model.duoLupePress.value,
        blackTint = 0f,
        dispersion = false,
        rimAlpha = LUPE_RIM,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val s = size.width
            drawCircle(Color.Black.copy(alpha = LUPE_OUTLINE), s / 2 - 0.5f * density, style = Stroke(1f * density))
            val sw = s * 0.031f
            val c = Offset(s * 0.44f, s * 0.44f); val rad = s * 0.135f
            val a = c + Offset(rad * 0.72f, rad * 0.72f)
            drawCircle(LUPE_GLYPH, rad, c, style = Stroke(sw))
            drawLine(LUPE_GLYPH, a, Offset(s * 0.665f, s * 0.665f), sw, StrokeCap.Round)
        }
    }
}

/** iPhone-Duo-Stil: Seitenpunkte dauerhaft unter dem Raster (aufgeklappt unter der rechten Seite). Die Lupe ist die SearchPill. */
@Composable
fun DuoDots(geo: HomeGeometry, pager: PagerState, density: Float, dotCount: Int) {
    if (dotCount > 1) Canvas(Modifier.fillMaxSize()) {
        // aufgeklappt: mittig über der Fläche links vom Dock (x 436 pt); mit offener Heute-Seite wandern sie unter die
        // rechte Seite (x 624 pt) [GEMESSEN 19:18, 1,5 s / 2,6 s]
        val c = if (geo.unfolded) {
            val t = (-pager.position.value).coerceIn(0f, 1f)
            Offset(geo.duoDotsHomeX + (geo.duoDotsTodayX - geo.duoDotsHomeX) * t, geo.duoDotsCenter.y)
        } else geo.duoDotsCenter
        val pitch = DOT_PITCH * density; val rad = DOT_D / 2 * density
        // aktiv = rechte sichtbare Seite (aufgeklappt) bzw. die Seite (zugeklappt) [GEMESSEN Bild 0,5 s: mittlerer Punkt]
        val active = (pager.position.value + geo.pagesVisible - 1).coerceIn(0f, (dotCount - 1).toFloat())
        val x0 = c.x - pitch * (dotCount - 1) / 2
        for (k in 0 until dotCount) {
            val on = (1f - abs(k - active)).coerceIn(0f, 1f)
            drawCircle(Color.White.copy(alpha = 0.4f + 0.6f * on), rad, Offset(x0 + k * pitch, c.y))
        }
    }
}

private val LUPE_GLYPH = Color(0xF5FFFFFF)
private const val LUPE_RIM = 0.7f
private const val LUPE_OUTLINE = 0.16f
private const val DOT_PITCH = 17.7f // pt [GEMESSEN]
private const val DOT_D = 7f
