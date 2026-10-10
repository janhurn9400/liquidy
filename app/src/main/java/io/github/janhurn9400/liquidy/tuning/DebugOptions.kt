package io.github.janhurn9400.liquidy.tuning

/** Nur fürs Testen im Emulator — keine iOS-Werte. */
object DebugOptions {
    // Der Emulator hat nur ~15 Apps (= 1 Seite). Verteilt die App-Liste beim (Neu-)Aufbau des Layouts auf 1 + n Seiten, um Paging zu testen.
    // Änderung setzt das gespeicherte Layout zurück.
    val demoPages = tunable("DBG.demoPages", "Demo: zusätzliche Seiten (setzt Layout zurück)", "Debug", 1f, 0f, 5f, "Seiten")

    // 0 = grauer Referenz-Wallpaper (wie Nutzer-iPhone), 1 = farbiger Test-Wallpaper (macht Glas-Brechung sichtbar)
    val wallpaperStyle = tunable("DBG.wallpaper", "Wallpaper: 0 grau (Referenz) / 1 farbig (Glas-Test)", "Debug", 0f, 0f, 1f)

    fun touch() = Unit
}
