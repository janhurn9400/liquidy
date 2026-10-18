package io.github.janhurn9400.liquidy.design

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/** iOS icon styles (Customise sheet, recording7 13–30 s). */
enum class IconStyle { Default, Dark, Clear, Tinted }

/** Light/dark variant: Dark → Always/Auto, Clear/Tinted → Light/Dark/Auto. */
enum class IconVariant { Light, Dark, Auto }

/** Where the Tinted colour comes from (iOS 27 Customise: phone / wallpaper / eyedropper). */
enum class TintSource { Device, Wallpaper, Custom }

/**
 * Chosen icon style (global, Compose state → all icons redraw immediately). Saved in SharedPreferences.
 * [hue] 0…360, [saturation] 0…1 for Tinted.
 */
object IconAppearance {
    var style by mutableStateOf(IconStyle.Default)
    var variant by mutableStateOf(IconVariant.Auto)
    var hue by mutableStateOf(DEFAULT_HUE)
    var saturation by mutableStateOf(DEFAULT_SAT)
    var tintSource by mutableStateOf(TintSource.Custom)
    /** Customise › sun: dim the wallpaper while the system is in dark mode (iOS). */
    var dimWallpaper by mutableStateOf(false)
    /** Customise › size toggle: large icons without labels (iOS 18+). */
    var largeIcons by mutableStateOf(false)
    /** System in dark mode (for Auto), set from MainActivity. */
    var systemDark by mutableStateOf(false)

    /** Dark style: "Always" = always dark, "Auto" = only in dark mode. Clear/Tinted: Light/Dark/Auto. */
    val dark: Boolean get() = when (variant) {
        IconVariant.Dark -> true
        IconVariant.Light -> style == IconStyle.Dark // with the Dark style "Always" sits in the Light slot
        IconVariant.Auto -> systemDark
    }

    /** Effective style: Dark with "Auto" in light mode = Default. */
    val effective: IconStyle get() = if (style == IconStyle.Dark && !dark) IconStyle.Default else style

    fun tint(value: Float, satScale: Float = 1f): Color =
        Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, (saturation * satScale).coerceIn(0f, 1f), value.coerceIn(0f, 1f))))

    /** Tinted (Light variant): opaque plate colour for icons and widgets, top or bottom of its slight gradient. */
    fun tintPlate(top: Boolean = true): Color =
        tint(if (top) DesignTokens.TINT_PLATE_V_TOP else DesignTokens.TINT_PLATE_V_BOTTOM, DesignTokens.TINT_PLATE_S)

    /** Tinted (Light variant): glyph colour — almost white with a hint of the hue. */
    fun tintGlyph(): Color = tint(DesignTokens.TINT_GLYPH_V, DesignTokens.TINT_GLYPH_S)

    /**
     * Tinted: maps brightness linearly from the plate colour (black) to the glyph colour (white), alpha unchanged —
     * keeps the motif's inner shading (e.g. camera lens rings) instead of a flat silhouette. [gain] > 1 lets light
     * but not white content already reach the glyph colour; [floor] lifts even dark motif parts that far towards the
     * glyph colour (icons: the whole motif stays clearly lighter than the plate, like on iOS).
     */
    fun duotone(plate: Color, glyph: Color, gain: Float = 1f, floor: Float = 0f): FloatArray {
        // out = plate + (glyph − plate) · (floor + (1 − floor) · gain · brightness)
        fun row(p: Float, q: Float): FloatArray {
            val k = (q - p) * (1 - floor) * gain
            return floatArrayOf(k * 0.299f, k * 0.587f, k * 0.114f, 0f, (p + (q - p) * floor) * 255f)
        }
        return row(plate.red, glyph.red) + row(plate.green, glyph.green) + row(plate.blue, glyph.blue) + floatArrayOf(0f, 0f, 0f, 1f, 0f)
    }

    fun load(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        style = runCatching { IconStyle.valueOf(p.getString("style", null) ?: "") }.getOrDefault(IconStyle.Default)
        variant = runCatching { IconVariant.valueOf(p.getString("variant", null) ?: "") }.getOrDefault(IconVariant.Auto)
        hue = p.getFloat("hue", DEFAULT_HUE)
        saturation = p.getFloat("sat", DEFAULT_SAT)
        tintSource = runCatching { TintSource.valueOf(p.getString("source", null) ?: "") }.getOrDefault(TintSource.Custom)
        dimWallpaper = p.getBoolean("dim", false)
        largeIcons = p.getBoolean("large", false)
    }

    /**
     * Tint colour from [source]: Device = the system accent colour (Material You — Android's counterpart of "matches the
     * phone"), Wallpaper = the wallpaper's primary colour, Custom = keep the slider values. Sets [hue]/[saturation].
     */
    fun applySource(context: Context, source: TintSource) {
        tintSource = source
        val c: Int? = when (source) {
            TintSource.Device -> runCatching { context.getColor(android.R.color.system_accent1_500) }.getOrNull()
            TintSource.Wallpaper -> runCatching {
                android.app.WallpaperManager.getInstance(context).getWallpaperColors(android.app.WallpaperManager.FLAG_SYSTEM)?.primaryColor?.toArgb()
            }.getOrNull()
            TintSource.Custom -> null
        }
        if (c != null) {
            val hsv = FloatArray(3).also { android.graphics.Color.colorToHSV(c, it) }
            hue = hsv[0]; saturation = hsv[1].coerceIn(MIN_SOURCE_SAT, 1f)
        }
        save(context)
    }

    fun save(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("style", style.name).putString("variant", variant.name).putFloat("hue", hue).putFloat("sat", saturation)
            .putString("source", tintSource.name).putBoolean("dim", dimWallpaper).putBoolean("large", largeIcons).apply()
    }

    private const val PREFS = "icons"
    private const val DEFAULT_HUE = 290f  // purple like in the video (recording7 22.4 s)
    private const val DEFAULT_SAT = 0.6f
    private const val MIN_SOURCE_SAT = 0.35f // grey wallpapers/accents would give an almost colourless tint
}
