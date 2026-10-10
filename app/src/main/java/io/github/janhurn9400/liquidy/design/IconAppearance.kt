package io.github.janhurn9400.liquidy.design

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/** iOS-Icon-Stile (Customise-Blatt, recording7 13–30 s). */
enum class IconStyle { Default, Dark, Clear, Tinted }

/** Hell/Dunkel-Variante: Dark → Always/Auto, Clear/Tinted → Light/Dark/Auto. */
enum class IconVariant { Light, Dark, Auto }

/**
 * Gewählter Icon-Stil (global, Compose-State → alle Icons zeichnen sofort neu). Gespeichert in SharedPreferences.
 * [hue] 0…360, [saturation] 0…1 für Tinted.
 */
object IconAppearance {
    var style by mutableStateOf(IconStyle.Default)
    var variant by mutableStateOf(IconVariant.Auto)
    var hue by mutableStateOf(DEFAULT_HUE)
    var saturation by mutableStateOf(DEFAULT_SAT)
    /** System im Dunkelmodus (für Auto), gesetzt aus MainActivity. */
    var systemDark by mutableStateOf(false)

    /** Dark-Stil: „Always“ = immer dunkel, „Auto“ = nur im Dunkelmodus. Clear/Tinted: Light/Dark/Auto. */
    val dark: Boolean get() = when (variant) {
        IconVariant.Dark -> true
        IconVariant.Light -> style == IconStyle.Dark // beim Dark-Stil steht „Always“ auf dem Light-Platz
        IconVariant.Auto -> systemDark
    }

    /** Effektiver Stil: Dark mit „Auto“ im Hellmodus = Default. */
    val effective: IconStyle get() = if (style == IconStyle.Dark && !dark) IconStyle.Default else style

    fun tint(value: Float, satScale: Float = 1f): Color =
        Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, (saturation * satScale).coerceIn(0f, 1f), value.coerceIn(0f, 1f))))

    fun load(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        style = runCatching { IconStyle.valueOf(p.getString("style", null) ?: "") }.getOrDefault(IconStyle.Default)
        variant = runCatching { IconVariant.valueOf(p.getString("variant", null) ?: "") }.getOrDefault(IconVariant.Auto)
        hue = p.getFloat("hue", DEFAULT_HUE)
        saturation = p.getFloat("sat", DEFAULT_SAT)
    }

    fun save(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("style", style.name).putString("variant", variant.name).putFloat("hue", hue).putFloat("sat", saturation).apply()
    }

    private const val PREFS = "icons"
    private const val DEFAULT_HUE = 290f  // Lila wie im Video (recording7 22,4 s)
    private const val DEFAULT_SAT = 0.6f
}
