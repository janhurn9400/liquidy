package io.github.janhurn9400.liquidy.tuning

/** Only for testing in the emulator — no iOS values. */
object DebugOptions {
    // The emulator has only ~15 apps (= 1 page). Spreads the app list over 1 + n pages when (re)building the layout, to test paging.
    // Changing it resets the saved layout.
    val demoPages = tunable("DBG.demoPages", "Demo: extra pages (resets layout)", "Debug", 1f, 0f, 5f, "pages")

    // 0 = grey reference wallpaper (like the user's iPhone), 1 = colourful test wallpaper (makes glass refraction visible)
    val wallpaperStyle = tunable("DBG.wallpaper", "Wallpaper: 0 grey (reference) / 1 colourful (glass test)", "Debug", 0f, 0f, 1f)

    // 1 = always lay our own wallpaper copy (sharp) over Android's image — checks whether our crop is congruent
    val wallpaperCopy = tunable("DBG.wallpaperCopy", "Always show wallpaper copy (check crop)", "Debug", 0f, 0f, 1f)

    // Performance test on real devices: 1 = no Liquid Glass at all (no backdrop blur/lens/highlight, flat grey instead)
    val noGlass = tunable("DBG.noGlass", "Liquid Glass off (performance test)", "Debug", 0f, 0f, 1f)

    // Performance test: 1 = no full-screen background blur (folder, search, Today view/App Library, category)
    val noBlur = tunable("DBG.noBlur", "Background blur off (performance test)", "Debug", 0f, 0f, 1f)

    val glassOff: Boolean get() = noGlass.value >= 0.5f
    val blurOff: Boolean get() = noBlur.value >= 0.5f

    /** Flat stand-in for glass surfaces while [glassOff] (keeps buttons and fields visible). */
    val GLASS_OFF_FILL = androidx.compose.ui.graphics.Color(0x59808080)

    fun touch() = Unit
}
