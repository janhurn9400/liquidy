# Liquid Glass nachbauen — Recherche (2026-10-06)

## Was Apple selbst beschreibt (WWDC25 „Meet Liquid Glass“) [BELEGT]
- Material **bricht, formt und bündelt Licht** in Echtzeit (Lensing) statt nur zu streuen (Blur).
- **Größere Elemente = „dickeres“ Glas:** tiefere Schatten, stärkere Linsen-/Brechungseffekte, weichere Streuung.
- **Spekular-Lichter** folgen der Geometrie, wandern bei Interaktion um die Silhouette, teils mit Gerätebewegung.
- **Berührung:** Element „leuchtet von innen“, ab Fingerposition, Glow springt auf benachbarte Glas-Elemente über; gelartiges Nachgeben.
- **Adaptiv:** Schatten-Deckkraft, Tönung und Dynamikumfang passen sich dem Inhalt darunter an; kleine Elemente (Tab-/Navbars) wechseln hell ↔ dunkel, große (Menüs) nicht.
- **Erscheinen:** kein Ausblenden über Deckkraft, sondern „materialize“ durch Hochfahren der Lichtbrechung.
- Varianten **Regular** (adaptiv) und **Clear** (transparenter, braucht Abdunkelung).
- iOS 27: dunklerer Rand, hellere Spekularkanten, Regler „Klar ↔ Getönt“ (siehe RESEARCH §1).
Quelle: https://developer.apple.com/videos/play/wwdc2025/219/

## Wie Nachbauten technisch funktionieren [BELEGT aus mehreren Implementierungen]
1. **Form:** Squircle/Rounded-Rect als SDF (Signed Distance Field).
2. **Dicke/Höhenprofil:** flache Mitte, „Bevel“ zum Rand (smoothstep über SDF) → Oberflächennormale kippt am Rand nach außen.
3. **Brechung:** Hintergrund-UV wird entlang des SDF-Gradienten verschoben (Snell bzw. Näherung) — volle Stärke am Rand, 0 in der Mitte.
4. **Dispersion:** R/G/B mit unterschiedlichem Versatz (Blau am stärksten) → Farbsäume am Rand.
5. **Blur + Vibrancy:** Hintergrund geblurrt, Sättigung angehoben (Kyant: ×1,5).
6. **Spekular/Fresnel:** Normale · Lichtrichtung, Potenzfunktion; Fresnel (Schlick) → Rand heller.
7. **Schatten:** äußerer Drop-Shadow + innerer Schatten (→ iOS-27-„dunkler Rand“).
Quellen: https://charlesgrassi.dev/blog/apple-liquid-glass/ · https://1ar.io/updates/how-liquid-glass-works · https://zenn.dev/orectic/articles/liquid-glass-webgl-refraction?locale=en · https://github.com/Oliverrr2424/webgl-apple-liquid-glass

## Fertige Android/Compose-Projekte
| Projekt | Lizenz/Stand | Was es kann |
|---|---|---|
| **Kyant0/AndroidLiquidGlass → `io.github.kyant0:backdrop` 2.0.1** | Apache-2.0, ~4,1k Sterne, Compose Multiplatform | `layerBackdrop` nimmt beliebigen Inhalt (nicht nur Wallpaper) in eine Textur auf; `drawBackdrop` mit `vibrancy()`, `blur()`, `lens()` (AGSL, API 33+, SDF-Refraktion + Dispersion), Highlight-Rand, innerer Schatten, Drop-Shadow; Beispiel-Komponenten (Button, Toggle, Slider, BottomTabs). De-facto-Standard, viele Ports. https://github.com/Kyant0/AndroidLiquidGlass · Doku: https://kyant.gitbook.io/backdrop |
| shayann07/liquidglass | Compose + Desktop | Refraktion, Dispersion, Rim in einem Shader https://github.com/shayann07/liquidglass |
| styropyr0/PrismalAGSL | Compose | Backdrop-Sampling, Blur, Rand-Refraktion, Spekular, Degradation auf alten Versionen https://github.com/styropyr0/PrismalAGSL |
| QWEA0/Liquid-Glass-Android | View-System, API 24+ | SDF-Refraktion, Dispersion, Sensor-gesteuerte Spekular-Lichter https://github.com/QWEA0/Liquid-Glass-Android |
| jtt0001/liquid-glass-demo | Demo | lokale Kopie von Kyant + Kontrollzentrum/Bottom-Bar-Demos https://github.com/jtt0001/liquid-glass-demo |

