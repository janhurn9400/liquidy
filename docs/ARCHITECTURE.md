# Architekturplan (Entwurf Phase 0 — noch nicht umgesetzt)

## Setup
- Kotlin + Jetpack Compose, minSdk 33, targetSdk 36, compileSdk 36, ein Modul `:app` (später `:controlcenter` für den AccessibilityService).
- Schrift: **Inter** (OFL) als gebündelte TTF. Keine Apple-Assets.
- Emulator: neues AVD `iOSHome_API36` — System-Image `android-36;google_apis;arm64-v8a`, **1206 × 2622 px @ 480 dpi = 402 × 874 dp** (= Nutzer-iPhone, gemessen), `hw.gpu.mode=host`.
- Launcher-Integration nach Launcher3-Vorbild: `MAIN` + `HOME` + `DEFAULT`-Intent-Filter, `launchMode=singleTask`, `stateNotNeeded`, `onNewIntent` (Home-Taste → Ordner schließen / zu Seite 1), App-Liste über `LauncherApps` (+ Callback bei Install/Deinstall), Edge-to-Edge mit transparenten Systemleisten.

## Paketstruktur
```
app/src/main/java/…/
  motion/     Motion.kt          alle Springs/Dauern/Kurven, je mit Verweis auf RESEARCH-ID (M-…)
              IosSpring.kt       data class IosSpring(response, dampingFraction) → Compose-SpringSpec
  design/     DesignTokens.kt    alle Maße/Abstände/Radien/Blur/Farben (T-…)
              ContinuousShape.kt Apple-„continuous corner“-Shape (Bézier-Konstanten)
  tuning/     Tunables.kt        Registry: jeder Motion-/Token-Wert ist ein observierbarer State
              DebugPanel.kt      3-Finger-Long-Press → Slider-Panel, Reset, Export (Kotlin-Snippet + JSON)
  data/       AppRepository.kt   LauncherApps, Icons laden
              LayoutStore.kt     Seiten/Dock/Ordner als JSON in DataStore
              IconRenderer.kt    AdaptiveIcon → Squircle; Legacy-Icons auf Fläche skaliert; Icon-Cache
  glass/      Backdrop.kt        Wallpaper-Layer als GraphicsLayer zum Abtasten
              LiquidGlass.kt     AGSL: Blur + Refraktion + Spekular + dunkler Rand + Tönung
  gesture/    HomeGestures.kt    eine zentrale Gesten-Arena: horizontal = Paging, vertikal = Suche,
                                 Long-Press = Menü/Edit, 3-Finger = Debug; VelocityTracker
  ui/home/    HomeScreen, Pager (eigener, kein HorizontalPager), IconGrid, AppIcon, Dock, PageIndicator/SearchPill
  ui/edit/    (Phase 2)  ui/folder/ (Phase 3)  ui/library/ ui/search/ (Phase 4)  ui/widgets/ (Phase 5)
```

## Zentrale Entscheidungen
1. **Eigener Pager statt `HorizontalPager`.** Seitenposition ist ein `Animatable<Float>`; Drag schreibt direkt (`snapTo`), Loslassen → `animateTo(ziel, M-PAGE, initialVelocity = fingerVelocity)`. Neuer Touch während der Animation stoppt sie an Ort und Stelle und übernimmt — exakt das iOS-Verhalten. Gummiband per UIScrollView-Formel.
2. **Jede animierte Größe ist ein `Animatable`**, nie `animate*AsState` mit Tween. Retarget behält Geschwindigkeit (Compose-Default). Keine Dauer-basierten Animationen außer Wackeln.
3. **Motion/Tokens sind live-editierbar.** `Motion.kt`/`DesignTokens.kt` definieren die Defaults; zur Laufzeit liest die UI aus `Tunables` (Compose-State). Das Debug-Panel ändert sie live; „Export“ erzeugt die neuen Default-Zeilen, die ich dann in Motion.kt/DesignTokens.kt übernehme. So bleibt die Quelle der Wahrheit im Code.
4. **Eigener Wallpaper im Launcher** (siehe RESEARCH §5.1), damit Glas/Blur echt auf den Hintergrund reagieren.
5. **Liquid Glass:** eigener AGSL-Shader mit voller Parametrisierung über Tokens; [Kyant0/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass) als Referenz (ggf. als Abhängigkeit für den Start, wenn Lizenz passt und es Zeit spart).
6. **Layout in pt = dp**, alle Positionen aus Tokens berechnet (kein Constraint-Layout-Raten) → Abgleich mit iPhone-Screenshots per Overlay möglich.
7. **Verifikations-Tool:** kleines Skript `tools/compare.sh`, das einen Emulator-Screenshot und einen iPhone-Screenshot (gleiche 3x-Auflösung) übereinanderlegt/differenziert.

## Ablauf Phase 1 (nach deinem OK)
1. Gradle-Projekt, AVD anlegen (Download System-Image ~1,5 GB), Launcher-Manifest, Inter.
2. Motion.kt, DesignTokens.kt, Tunables + Debug-Panel-Grundgerüst (früh, wie gewünscht).
3. Wallpaper + Grid + Dock (Glas) + eigener Pager + Page-Indicator/Pill + Antipp-Feedback.
4. Screenshots ziehen, gegen Messwerte prüfen, an dich übergeben.
