package io.github.janhurn9400.liquidy.glass

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import io.github.janhurn9400.liquidy.design.DesignTokens

/**
 * iOS setting Liquid Glass "Clear ↔ Tinted" (Settings › Appearance › Liquid Glass, RESEARCH glass slider).
 * [amount] 0 = clear (the previous, measured glass) … 1 = tinted (milky, no visible refraction anymore).
 *
 * Measured on the user's iPhone 10-14 (dock + search field, three slider positions, T-GLASS.frost*):
 * - Blur in the glass: clear σ ≈ 0.4 pt, middle ≈ 1.3 pt, tinted ≥ 4 pt (completely smooth) — the dock colour stays almost the same.
 * - Tinted: no refraction anymore (edges only softened, nothing bent) and a neutral grey veil — light glass
 *   a bit darker, dark (search field) lighter, colours paler. Up to the middle it looks like clear.
 */
object GlassClarity {
    private const val PREFS = "glass"
    private const val KEY = "clarity"

    var amount by mutableFloatStateOf(0f)
        private set

    fun load(c: Context) {
        amount = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getFloat(KEY, 0f).coerceIn(0f, 1f)
    }

    fun set(c: Context, value: Float) {
        amount = value.coerceIn(0f, 1f)
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putFloat(KEY, amount).apply()
    }

    /** Minimum blur (dp) for [t]: 0 → none, middle → T-GLASS.frostMid, tinted → T-GLASS.frostTinted. */
    fun frostBlurDp(t: Float = amount): Float {
        val mid = DesignTokens.glassFrostMid.value; val full = DesignTokens.glassFrostTinted.value
        return if (t <= 0.5f) mid * (t / 0.5f) else mid + (full - mid) * ((t - 0.5f) / 0.5f)
    }

    /** Share "tinted" for the tint (0 up to the middle, then up to 1) — the middle still looks like clear on the iPhone. */
    fun tintShare(t: Float = amount): Float = ((t - 0.5f) / 0.5f).coerceIn(0f, 1f)
}
