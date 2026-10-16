package io.github.janhurn9400.liquidy.design

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue

/**
 * Duo style unfolded on foldables narrower than the Duo (e.g. Galaxy Z Fold 823 pt, Pixel Fold ≈ 880 pt):
 * the whole UI is scaled down uniformly so the width in pt matches that of the Duo. That way all
 * measured proportions (margins, distance of the two pages, paths out of view, icon and widget sizes) stay like on the Duo —
 * instead of squeezing icons at original size. Larger displays (tablets) are enlarged up to 125 %.
 */
object UiScale {
    var factor by mutableFloatStateOf(1f)
        private set

    /** Width of the Duo inner display (pt) that is scaled to. */
    private const val DUO_WIDTH_PT = 951.3f
    /** Not smaller than 78 % — otherwise text would become illegible. */
    private const val MIN_FACTOR = 0.78f

    /** Width of the Duo outer display (pt) — folded (e.g. Z Fold outer display 369 pt) is scaled to it. */
    private const val DUO_FOLDED_WIDTH_PT = 466f

    /** Height of the Duo (pt) unfolded / folded — the height must also suffice for 6 rows. */
    private const val DUO_HEIGHT_PT = 667f
    private const val DUO_FOLDED_HEIGHT_PT = 678f
    /** Larger displays (tablets): enlarge up to 125 % so it doesn't look lost (iPad: larger icons). */
    private const val MAX_FACTOR = 1.25f

    fun compute(duo: Boolean, widthPx: Int, heightPx: Int, density: Float): Float {
        if (!duo || widthPx <= 0 || heightPx <= 0) return 1f
        val landscape = widthPx > heightPx
        val f = minOf(widthPx / density / (if (landscape) DUO_WIDTH_PT else DUO_FOLDED_WIDTH_PT),
            heightPx / density / (if (landscape) DUO_HEIGHT_PT else DUO_FOLDED_HEIGHT_PT))
        // The Duo itself and almost equally sized displays: unchanged
        return if (kotlin.math.abs(f - 1f) < 0.03f) 1f else f.coerceIn(MIN_FACTOR, MAX_FACTOR)
    }

    fun update(f: Float) { factor = f }

    /** px per pt as the UI uses it (instead of displayMetrics.density). */
    fun density(c: Context): Float = c.resources.displayMetrics.density * factor
}
