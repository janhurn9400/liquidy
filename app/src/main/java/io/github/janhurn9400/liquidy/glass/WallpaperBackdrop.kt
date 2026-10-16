package io.github.janhurn9400.liquidy.glass

import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.unit.Density
import com.kyant.backdrop.Backdrop

/**
 * Glass source from the wallpaper only (without icons) — for glass that itself lies in the recorded icon layer
 * (folder icons): there it can't refract the layer it sits in. Drawn with the current mapping
 * screen → glass surface (incl. jiggle/page swipe), so the background stands still.
 */
class WallpaperBackdrop(
    private val wallpaper: ImageBitmap,
    private val blurred: ImageBitmap? = null,
    private val blurAmount: () -> Float = { 0f },
) : Backdrop {
    override val isCoordinatesDependent: Boolean get() = true

    override fun DrawScope.drawBackdrop(density: Density, coordinates: LayoutCoordinates?, layerBlock: (GraphicsLayerScope.() -> Unit)?) {
        val m = Matrix()
        if (coordinates != null && coordinates.isAttached) coordinates.transformFrom(coordinates.findRootCoordinates(), m)
        withTransform({ transform(m) }) {
            drawImage(wallpaper)
            val a = blurAmount()
            if (blurred != null && a > 0f) drawImage(blurred, alpha = a.coerceIn(0f, 1f))
        }
    }
}

/** Blurred wallpaper + current share (Today view/App Library), for all glass sources. */
class WallpaperBlur(val blurred: ImageBitmap?, val amount: () -> Float)

val LocalWallpaperBlur = androidx.compose.runtime.staticCompositionLocalOf { WallpaperBlur(null) { 0f } }
