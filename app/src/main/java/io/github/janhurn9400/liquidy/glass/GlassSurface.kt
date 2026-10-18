package io.github.janhurn9400.liquidy.glass

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Dp
import io.github.janhurn9400.liquidy.design.DesignTokens
import kotlin.math.roundToInt

/**
 * Liquid Glass surface, stage 1 (RESEARCH §2.8):
 *  - samples our own wallpaper at its own screen position and blurs it (RenderEffect),
 *  - tint (darken/brighten),
 *  - rim: light rim top/bottom, dark rim at the sides (iOS 27, measured on pill & dock).
 * Refraction (AGSL) follows as stage 2.
 */
@Composable
fun GlassSurface(
    wallpaper: ImageBitmap,
    shape: Shape,
    modifier: Modifier = Modifier,
    blur: Dp = DesignTokens.glassBlur.dp,
    tint: Float = DesignTokens.glassTint.value,
    whiteTint: Float = DesignTokens.glassWhiteTint.value,
    /**
     * Transformation the surrounding element currently gets via graphicsLayer (rotation °, offset x/y px; pivot =
     * centre of this surface). It's undone when drawing the wallpaper crop → the background stands still,
     * only the glass moves (otherwise the wallpaper in the folder icon would jiggle along in edit mode).
     */
    counterTransform: (() -> Triple<Float, Float, Float>)? = null,
    content: @Composable BoxScope.() -> Unit = {},
) {
    var origin by remember { mutableStateOf(Offset.Zero) } // only a trigger for redrawing on position change
    val coords = remember { arrayOfNulls<androidx.compose.ui.layout.LayoutCoordinates>(1) }
    Box(modifier.onGloballyPositioned { origin = it.positionInRoot() }) {
        Box(Modifier.matchParentSize().clip(shape)) {
            val blurPx = blur
            Canvas(
                Modifier
                    .matchParentSize()
                    .onGloballyPositioned { coords[0] = it }
                    .graphicsLayer {
                        val r = if (io.github.janhurn9400.liquidy.tuning.DebugOptions.glassOff) 0f else blurPx.toPx()
                        renderEffect = if (r > 0.5f) BlurEffect(r, r, TileMode.Clamp) else null
                    }
            ) {
                origin // read → redraw on position change
                counterTransform?.invoke() // read → redraw on every jiggle frame
                // Current mapping screen → this surface (incl. rotation/offset of all graphicsLayers above):
                // the wallpaper is drawn with it and so stands exactly still, however the element moves.
                val c = coords[0]
                val m = androidx.compose.ui.graphics.Matrix()
                if (c != null && c.isAttached) c.transformFrom(c.findRootCoordinates(), m)
                else m.translate(-origin.x, -origin.y)
                withTransform({ transform(m) }) { drawImage(wallpaper) }
                drawRect(Color.Black.copy(alpha = tint))
                drawRect(Color.White.copy(alpha = whiteTint))
            }
        }
        content()
        GlassRim(shape, Modifier.matchParentSize())
    }
}

/**
 * Liquid Glass edge of app icons and widgets (iOS 27, all icon styles): a hairline of light along the top and bottom
 * edge with a short glow inwards, the sides almost without (T-OBJ-RIM, measured on the user's iPhone 10-18). Drawn inside
 * the already clipped shape, so the outer half of each stroke is cut off.
 */
fun drawObjectRim(scope: androidx.compose.ui.graphics.drawscope.DrawScope, shape: Shape) = with(scope) {
    val outline = shape.createOutline(size, layoutDirection, this)
    fun band(a: Float) = Brush.verticalGradient(0f to Color.White.copy(alpha = a), RIM_FADE to Color.Transparent,
        1f - RIM_FADE to Color.Transparent, 1f to Color.White.copy(alpha = a * RIM_BOTTOM))
    drawOutline(outline, band(DesignTokens.objectRimGlow.value), style = Stroke(width = (RIM_GLOW_W * density) * 2f))
    drawOutline(outline, band(DesignTokens.objectRimLine.value), style = Stroke(width = (RIM_LINE_W * density) * 2f))
}

private const val RIM_LINE_W = 0.45f // dp: the hairline (×2 because half the stroke lies outside the clip)
private const val RIM_GLOW_W = 2.6f  // dp: the glow fading inwards
private const val RIM_FADE = 0.22f   // share of the height over which the light runs out along the sides
private const val RIM_BOTTOM = 0.9f  // bottom edge slightly weaker than the top

@Composable
fun GlassRim(shape: Shape, modifier: Modifier, lightAlpha: Float = DesignTokens.glassRimHighlight.value) {
    Canvas(modifier) {
        val w = DesignTokens.glassRimWidth.dp.toPx()
        if (w <= 0f) return@Canvas
        val light = Color.White.copy(alpha = lightAlpha)
        val dark = Color.Black.copy(alpha = DesignTokens.glassRimShadow.value)
        // vertical gradient: light rim at the top/bottom, dark in the middle (= side edges)
        val brush = Brush.verticalGradient(
            0f to light, 0.3f to dark, 0.7f to dark, 1f to light,
        )
        val inner = Size(size.width - w, size.height - w)
        val outline = shape.createOutline(inner, layoutDirection, this)
        translate(w / 2f, w / 2f) {
            drawOutline(outline, brush, style = Stroke(width = w))
        }
    }
}
