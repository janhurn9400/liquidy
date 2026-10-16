package io.github.janhurn9400.liquidy.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * "🍎 Quirks" (Launcher settings): the default is 1:1 like iOS, including its peculiarities. Here they can be relaxed
 * one by one — every option is deliberately "not like the original". (More apps in the Duo dock lives in [FoldableStyle].)
 */
object Quirks {
    private const val PREFS = "quirks"

    /** Widgets in any row (iOS: 2-row-tall widgets only in the row pairs 1–2, 3–4, 5–6). */
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
