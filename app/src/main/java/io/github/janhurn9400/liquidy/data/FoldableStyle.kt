package io.github.janhurn9400.liquidy.data

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Foldables: Erkennung über das Systemmerkmal „Scharnier-Sensor“ (android.hardware.sensor.hinge_angle, Android 11+;
 * gemeldet von Pixel Fold, Galaxy Z Fold u. a.) und die Wahl des Nutzers, ob der Homescreen wie das iPhone Duo aussieht
 * (zwei Seiten nebeneinander aufgeklappt, Dock rechts senkrecht, runder Suchknopf). Abgefragt im Willkommen.
 */
object FoldableStyle {
    private const val PREFS = "foldable"

    /** Duo-Stil aktiv (nur sinnvoll auf Foldables). */
    var duoStyle by mutableStateOf(false)
        private set

    fun isFoldable(c: Context): Boolean = c.packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_HINGE_ANGLE)

    fun load(c: Context) {
        duoStyle = isFoldable(c) && c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("duo", true) // auf Foldables vorausgewählt
    }

    fun set(c: Context, on: Boolean) {
        duoStyle = on && isFoldable(c)
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("duo", on).apply()
    }
}
