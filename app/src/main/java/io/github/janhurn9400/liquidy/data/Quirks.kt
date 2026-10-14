package io.github.janhurn9400.liquidy.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * „🍎 Quirks“ (Launcher-Einstellungen): Standard ist 1:1 wie iOS, auch mit dessen Eigenheiten. Hier lassen sie sich einzeln
 * lockern — jede Option ist bewusst „nicht wie im Original“. (Mehr Apps im Duo-Dock liegt in [FoldableStyle].)
 */
object Quirks {
    private const val PREFS = "quirks"

    /** Widgets in jeder Reihe (iOS: 2 Reihen hohe Widgets nur in den Reihenpaaren 1–2, 3–4, 5–6). */
    var freeWidgetRows by mutableStateOf(false)
        private set

    fun load(c: Context) {
        freeWidgetRows = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("freeWidgetRows", false)
    }

    fun setFreeWidgetRows(c: Context, on: Boolean) {
        freeWidgetRows = on
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("freeWidgetRows", on).apply()
    }
}