## Unsere aktuelle Lücke
`glass/GlassSurface.kt` tastet nur den **Wallpaper** ab (Blur + Tönung + Randverlauf). Keine Lichtbrechung, keine Dispersion,
und Glas über Icons (Ordner-Panel, Kontextmenü, Pill über Inhalt) sieht die Icons nicht → nicht wie iOS.

## Empfehlung
1. **Kyant `backdrop` 2.0.1 einbinden** (Apache-2.0) und hinter unserer `GlassSurface`-API kapseln, damit alle Parameter weiter in
   DesignTokens/Debug-Panel liegen. Gesamte Homescreen-Ebene per `layerBackdrop` aufnehmen → Dock, Pill, Badges, Ordner, Menü
   brechen echten Inhalt.
2. Parameter je Element aus unseren Referenzen kalibrieren (iOS 27: dunkler Innenrand = inner shadow, helle Kanten = highlight;
   größere Elemente stärkere Linse — Apple-Aussage).
3. Interaktion: „von innen leuchten“ + gelartiges Nachgeben bei Berührung (Phase-2-Politur).
Risiko: Kompatibilität Kotlin/Compose-Version (backdrop 2.x ist evtl. mit neuerem Kotlin gebaut) → beim Einbinden prüfen,
ggf. Kotlin anheben. Fallback: eigenes AGSL nach obigem Rezept.

## Umsetzung (2026-10-06)
- `io.github.kyant0:backdrop:2.0.1` + `io.github.kyant0:shapes:1.2.1` (G2-Ecken, von der Linse unterstützt). Kotlin dafür auf 2.4.10 angehoben
  (backdrop ist mit Kotlin 2.4 gebaut; AGP-9-built-in-Kotlin per `buildscript { classpath(kotlin-gradle-plugin) }` überschrieben, laut AGP-9-Release-Notes).
- `glass/LiquidGlass.kt`: colorControls(Sättigung) → blur → lens(Höhe ≤ Eckenradius, Tiefeneffekt; ohne Dispersion, s. Fallstricke 4) + Highlight (Licht 90° = oben/unten hell)
  + innerer/äußerer Schatten + dunkler Seitenrand (GlassRim, nur dunkler Anteil). Alle Werte `T-GLASS.*` in DesignTokens.
- Glas-Quelle: Wallpaper + Seiten-Icons per `Modifier.layerBackdrop` (HomeScreen), bereitgestellt über `LocalGlassBackdrop`.
- Echtes Glas: Dock, Pill, Edit-Buttons, Ordner-Panel, Lösch-Dialog. **Noch altes Glas** (liegt selbst in der aufgenommenen Ebene → würde sich selbst aufnehmen):
  Minus-Badges, Ordner-Icons, Ordner-Hinweis, Mediathek-Kacheln → später über `exportedBackdrop`/eigene Ebene lösen.
- Debug: `DBG.wallpaper = 1` zeigt einen farbigen Test-Wallpaper, auf dem Brechung/Dispersion sichtbar sind (Referenz-Wallpaper ist einfarbig grau).

## Fallstricke
1. **Form nicht animieren:** Kyants Ebene übernimmt das Outline nur bei Größenänderung. Ein pro Frame veränderter Eckradius (Ordner-Öffnen)
   ergab einen veralteten Beschnitt (große Startecken) ≠ Lichtkante → „Form in der Form“. Lösung: konstanter Radius, Animation nur über Skalierung.
2. Lens braucht `RoundedRectangularShape` (Kyant Shapes) oder `AbsoluteRoundedCornerShape`; unsere eigene ContinuousRoundedShape geht dort nicht.
3. Zu große Brechungszone (Dicke 2 → 24 pt) wirkt wie ein Doppelrand; iOS-Ordner hat eine schmale Zone → `T-GLASS.thickLarge = 1`.
4. **Keine Farbsäume (Dispersion)** (2026-10-14): Apples Liquid Glass hat keine Regenbogen-Ecken (Nutzer am echten iPhone geprüft).
   Kyants `lens(chromaticAberration = true)` gab kräftige Säume → überall `chromaticAberration = false`, Token `T-GLASS.dispersion` entfernt.

## Weitere Quellen
- QWEA0/Liquid-Glass-Android (MIT, View-System): Ideen für später — Brechungsabfall `∝ (1 + x/k)^-p`, Lagesensor-Lichtreflex,
  Helligkeitsmessung dahinter für adaptive Tönung. https://github.com/QWEA0/Liquid-Glass-Android
- Void Launcher: Closed Source, Lizenz verbietet Reverse Engineering → nicht verwendbar. https://github.com/Navoto01/VoidLauncher
