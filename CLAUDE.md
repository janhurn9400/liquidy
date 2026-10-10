# CLAUDE.md — iOS-27-Homescreen-Launcher für Android

Ziel: Android-Launcher (Kotlin + Compose, minSdk 33), der sich 1:1 wie der iOS-27-Homescreen anfühlt (Layout, Physik, Timing). Antworten an den Nutzer auf Deutsch.

## Dokumente
- `docs/RESEARCH.md` — alle Maße/Springs mit Quelle und Status [BELEGT]/[ABGELEITET]/[GESCHÄTZT], plus „Bitte am iPhone prüfen“
- `docs/ARCHITECTURE.md` — Architektur & Entscheidungen

## Regeln
1. Alle Animationswerte nur in `motion/Motion.kt` (mit RESEARCH-ID `M-…`), alle Design-Werte nur in `design/DesignTokens.kt` (`T-…`). Keine Magic Numbers im UI-Code.
2. Alles Animierte ist ein unterbrechbares `Animatable`, Gesten-Geschwindigkeit fließt als `initialVelocity` ein.
3. Keine Apple-Assets (Schrift Inter, eigene/freie Wallpaper, App-Icons nur per Squircle-Maske).
4. Nach jeder Änderung: bauen, im Emulator installieren, starten, `adb exec-out screencap -p > shot.png` prüfen, dann kurz berichten + worauf am iPhone achten. Bei Feedback wenige Werte gezielt ändern und nennen.
5. Emulator = 60 Hz + Maus → beim Beurteilen von Ruckeln/Flings berücksichtigen.

## Bauen & Testen
- Emulator: AVD `iOSHome_API36` (API 36, 1206×2622 @480 dpi = 402×874 dp wie das Nutzer-iPhone, GPU host).
  Start: `~/Library/Android/sdk/emulator/emulator -avd iOSHome_API36 -gpu host -no-boot-anim`
- `./gradlew :app:assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk`
  Als Home setzen: `adb shell cmd package set-home-activity io.github.janhurn9400.liquidy/.MainActivity`
- Debug-Panel: 3-Finger-Long-Press (Gerät), Taste **D** (Emulator), oder
  `adb shell am start -n io.github.janhurn9400.liquidy/.MainActivity --ez debug true`.
  „Export“ → Zwischenablage + Logcat-Tag `Tuning` (`adb logcat -s Tuning`).
- Vergleich: Screenshots nach `shots/` (gitignored), Messskripte in `tools/measure/`, Referenzen in `docs/reference/iphone/`.

