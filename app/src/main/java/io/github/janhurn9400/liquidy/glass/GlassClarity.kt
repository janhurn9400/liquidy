package io.github.janhurn9400.liquidy.glass

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import io.github.janhurn9400.liquidy.design.DesignTokens

/**
 * iOS-Einstellung Liquid Glass „Klar ↔ Getönt“ (Einstellungen › Darstellung › Liquid Glass, RESEARCH Glas-Regler).
 * [amount] 0 = klar (bisheriges, gemessenes Glas) … 1 = getönt (milchig, keine sichtbare Brechung mehr).
 *
 * Gemessen am Nutzer-iPhone 10-14 (Dock + Suchfeld, drei Reglerstellungen, T-GLASS.frost*):
 * - Unschärfe im Glas: klar σ ≈ 0,4 pt, Mitte ≈ 1,3 pt, getönt ≥ 4 pt (völlig glatt) — Farbe des Docks bleibt fast gleich.
 * - Getönt: keine Brechung mehr (Ränder nur weichgezeichnet, nichts verbogen) und ein neutral-grauer Schleier — helles Glas
 *   etwas dunkler, dunkles (Suchfeld) heller, Farben blasser. Bis zur Mitte sieht es aus wie klar.
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

    /** Mindest-Unschärfe (dp) für [t]: 0 → keine, Mitte → T-GLASS.frostMid, getönt → T-GLASS.frostTinted. */
    fun frostBlurDp(t: Float = amount): Float {
        val mid = DesignTokens.glassFrostMid.value; val full = DesignTokens.glassFrostTinted.value
        return if (t <= 0.5f) mid * (t / 0.5f) else mid + (full - mid) * ((t - 0.5f) / 0.5f)
    }

    /** Anteil „getönt“ für die Tönung (0 bis zur Mitte, dann bis 1) — Mitte sieht am iPhone noch aus wie klar. */
    fun tintShare(t: Float = amount): Float = ((t - 0.5f) / 0.5f).coerceIn(0f, 1f)
}
