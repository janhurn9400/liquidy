package io.github.janhurn9400.liquidy.design

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue

/**
 * Duo-Stil aufgeklappt auf Foldables, die schmaler sind als das Duo (z. B. Galaxy Z Fold 823 pt, Pixel Fold ≈ 880 pt):
 * die ganze Oberfläche wird gleichmäßig verkleinert, sodass die Breite in pt der des Duo entspricht. So bleiben alle
 * gemessenen Verhältnisse (Ränder, Abstand der beiden Seiten, Wege aus dem Bild, Icon- und Widgetgrößen) wie am Duo —
 * statt Icons in Originalgröße zusammenzuquetschen. Größere Displays (Tablets) werden bis 125 % vergrößert.
 */
object UiScale {
    var factor by mutableFloatStateOf(1f)
        private set

    /** Breite des Duo-Innendisplays (pt), auf die skaliert wird. */
    private const val DUO_WIDTH_PT = 951.3f
    /** Nicht kleiner als 78 % — sonst würde Text unleserlich. */
    private const val MIN_FACTOR = 0.78f

    /** Breite des Duo-Außendisplays (pt) — zugeklappt (z. B. Z-Fold-Außendisplay 369 pt) wird darauf skaliert. */
    private const val DUO_FOLDED_WIDTH_PT = 466f

    /** Höhe des Duo (pt) aufgeklappt / zugeklappt — auch die Höhe muss für 6 Reihen reichen. */
    private const val DUO_HEIGHT_PT = 667f
    private const val DUO_FOLDED_HEIGHT_PT = 678f
    /** Größere Displays (Tablets): bis 125 % vergrößern, damit es nicht verloren wirkt (iPad: größere Icons). */
    private const val MAX_FACTOR = 1.25f

    fun compute(duo: Boolean, widthPx: Int, heightPx: Int, density: Float): Float {
        if (!duo || widthPx <= 0 || heightPx <= 0) return 1f
        val landscape = widthPx > heightPx
        val f = minOf(widthPx / density / (if (landscape) DUO_WIDTH_PT else DUO_FOLDED_WIDTH_PT),
            heightPx / density / (if (landscape) DUO_HEIGHT_PT else DUO_FOLDED_HEIGHT_PT))
        // Duo selbst und fast gleich große Displays: unverändert
        return if (kotlin.math.abs(f - 1f) < 0.03f) 1f else f.coerceIn(MIN_FACTOR, MAX_FACTOR)
    }

    fun update(f: Float) { factor = f }

    /** px je pt, wie ihn die Oberfläche nutzt (statt displayMetrics.density). */
    fun density(c: Context): Float = c.resources.displayMetrics.density * factor
}
