package io.github.janhurn9400.liquidy.data

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Haptik — bewusst sparsam: nur wo iOS spürbar tickt (Kontextmenü geht auf, Bearbeiten-Modus beginnt),
 * NICHT z. B. beim Einrasten von Widget-Größen. Immer abschaltbar (Setup + Bearbeiten-Menü), weil viele Android-Handys
 * nur „matschige“ Vibrationsmotoren haben. Voreinstellung: an, wenn das Gerät präzise Klicks kann (Composition-Primitive),
 * sonst aus. Läuft über performHapticFeedback → respektiert auch die System-Einstellung „Haptisches Feedback“.
 */
object Haptics {
    private const val PREFS = "haptics"
    var enabled by mutableStateOf(false)
        private set

    fun load(c: Context) {
        enabled = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("on", hasPreciseMotor(c))
    }

    fun set(c: Context, on: Boolean) {
        enabled = on
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("on", on).apply()
    }

    /** Präziser Motor (kurze, knackige Klicks möglich) — Taptic-Engine-ähnlich. */
    fun hasPreciseMotor(c: Context): Boolean = runCatching {
        c.getSystemService(Vibrator::class.java)?.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_CLICK) == true
    }.getOrDefault(false)

    /** Ein kurzer, fester Tick (Menü auf / Bearbeiten beginnt). */
    fun tick(view: View) {
        if (enabled) view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }
}
