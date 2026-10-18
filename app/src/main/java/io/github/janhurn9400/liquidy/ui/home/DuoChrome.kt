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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import io.github.janhurn9400.liquidy.glass.LiquidGlass
import io.github.janhurn9400.liquidy.glass.glassCapsule
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Magnifier button (Duo): round Liquid Glass button in the right column. Tap → lights up white, disappears while
 * search is open, and is back immediately after closing (recording 21:28, 41.15 / 41.22 / 42.54 s).
 */
@Composable
fun DuoSearchButton(model: HomeModel, geo: HomeGeometry, density: Float) {
    // Pulling down to search: the magnifier fades out smoothly (follows the pull) instead of disappearing hard [Duo, user 10-11];
    // on tap it first lights up white (duoLupePress) and then goes
    val searchFade = if (model.duoLupePress.value > 0.01f) 0f
        else (model.searchDim() / (io.github.janhurn9400.liquidy.design.DesignTokens.searchDimMax.value * LUPE_FADE_FULL_AT)).coerceIn(0f, 1f)
    if (searchFade >= 0.999f) return
    // Edit mode: magnifier gone [Duo, video 6, 11 s / video 4, 10 s]
    val hide = model.badgeScale.value.coerceIn(0f, 1f)
    if (hide >= 0.999f) return
    val r = geo.pillRect
    // Reference (10:40, 13 s): almost clear glass without tint and without colour fringe, fine rim (top/bottom light, sides
    // a dark hairline), magnifier white and thin (≈ 1.4 pt stroke, circle ≈ 13 pt) slightly left above the centre
    LiquidGlass(
        shape = glassCapsule(),
        modifier = Modifier.offset { IntOffset(r.left.roundToInt(), r.top.roundToInt()) }.size(Dp(r.width / density))
            .graphicsLayer { alpha = (1f - model.badgeScale.value.coerceIn(0f, 1f)) * (1f - searchFade) },
        whiteTint = 0.85f * model.duoLupePress.value,
        blackTint = 0f,
        rimAlpha = LUPE_RIM,
        onWallpaper = true,
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

/** Centre of the page dots (unfolded they move under the right page with the Today page open). */
private fun duoDotsCenter(geo: HomeGeometry, pagerPos: Float): Offset = if (geo.unfolded) {
    val t = (-pagerPos).coerceIn(0f, 1f)
    Offset(geo.duoDotsHomeX + (geo.duoDotsTodayX - geo.duoDotsHomeX) * t, geo.duoDotsCenter.y)
} else geo.duoDotsCenter

/** Glass capsule around the dots in edit mode [MEASURED video 6, 11 s: 56 × 25 pt, centred on the dots, 2 dots]. */
fun duoDotsCapsule(geo: HomeGeometry, pagerPos: Float, density: Float, dotCount: Int = 2): androidx.compose.ui.geometry.Rect {
    val c = duoDotsCenter(geo, pagerPos)
    val w = (DOTS_CAPSULE_W + DOT_PITCH * (dotCount - 2).coerceAtLeast(0)) * density
    val h = DOTS_CAPSULE_H * density
    return androidx.compose.ui.geometry.Rect(c.x - w / 2, c.y - h / 2, c.x + w / 2, c.y + h / 2)
}

/** iPhone Duo style: page dots permanently below the grid (unfolded below the right page). The magnifier is the SearchPill.
 *  In edit mode ([edit] 0…1) they sit in a clear glass capsule (tap → page overview). */
@Composable
fun DuoDots(geo: HomeGeometry, pager: PagerState, density: Float, dotCount: Int, edit: () -> Float = { 0f }) {
    if (dotCount <= 1 && edit() <= 0.001f) return // iOS: in edit mode even with one page (access to the page overview)
    val e = edit().coerceIn(0f, 1f)
    if (e > 0.001f) {
        val r = duoDotsCapsule(geo, pager.position.value, density, dotCount)
        LiquidGlass(
            shape = glassCapsule(),
            modifier = Modifier.offset { IntOffset(r.left.roundToInt(), r.top.roundToInt()) }
                .size(Dp(r.width / density), Dp(r.height / density))
                .graphicsLayer { alpha = e; scaleX = 0.8f + 0.2f * e; scaleY = 0.8f + 0.2f * e },
            whiteTint = 0.08f, blackTint = 0f, rimAlpha = LUPE_RIM,
            onWallpaper = true, // edit mode only: lies on the wallpaper, jiggling icons would force a live re-record every frame
        )
    }
    Canvas(Modifier.fillMaxSize()) {
        // unfolded: centred above the area left of the dock (x 436 pt); with the Today page open they move under the
        // right page (x 624 pt) [MEASURED 19:18, 1.5 s / 2.6 s]
        val c = duoDotsCenter(geo, pager.position.value)
        val pitch = DOT_PITCH * density; val rad = DOT_D / 2 * density
        // active = right visible page (unfolded) or the page (folded) [MEASURED frame 0.5 s: middle dot]
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
private const val LUPE_FADE_FULL_AT = 0.4f // share of full dimming at which the magnifier is fully gone
private const val LUPE_OUTLINE = 0.16f
private const val DOT_PITCH = 17.7f // pt [MEASURED]
private const val DOT_D = 7f
private const val DOTS_CAPSULE_W = 56f
private const val DOTS_CAPSULE_H = 25f