## Stand (2026-10-06)
- Phase 0: abgeschlossen. Phase 1–4 funktional gebaut, Feinabgleich läuft (Nutzer tunt NICHT selbst — Werte aus seinen Videos ableiten, Emulator-Aufnahme frameweise danebenlegen).
- Phase 1: Raster/Labels/Dock/Pill ±1 px; Pager M-PAGE verifiziert (0,362 s / 0,904).
- Phase 2: Bearbeitungsmodus (Long-Press, Wackeln gemessen, Badges, Edit-Buttons, Ziehen + Ausweichen, Dock ↔ Raster, Rand-Seitenwechsel inkl. neuer Seite, Lösch-Dialog, persistentes Layout `LayoutStore`). Offen: Kontextmenü beim Long-Press auf Icon (iOS 27), Blasenmenü im Bearbeitungsmodus.
- Phase 3: Ordner (Drüberziehen + Halten, Name aus App-Kategorie, Mini-Raster gemessen, Öffnen/Schließen M-FOLDER gemessen, Rausziehen per Long-Press). Offen: Umbenennen, Ordner-Paging > 9, Umsortieren im Ordner.
- Phase 4: Suche (Rev. 3 nach frameweiser Analyse), App-Mediathek 1:1 nach recording2 (Kacheln, Glas-Suchfeld mit Scroll-Kante, A–Z-Liste per Herunterziehen, panellose Kategorie mit Gruppen-Zoom).
- Emulator-Performance: GPU-Median 18 ms/Frame (~40 fps) unabhängig von Glas/Blur (A/B gemessen) → Ruckeln im Emulator ist kein App-Problem. `adb shell screenrecord` liefert oft nur Standbilder; für Zwischenzustände Federn per Debug-Prefs verlangsamen + screencap.
- Tastatur: Manifest `adjustResize` (nötig für animierte IME-Insets); IME-Insets nur in Layout-/Draw-Lambdas lesen (Composition-Lesen = 1 Frame Verzug).
- Suche Rev. 6: Pill → Suchfeld-Morph (SearchPill.kt, liegt außerhalb der verwischten Ebene), Tippen auf Pill öffnet Suche.
- Liquid Glass: Kyant backdrop (siehe docs/GLASS.md inkl. Fallstricke). Kotlin 2.4.10 via buildscript-Classpath.
- Suche Rev. 5: Wallpaper statisch (nur Blur), Inhalt verschoben; nach dem Öffnen federt der Homescreen auf 0 pt zurück (Skala 0,92).
- Kontextmenü (M-CTXMENU/T-CTX) mit nativen Android-Shortcuts gebaut; Weiterziehen → Bearbeitungsmodus.
- Wallpaper-Editor (Pair/Colour/Gradient/Photo + Blur, eigener Fotos-Picker über MediaStore, Zuschnitt) über Edit-Menü; Wallpaper in SharedPreferences („wallpaper“). Labels passen sich der Wallpaper-Helligkeit an.
- Customise-Blatt: Icon-Stile Default/Dark/Clear/Tinted (IconAppearance, Ebenen aus IconRenderer.layers).
- Widgets (Grundlage): WidgetSpec im Layout (Schlüssel „widget:…“), GridPlacement (iOS-Fluss, Widgets nur Spalte 0/2), eigene iOS-Widgets (ui/widgets/IosWidgets.kt: Uhr, Kalender, Batterien), Android-Widgets über AppWidgetHost (AndroidWidgets.kt, Bind-/Konfig-Flow über onActivityResult), Picker + Hinzufügen-Blatt (WidgetPicker.kt, recording8).
- Heute-Ansicht (Pager-Seite −1, TodayPage.kt), Resize per Griff + Größenleiste im Widget-Menü, Widgets ziehen/antippen. Ordner-Icons + Widget-Glas nutzen WallpaperBackdrop (nur Wallpaper als Quelle).
- Wallpaper: es gibt NUR das Android-Wallpaper (Nutzerwunsch, nicht ändern!). Fenster durchsichtig (windowShowWallpaper + windowIsTranslucent), Unschärfe macht Android per Window.setBackgroundBlurRadius. Der Picker setzt seine Wahl als Android-Wallpaper. Pixel (nur intern fürs Glas) per MANAGE_EXTERNAL_STORAGE gelesen; ohne Zugriff Rückfall: selbst zeichnen.
- GestureNavContract (Wisch nach Hause → Fenster fliegt ins Icon) mit Icon-Surface wie Launcher3 FloatingSurfaceView; Teststart: `adb shell am start -a android.intent.action.MAIN -c android.intent.category.HOME` (am start -n leitet auf den Home-Task um).
- App öffnen/schließen Scharf/Unscharf: Code da (launchBlur/returnBlur in HomeModel), in backgroundBlurPx deaktiviert — Nutzer will es später mit iPhone-Video genauer prüfen.
- Root-Weg (optional): Magisk-Modul tools/magisk-module (Build: tools/build-magisk-module.sh → build/ioshome-magisk.zip) blendet die installierte App als priv-app ein + Berechtigung CONTROL_REMOTE_APP_TRANSITION_ANIMATIONS + Hidden-API-Freigabe. Dann öffnet AppLaunchTransition (RemoteTransition, Stubs in :hiddenapi, compileOnly) Apps mit eigener Feder aus dem Icon. Emulator: rootAVD-Ramdisk gepatcht (Magisk 26.4). Play Store: App darf das Modul nicht selbst laden/installieren — nur Link + „Magisk öffnen“.
- Offen als Nächstes: Hinweis/Knopf „Android-Hintergrund verwenden“ (MANAGE_EXTERNAL_STORAGE anfragen), iOS-Video für App-Schließen (M-HOME-RETURN kalibrieren), Wetter-Widget (braucht Internet + Standort — Nutzer fragen), Heute-Ansicht bearbeiten (Widgets dort hinzufügen/ziehen), App-Öffnen-Animation (Phase 6), Icon-Stil auch für Ordner-Icons/Mediathek, Icon-Stil in Mediathek-Kacheln/Liste prüfen.
- Architektur: eine zentrale Gesten-Arena in `ui/home/HomeScreen.kt`; Geometrie nur in `HomeGeometry`/`FolderGeometry`/`LibraryGeometry`; Layout-Zustand in `HomeModel`.
- Lektion: Bewegungen IMMER frameweise gegen die Referenz analysieren (Montage + Tracking), nicht nur Einzelwerte — sonst wirkt es „schlampig“ (Suche Rev. 1/2).
