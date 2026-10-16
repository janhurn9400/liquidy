package io.github.janhurn9400.liquidy.data

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Foldables: detected via the system feature "hinge sensor" (android.hardware.sensor.hinge_angle, Android 11+;
 * reported by Pixel Fold, Galaxy Z Fold etc.) and the user's choice whether the home screen looks like the iPhone Duo
 * (two pages side by side when unfolded, dock vertical on the right, round search button). Asked in the welcome.
 */
object FoldableStyle {
    private const val PREFS = "foldable"

    /** Duo style active (foldables; tablets only in landscape). */
    private var chosen by mutableStateOf(false)
    /** Tablet in portrait: the Duo layout doesn't fit there (one narrow page next to lots of emptiness) → normal style. */
    private var tabletPortrait by mutableStateOf(false)
    val duoStyle: Boolean get() = chosen && !tabletPortrait

    /** Set by MainActivity on every size change. */
    fun updateWindow(c: Context, widthPx: Int, heightPx: Int) { tabletPortrait = isTablet(c) && heightPx > widthPx }

    fun isFoldable(c: Context): Boolean = c.packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_HINGE_ANGLE)

    /** Tablet: smallest screen width ≥ 600 dp (Android's threshold for "large screens"). */
    fun isTablet(c: Context): Boolean = c.resources.configuration.smallestScreenWidthDp >= 600 && !isFoldable(c)

    /** Duo style possible: foldables and tablets (two pages side by side in landscape). */
    fun supportsDuo(c: Context): Boolean = isFoldable(c) || isTablet(c)

    /** Own extension (not like on the Duo): the vertical dock takes more than 4 apps, as many as fit into the column. */
    var moreDockApps by mutableStateOf(false)
        private set

    fun load(c: Context) {
        val prefs = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        chosen = supportsDuo(c) && prefs.getBoolean("duo", true) // preselected on foldables/tablets
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
