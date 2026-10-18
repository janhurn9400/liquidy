package io.github.janhurn9400.liquidy.glass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawOutline
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
 * Glass with a freely shaped area (droplet, wobbling shape): like [LiquidGlass], but without the lens (it needs a
 * rounded rectangle) — saturation boost, blur, light rim, shadow, tint.
 */
@Composable
fun DropletGlass(shape: () -> Shape, modifier: Modifier = Modifier, blur: Dp, whiteTint: () -> Float, blackTint: () -> Float,
                 content: @Composable BoxScope.() -> Unit = {}) {
    val backdrop = LocalGlassBackdrop.current
    if (backdrop == null) { Box(modifier, content = content); return }
    if (io.github.janhurn9400.liquidy.tuning.DebugOptions.glassOff) {
        Box(modifier.drawBehind { drawOutline(shape().createOutline(size, layoutDirection, this), io.github.janhurn9400.liquidy.tuning.DebugOptions.GLASS_OFF_FILL) }, content = content)
        return
    }
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
 * Droplet/wobble shape in its own box (0…W, 0…H): rounded rectangle with its own corner radii, outwards-bulging
 * edges ([bulge] in px, + = outwards) and a tail at the top-right corner ([tail] in px, towards the button).
 * Corner radii are limited to half the edge (small droplets = circle/oval).
 */
class DropletShape(
    private val rTL: Float, private val rTR: Float, private val rBR: Float, private val rBL: Float,
    private val bulge: Float, private val tail: Float,
    /** Margin (px) around the actual shape: bulge and tip may reach out without being clipped. */
    private val inset: Float = 0f,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val w = size.width - 2 * inset; val h = size.height - 2 * inset; val m = minOf(w, h) / 2
        val tl = rTL.coerceIn(0f, m); val tr = rTR.coerceIn(0f, m); val br = rBR.coerceIn(0f, m); val bl = rBL.coerceIn(0f, m)
        val k = 0.5523f // circle approximation with cubic Bézier curves
        val p = Path()
        p.moveTo(tl, 0f)
        // top edge (bulging) up to the top-right corner
        p.cubicTo(tl + (w - tl - tr) / 3, -bulge, w - tr - (w - tl - tr) / 3, -bulge, w - tr, 0f)
        if (tail > 0.5f) {
            // Tail: the top-right corner runs out to a point towards the button
            // Tail with a ROUND tip (droplet being drawn into the button): tip = semicircle with radius rc
            val tip = Offset(w - tr * 0.3f + tail * 0.7071f, tr * 0.3f - tail * 0.7071f)
            val rc = (tail * 0.75f).coerceAtMost(tr * 0.9f).coerceAtLeast(1f)
            val a = Offset(tip.x - rc * 0.7071f, tip.y - rc * 0.7071f)   // left flank at the top
            val b = Offset(tip.x + rc * 0.7071f, tip.y + rc * 0.7071f)   // right flank at the bottom
            val far = Offset(tip.x + rc * 0.7071f, tip.y - rc * 0.7071f) // outermost point of the tip
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
