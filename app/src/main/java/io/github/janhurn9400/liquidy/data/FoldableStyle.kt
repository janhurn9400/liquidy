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

    /** Duo-Stil aktiv (Foldables; Tablets nur im Querformat). */
    private var chosen by mutableStateOf(false)
    /** Tablet im Hochformat: dort passt das Duo-Layout nicht (eine schmale Seite neben viel Leere) → normaler Stil. */
    private var tabletPortrait by mutableStateOf(false)
    val duoStyle: Boolean get() = chosen && !tabletPortrait

    /** Von MainActivity bei jeder Größenänderung gesetzt. */
    fun updateWindow(c: Context, widthPx: Int, heightPx: Int) { tabletPortrait = isTablet(c) && heightPx > widthPx }

    fun isFoldable(c: Context): Boolean = c.packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_HINGE_ANGLE)

    /** Tablet: kleinste Bildschirmbreite ≥ 600 dp (Android-Grenze für „große Bildschirme“). */
    fun isTablet(c: Context): Boolean = c.resources.configuration.smallestScreenWidthDp >= 600 && !isFoldable(c)

    /** Duo-Stil möglich: Foldables und Tablets (zwei Seiten nebeneinander im Querformat). */
    fun supportsDuo(c: Context): Boolean = isFoldable(c) || isTablet(c)

    /** Eigene Erweiterung (nicht wie am Duo): senkrechtes Dock nimmt mehr als 4 Apps auf, so viele wie in die Spalte passen. */
    var moreDockApps by mutableStateOf(false)
        private set

    fun load(c: Context) {
        val prefs = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        chosen = supportsDuo(c) && prefs.getBoolean("duo", true) // auf Foldables/Tablets vorausgewählt
        moreDockApps = prefs.getBoolean("moreDock", false)
    }

    fun setMoreDockApps(c: Context, on: Boolean) {
        moreDockApps = on
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("moreDock", on).apply()
    }

    fun set(c: Context, on: Boolean) {
        chosen = on && supportsDuo(c)
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("duo", on).apply()
    }
}
