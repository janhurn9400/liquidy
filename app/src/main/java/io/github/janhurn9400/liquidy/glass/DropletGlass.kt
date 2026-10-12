package io.github.janhurn9400.liquidy.glass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.janhurn9400.liquidy.design.DesignTokens
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import com.kyant.backdrop.shadow.Shadow

/**
 * Glas mit frei geformter Fläche (Tropfen, wabbelnde Form): wie [LiquidGlass], aber ohne Linse (die braucht ein
 * abgerundetes Rechteck) — Farbverstärkung, Blur, Lichtsaum, Schatten, Tönung.
 */
@Composable
fun DropletGlass(shape: () -> Shape, modifier: Modifier = Modifier, blur: Dp, whiteTint: () -> Float, blackTint: () -> Float,
                 content: @Composable BoxScope.() -> Unit = {}) {
    val backdrop = LocalGlassBackdrop.current
    if (backdrop == null) { Box(modifier, content = content); return }
    Box(
        modifier.drawBackdrop(
            backdrop = backdrop,
            shape = shape,
            effects = {
                colorControls(saturation = DesignTokens.glassSaturation.value)
                val b = blur.toPx(); if (b > 0.5f) blur(b)
            },
            highlight = {
                Highlight(width = DesignTokens.glassRimWidth.dp, alpha = DesignTokens.glassRimHighlight.value,
                    style = HighlightStyle.Default(angle = DesignTokens.glassLightAngle.value, falloff = DesignTokens.glassLightFalloff.value))
            },
            shadow = { Shadow(radius = DesignTokens.glassShadowRadius.dp, color = Color.Black.copy(alpha = DesignTokens.glassShadowAlpha.value)) },
            onDrawSurface = {
                val bt = blackTint(); if (bt > 0f) drawRect(Color.Black.copy(alpha = bt))
                val wt = whiteTint().coerceAtMost(1f); if (wt > 0f) drawRect(Color.White.copy(alpha = wt))
            },
        ),
        content = content,
    )
}

/**
 * Tropfen-/Wabbelform in der eigenen Box (0…W, 0…H): abgerundetes Rechteck mit eigenen Eckradien, nach außen gewölbten
 * Kanten ([bulge] in px, + = nach außen) und einem Schweif an der Ecke oben rechts ([tail] in px, Richtung Knopf).
 * Eckradien werden auf die halbe Kante begrenzt (kleine Tropfen = Kreis/Oval).
 */
class DropletShape(
    private val rTL: Float, private val rTR: Float, private val rBR: Float, private val rBL: Float,
    private val bulge: Float, private val tail: Float,
    /** Rand (px) um die eigentliche Form: Wölbung und Kuppe dürfen hinausragen, ohne abgeschnitten zu werden. */
    private val inset: Float = 0f,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val w = size.width - 2 * inset; val h = size.height - 2 * inset; val m = minOf(w, h) / 2
        val tl = rTL.coerceIn(0f, m); val tr = rTR.coerceIn(0f, m); val br = rBR.coerceIn(0f, m); val bl = rBL.coerceIn(0f, m)
        val k = 0.5523f // Kreis-Näherung mit kubischen Bézierkurven
        val p = Path()
        p.moveTo(tl, 0f)
        // obere Kante (gewölbt) bis zur Ecke oben rechts
        p.cubicTo(tl + (w - tl - tr) / 3, -bulge, w - tr - (w - tl - tr) / 3, -bulge, w - tr, 0f)
        if (tail > 0.5f) {
            // Schweif: Ecke oben rechts läuft spitz zum Knopf aus
            // Schweif mit RUNDER Kuppe (Tropfen, der in den Knopf gezogen wird): Kuppe = Halbkreis mit Radius rc
            val tip = Offset(w - tr * 0.3f + tail * 0.7071f, tr * 0.3f - tail * 0.7071f)
            val rc = (tail * 0.75f).coerceAtMost(tr * 0.9f).coerceAtLeast(1f)
            val a = Offset(tip.x - rc * 0.7071f, tip.y - rc * 0.7071f)   // linke Flanke oben
            val b = Offset(tip.x + rc * 0.7071f, tip.y + rc * 0.7071f)   // rechte Flanke unten
            val far = Offset(tip.x + rc * 0.7071f, tip.y - rc * 0.7071f) // äußerster Punkt der Kuppe
            p.cubicTo(w - tr + tr * k, 0f, a.x - tail * 0.35f, a.y + tail * 0.12f, a.x, a.y)
            p.cubicTo(a.x + rc * 0.39f, a.y - rc * 0.39f, far.x - rc * 0.39f, far.y - rc * 0.39f, far.x, far.y)
            p.cubicTo(far.x + rc * 0.39f, far.y + rc * 0.39f, b.x + rc * 0.39f, b.y - rc * 0.39f, b.x, b.y)
            p.cubicTo(b.x - tail * 0.12f, b.y + tail * 0.35f, w, tr - tr * k, w, tr)
        } else p.cubicTo(w - tr + tr * k, 0f, w, tr - tr * k, w, tr)
        p.cubicTo(w + bulge, tr + (h - tr - br) / 3, w + bulge, h - br - (h - tr - br) / 3, w, h - br)
        p.cubicTo(w, h - br + br * k, w - br + br * k, h, w - br, h)
        p.cubicTo(w - br - (w - br - bl) / 3, h + bulge, bl + (w - br - bl) / 3, h + bulge, bl, h)
        p.cubicTo(bl - bl * k, h, 0f, h - bl + bl * k, 0f, h - bl)
        p.cubicTo(-bulge, h - bl - (h - bl - tl) / 3, -bulge, tl + (h - bl - tl) / 3, 0f, tl)
        p.cubicTo(0f, tl - tl * k, tl - tl * k, 0f, tl, 0f)
        p.close()
        if (inset != 0f) p.translate(Offset(inset, inset))
        return Outline.Generic(p)
    }
}
