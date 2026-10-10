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
 * Liquid-Glass-Fläche, Stufe 1 (RESEARCH §2.8):
 *  - tastet den eigenen Wallpaper an der eigenen Bildschirmposition ab und blurt ihn (RenderEffect),
 *  - Tönung (abdunkeln/aufhellen),
 *  - Rand: heller Lichtsaum oben/unten, dunkler Rand seitlich (iOS 27, gemessen an Pill & Dock).
 * Refraktion (AGSL) folgt als Stufe 2.
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
     * Transformation, die das umgebende Element gerade per graphicsLayer erfährt (Drehung °, Versatz x/y px; Drehpunkt =
     * Mitte dieser Fläche). Wird beim Zeichnen des Wallpaper-Ausschnitts zurückgenommen → der Hintergrund steht still,
     * nur das Glas bewegt sich (sonst wackelt beim Bearbeiten das Wallpaper im Ordner-Icon mit).
     */
    counterTransform: (() -> Triple<Float, Float, Float>)? = null,
    content: @Composable BoxScope.() -> Unit = {},
) {
    var origin by remember { mutableStateOf(Offset.Zero) } // nur Auslöser fürs Neuzeichnen bei Positionsänderung
    val coords = remember { arrayOfNulls<androidx.compose.ui.layout.LayoutCoordinates>(1) }
    Box(modifier.onGloballyPositioned { origin = it.positionInRoot() }) {
        Box(Modifier.matchParentSize().clip(shape)) {
            val blurPx = blur
            Canvas(
                Modifier
                    .matchParentSize()
                    .onGloballyPositioned { coords[0] = it }
                    .graphicsLayer {
                        val r = blurPx.toPx()
                        renderEffect = if (r > 0.5f) BlurEffect(r, r, TileMode.Clamp) else null
                    }
            ) {
                origin // lesen → Neuzeichnen bei Positionsänderung
                counterTransform?.invoke() // lesen → Neuzeichnen in jedem Wackel-Frame
                // Aktuelle Umrechnung Bildschirm → diese Fläche (inkl. Drehung/Versatz aller graphicsLayer darüber):
                // der Wallpaper wird damit gezeichnet und steht dadurch exakt still, egal wie das Element sich bewegt.
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

@Composable
fun GlassRim(shape: Shape, modifier: Modifier, lightAlpha: Float = DesignTokens.glassRimHighlight.value) {
    Canvas(modifier) {
        val w = DesignTokens.glassRimWidth.dp.toPx()
        if (w <= 0f) return@Canvas
        val light = Color.White.copy(alpha = lightAlpha)
        val dark = Color.Black.copy(alpha = DesignTokens.glassRimShadow.value)
        // vertikaler Verlauf: oben/unten Lichtsaum, in der Mitte (= Seitenkanten) dunkel
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
