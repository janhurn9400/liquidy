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
 * Haptics — deliberately sparing: only where iOS ticks noticeably (context menu opens, edit mode starts),
 * NOT e.g. when widget sizes snap. Always switchable off (setup + edit menu), because many Android phones
 * only have "mushy" vibration motors. Default: on if the device can do precise clicks (composition primitives),
 * otherwise off. Runs via performHapticFeedback → also respects the system setting "Haptic feedback".
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

    /** Precise motor (short, crisp clicks possible) — Taptic Engine-like. */
    fun hasPreciseMotor(c: Context): Boolean = runCatching {
        c.getSystemService(Vibrator::class.java)?.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_CLICK) == true
    }.getOrDefault(false)

    /** A short, firm tick (menu opens / edit mode starts). */
    fun tick(view: View) {
        if (enabled) view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }
}
