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
 * Glas-Quelle nur aus dem Wallpaper (ohne Icons) — für Glas, das selbst in der aufgenommenen Icon-Ebene liegt
 * (Ordner-Icons): dort kann es die Ebene nicht brechen, in der es steckt. Gezeichnet mit der aktuellen Umrechnung
 * Bildschirm → Glasfläche (inkl. Wackeln/Seitenwischen), damit der Hintergrund still steht.
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

/** Unscharfes Wallpaper + aktueller Anteil (Heute-Ansicht/Mediathek), für alle Glas-Quellen. */
class WallpaperBlur(val blurred: ImageBitmap?, val amount: () -> Float)

val LocalWallpaperBlur = androidx.compose.runtime.staticCompositionLocalOf { WallpaperBlur(null) { 0f } }
