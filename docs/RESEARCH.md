# RESEARCH.md — iOS 27 Homescreen → Android (Compose)

Stand: 2026-10-06 · Phase 0 (Rev. 2: mit Messungen aus Nutzer-Screenshots + Bildschirmaufnahme)

**Legende**
- **[GEMESSEN]** — pixelgenau aus den nativen iPhone-Referenzen in `docs/reference/iphone/` (iOS 27, 1206 × 2622 px @3x = 402 × 874 pt); Animationen per Frame-Tracking aus `recording1.mp4` (60 fps) + Feder-Fit
- **[BELEGT]** — direkt aus Apple-Doku/WWDC oder klar zitierbarer Quelle
- **[ABGELEITET]** — aus belegten/gemessenen Werten berechnet (Rechenweg steht dabei)
- **[GESCHÄTZT]** — begründete Annahme → „Bitte am iPhone prüfen“

Jede Zahl, die in `Motion.kt`/`DesignTokens.kt` landet, hat eine ID (`M-…`, `T-…`), auf die der Code verweist. Alle Längen in **pt = dp**.

### Referenzmaterial (vom Nutzer, 2026-10-05)
| Datei | Inhalt |
|---|---|
| IMG_3123 | Homescreen Seite 1 (2 kleine Widgets, 14 Icons, Pill „Suche“, Dock) — heller grauer Wallpaper |
| IMG_3124 | Bearbeitungsmodus (Minus-Badges, Buttons oben, Page-Dots in der Pill) |
| IMG_3125 | Bearbeitungsmodus + Long-Press-Menü (Add Widget / Customise / Edit Wallpaper / Edit Pages) |
| IMG_3126 | Widget-Galerie (Sheet) |
| IMG_3127 | „Customise“-Panel (Default / Dark / Clear / Tinted) |
| IMG_3128 | Wallpaper-Editor |
| IMG_3129 | Seiten bearbeiten |
| IMG_3130 | geöffneter Ordner „Utilities“ |
| IMG_3131 | Suche (Swipe down) mit Tastatur |
| IMG_3132 | Heute-Ansicht (links von Seite 1) |
| recording1.mp4 | 26,5 s: Seiten wischen, Heute-Ansicht, Suche 2×, Ordner zu/auf/zu, Bearbeitungsmodus + Menü, App öffnen (Fotos), Kontrollzentrum |

Messskripte: siehe `tools/measure/` (werden mit Phase 1 eingecheckt).

---

## 1. Was ist neu in iOS 27 (relevant für den Homescreen)

| Thema | Befund | Status | Quelle |
|---|---|---|---|
| Release | vorgestellt 8. Juni 2026, Release 14. Sept. 2026 | [BELEGT] | [Macworld](https://www.macworld.com/article/2986799/ios-27-new-iphone-features-release-date-beta-compatiblity-apple-intelligence-siri.html) |
| Liquid Glass | diffundiert Hintergrund stärker, **dunklerer Rand**, **hellere Spekularkanten** | [BELEGT] + [GEMESSEN]: Pill & Dock haben oben/unten einen hellen Lichtsaum, links/rechts einen dunklen Rand | [MacRumors](https://www.macrumors.com/2026/06/10/how-liquid-glass-is-changing-in-ios-27/) |
| Glas-Regler | Einstellungen › Darstellung › Liquid Glass: „Klarer ↔ Getönter“ | [BELEGT] | [Six Colors](https://sixcolors.com/post/2026/09/ios-27-review-little-things-mean-a-lot/) |
| Glas-Regler Messung | Nutzer-iPhone 10-14, Dock/Suchfeld bei klar/Mitte/getönt: Unschärfe σ ≈ 0,4 / 1,3 / ≥ 4 pt (getönt völlig glatt, Brechung unsichtbar), Dock-Farbe fast gleich; getönt ohne Brechung (Mediathek-Feld: Rand nur weichgezeichnet) und neutral-grauer Schleier (Dock 204→196, Suchfeld 44→54, Blau 252→224 → Grau ≈ 140, ≈ 15 %). Umgesetzt: glass/GlassClarity, T-GLASS.frostMid/frostTinted/tintedVeil/tintedGrey | [GEMESSEN] | Screenshots Nutzer |
| App-Icons | mehrere Glas-Layer, schärfer; **Tilt-Glitzer entfernt**; heller Rand-Lichtsaum oben | [BELEGT] + [GEMESSEN] Lichtsaum | [Design Compass](https://designcompass.org/en/2026/09/16/ios-27-liquid-glass-icons/) |
| Icon-Stile | Default / Dark / Clear / Tinted (Panel „Customise“, IMG_3127) | [BELEGT] + Screenshot | Design Compass |
| Widgets | neu: Vollbild-Widget (XL) | [BELEGT] | [TechCrunch](https://techcrunch.com/2026/06/09/ios-27-features-we-didnt-see-on-stage/) |
| Suche | Swipe nach unten → Suche mit „Siri Suggestions“ oben, Suchfeld über der Tastatur unten (IMG_3131) | [BELEGT] + Screenshot | [Macworld](https://www.macworld.com/article/3236198/ios-27-is-driving-my-fingers-insane.html) |
| Bearbeiten-Menü | Add Widget / Customise / Edit Wallpaper / Edit Pages — **kein Undo/Redo** in der ausgelieferten Version | [GEMESSEN] IMG_3125 | — |

Grundgerüst (4×6, Dock 4, Seiten, Ordner, App-Mediathek) unverändert. Tilt-Parallax bauen wir **nicht**.

---

## 2. Maße & Formen  — alle [GEMESSEN] aus IMG_3123/3124/3130, sofern nicht anders markiert

### 2.1 Gerät & Einheiten
- Referenzgerät: **402 × 874 pt @3x** (iPhone 17 Pro bzw. 16 Pro — gleiche Maße). Display-Eckenradius 62 pt ([ScreenCorners](https://github.com/kylebshr/ScreenCorners)) [BELEGT].
- 1 pt = 1 dp (Fehler ~2 %) [ABGELEITET]. Emulator-AVD: **1206 × 2622 px @ 480 dpi = 402 × 874 dp** → Screenshots pixelgleich vergleichbar.

### 2.2 Homescreen-Raster

| ID | Wert | Pixel (@3x) |
|---|---|---|
| T-ICON | **64 pt** Icon (nicht 60!) | 192 px |
| T-ICON-R | Eckenradius **16,33 pt** (25,5 % der Kante), *continuous corner* — Fit-Fehler 0,37 px; Kreisbogen passt schlechter (0,64 px). → iOS 27 ist runder als die alten 22,5 % | 49 px |
| T-GRID-SIDE | Seitenrand bis Icon-Kante **30 pt** (links = rechts) | 90 px |
| T-GRID-COLPITCH | Spaltenabstand Mitte-Mitte **92,67 pt** (Lücke zwischen Icons 28,67 pt) | 278 px |
| T-GRID-TOP | Oberkante Reihe 1 **90 pt** | 270 px |
| T-GRID-ROWPITCH | Reihenabstand **100,33 pt** | 301 px |
| T-GRID-COLS/ROWS | 4 × 6 | — |
| T-LABEL-CAP | Label-Versalhöhe ≈ **9 pt**, Versal-Oberkante 8 pt unter Icon, Grundlinie bei Icon-Oberkante + **80,33 pt** | 27 px / 24 px / 241 px |
| T-LABEL-W | Referenzbreite „FaceTime“ = **53 pt** → Inter-Schriftgröße so wählen, dass Breite & Versalhöhe passen (≈ 12–12,5 sp) | 159 px |
| T-LABEL-COLOR | auf hellem Wallpaper **dunkel, warm getönt** ≈ `#493A36` (iOS färbt Labels passend zum Wallpaper) | — |
| T-WIDGET-S | kleines Widget **164,33 × 164,33 pt**, r = **27,75 pt** continuous (Fit 0,37 px); vertikal exakt 2 Reihen (Icon-Oberkante R1 bis Icon-Unterkante R2), horizontal **4 pt** breiter als die Icons auf jeder Seite; x = 26 / 211,33 pt | 493 px |
| T-WIDGET-LABEL | Widget-Label Versal-Oberkante 8,67 pt unter Widget | 26 px |
| T-WIDGET-GRID | S 2×2, M 4×2, L 4×4, XL 4×6 (Vollbild, iOS 27) | [BELEGT] Layout-Gist / TechCrunch |

**Layout-Formel:** `iconX(col) = 30 + col·92.67`, `iconY(row) = 90 + row·100.33` (pt). Reihe 6 endet bei 655,67 pt + Label.

### 2.3 Such-Pill & Page-Dots

| ID | Wert |
|---|---|
| T-PILL | Kapsel **78,67 × 30 pt**, horizontal zentriert, Oberkante **703,67 pt** (Abstand Pill → Dock 20 pt) |
| T-PILL-RIM | oben/unten heller Lichtsaum (1–2 px, fast weiß), links/rechts dunkler 1-px-Rand |
| T-PILL-CONTENT | Lupe (≈ 9,3 pt breit) + „Search“, Farbe ≈ `#6B605D` |
| T-DOTS | in derselben Pill: Punkt Ø **6,33 pt**, Abstand Mitte-Mitte **14 pt**; aktiv Weiß 100 %, inaktiv Weiß ≈ 45 % |

### 2.4 Dock

| ID | Wert |
|---|---|
| T-DOCK-INSET | **16,67 pt** links/rechts, **17 pt** unten |
| T-DOCK | Glas-Platte **368,67 × 103,33 pt**, Oberkante 753,67 pt |
| T-DOCK-R | Eckenradius **38,33 pt** (Fit 0,5 px). *Nicht* konzentrisch zum Display (das wären 45,3 pt) — Schätzung aus Phase 0 damit widerlegt |
| T-DOCK-ICONS | 4 × 64 pt, vertikal zentriert (19,67 pt oben/unten), 20 pt Innenrand links/rechts, Abstand Mitte-Mitte **88,22 pt** (x = 36,67 / 125 / 213,33 / 301,33 pt) |
| T-DOCK-RIM | wie Pill: oben/unten heller Saum, seitlich dunkler Rand; Füllung minimal dunkler als Wallpaper (≈ −8 % Luminanz) |

### 2.5 Ordner (geöffnet)

| ID | Wert |
|---|---|
| T-FOLDER-PANEL | **306,33 × 352,67 pt**, r = **38,5 pt** continuous (Fit 0,7 px), **exakt bildschirmzentriert** |
| T-FOLDER-GRID | 3 Spalten, Icons 64 pt; Spaltenabstand **92,67 pt** (= Homescreen), Reihenabstand **104,33 pt**; Innenrand links 29 pt, oben 28,67 pt |
| T-FOLDER-TITLE | „Utilities“, fett, ≈ 34 pt (Large-Title-Größe), weiß, linksbündig über dem Panel (≈ 73 pt von links), [GESCHÄTZT] Größe ±2 pt |
| T-FOLDER-BG | Homescreen dahinter stark geblurrt (Icons nur noch als Farbflecken), leicht abgedunkelt |

### 2.6 Bearbeitungsmodus

| ID | Wert |
|---|---|
| T-BADGE | Minus-Badge Ø **26 pt**, hellgraue Glas-Kreisfläche, schwarzer Strich **12 × 2 pt**; Mittelpunkt bei Icon-Ecke oben links + (2,3 / 3,2) pt |
| T-EDIT-BTN | zwei Glas-Kapseln oben **66,67 × 27,33 pt**, y = 19 pt, 35,67 pt vom Rand; links Pinsel (Customise), rechts Häkchen (Fertig). Statusleiste wird ausgeblendet |
| T-EDIT-PILL | Pill zeigt Page-Dots statt „Search“ |
| T-WIDGET-RESIZE | Widgets bekommen unten rechts einen Resize-Griff (Kreisbogen) |

### 2.7 Squircle-Form  [BELEGT]
„Continuous corner“-Rechteck aus Bézier-Kurven; Ecke beginnt 1,52866 × r von der Kante ([Liam Rosenfeld](https://liamrosenfeld.com/posts/apple_icon_quest/)). Unsere Messungen (Icons, Widgets, Ordner) passen mit dieser Form deutlich besser als mit Kreisbögen → `ContinuousRoundedShape(r)` für alles.

### 2.8 Liquid-Glass-Material
Bausteine (AGSL): Blur + Sättigung, Rand-Refraktion, heller Saum oben/unten, dunkler Seitenrand, Tönung („Klar ↔ Getönt“). Saum/Rand-Verhalten [GEMESSEN] qualitativ; Blur-Radius & Refraktionsstärke [GESCHÄTZT] → Debug-Panel. Referenz: [Kyant0/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass).

---

## 3. Springs: Apple → Compose

### 3.1 Apple-Parameter  [BELEGT]
- `Spring(duration: 0.5, bounce: 0.0)` = Default; duration = response.
- `.smooth` bounce 0 · `.snappy` 0.15 · `.bouncy` 0.3 (je 0.5 s); `.spring(response: 0.5, dampingFraction: 0.825)`; `.interactiveSpring(0.15, 0.86, blend 0.25)`; `CASpringAnimation` mass 1 / stiffness 100 / damping 10.
- Retarget übernimmt die aktuelle Geschwindigkeit.
Quellen: [Apple – Spring](https://developer.apple.com/documentation/swiftui/spring), [snappy](https://developer.apple.com/documentation/swiftui/animation/snappy(duration:extrabounce:)), [bouncy](https://developer.apple.com/documentation/swiftui/animation/bouncy(duration:extrabounce:)), [spring(response:…)](https://developer.apple.com/documentation/swiftui/animation/spring(response:dampingfraction:blendduration:)), [interactiveSpring](https://developer.apple.com/documentation/swiftui/animation/interactivespring(response:dampingfraction:blendduration:)), [WWDC23](https://developer.apple.com/videos/play/wwdc2023/10158/), [WWDC18](https://developer.apple.com/videos/play/wwdc2018/803/)

### 3.2 Umrechnung  [ABGELEITET]
```
ω₀ = 2π / response
stiffness    = ω₀² = (2π / response)²          (m = 1, wie Compose)
dampingRatio = dampingFraction ;  aus bounce: ζ = 1 − bounce (≥0) bzw. 1/(1+bounce) (<0)
Compose: spring(dampingRatio = ζ, stiffness = (2π/response)²)
```
| response (s) | 0.15 | 0.2 | 0.25 | 0.3 | 0.34 | 0.36 | 0.4 | 0.45 | 0.5 |
|---|---|---|---|---|---|---|---|---|---|
| stiffness | 1754.6 | 987.0 | 631.7 | 438.6 | 341.5 | 304.6 | 246.7 | 195.0 | 157.9 |

Compose `Animatable.animateTo()` startet mit `initialVelocity = velocity` → Retarget ohne Sprung. Gesten-Geschwindigkeit (VelocityTracker, px/s) geht 1:1 als `initialVelocity` rein.

**Fit-Methode für [GEMESSEN]-Springs:** Position pro Frame per Template-Matching/Kreuzkorrelation, dann Least-Squares-Fit der gedämpften Oszillator-Lösung (freie Parameter ω₀, ζ, v₀) ab dem ersten Frame nach Loslassen. RMS-Fehler steht jeweils dabei.

### 3.3 Springs & Timings pro Interaktion

| ID | Interaktion | Wert | Status |
|---|---|---|---|
| **M-PAGE** | Seite einrasten nach Swipe | **response 0,36 s, ζ 0,90** (4 Swipes: 0,32–0,38 s / 0,89–0,92; RMS 0,4–3 pt); Flick-Geschwindigkeit beim Loslassen ≈ 2000 pt/s | [GEMESSEN] |
| M-PAGE-DRAG | Finger-Phase | Inhalt folgt 1:1 | [GEMESSEN] |
| M-RUBBER | Gummiband am Rand | `(x·d·0.55)/(d + 0.55·x)` | [BELEGT] UIScrollView |
| M-PAGE-THRESH | Seitenwechsel-Schwelle | > ~300 pt/s oder > 50 % Versatz | [GESCHÄTZT] |
| **M-PILL** | Pill „Search“ ↔ Dots | bei Swipe-Beginn Überblendung zu Dots in ≈ 0,15 s; nach dem Einrasten bleiben Dots ≈ **0,5 s** stehen, dann ≈ 0,15 s zurück zu „Search“ | [GEMESSEN] |
| **M-PRESS** | Icon antippen | Touch-Down: **sofort** (≤ 1 Frame) auf **57 % Helligkeit** (≙ Schwarz-Overlay α 0,43), **keine Skalierung**; beim Loslassen sofort zurück (+ App-Start) | [GEMESSEN] |
| M-PRESS-CANCEL | Finger wegziehen ohne Start | weiches Zurückblenden? | [GESCHÄTZT] → prüfen |
| **M-FOLDER** | Ordner öffnen **und** schließen | **response 0,34 s, ζ 1,0** (kritisch, kein Nachfedern; Öffnen/Schließen symmetrisch, RMS < 1 pt). Panel skaliert von Icon-Rechteck (Skala 64/306,33 = 0,209, Mittelpunkt = Ordner-Icon) zur Bildschirmmitte | [GEMESSEN] |
| **M-JIGGLE** | Wackeln | **Rotation ±1,70°, Periode 0,256 s**; Versatz **x ±0,39 pt @ 0,268 s**, **y ±0,39 pt @ 0,284 s** (drei leicht verschiedene Perioden → organisch); alles Sinus; **Phase pro Icon zufällig**; startet sofort mit voller Amplitude (kein Einblenden). Gilt für Grid, Dock und Widgets | [GEMESSEN] (4 Icons, RMS 0,05° / 0,01 pt) |
| M-EDIT-ENTER | Badges/Buttons/Dots beim Eintritt | erscheinen innerhalb ~0,1 s; Feinkurve noch nicht vermessen | [GESCHÄTZT] |
| M-SEARCH-PULL | Swipe nach unten (Rev. 3, beide Suchvorgänge der Aufnahme frameweise) | Blur wächst mit dem Finger und ist nach ≈ ¼ des Wegs voll (Icons bleiben als Farbformen erkennbar → Radius ≈ 12 pt); Homescreen rutscht mit (≈ 66 pt beim Loslassen, offen ≈ 90 pt), schrumpft (0,94 → 0,89) und dunkelt ab (−37 % → −58 %) | [GEMESSEN] |
| M-SEARCH-BLOCK | „Siri Suggestions“ + Karte | blenden ab ≈ ⅓ des Wegs ein, kommen von oben (≈ −40 pt) und folgen dem Finger **über** die Ruheposition hinaus (+80…88 pt); nach dem Loslassen Rückfedern **response 0,46 s, ζ 0,81** (RMS 0,3 pt) | [GEMESSEN] |
| M-SEARCH-KB | Tastatur + Suchfeld | Tastatur startet **schon während des Ziehens** (≈ 75 % des Wegs), Feder 0,44 s / 0,85 = gleiche Systemfeder wie der Block; Suchfeld reitet auf der Tastatur. Auf Android steuert das System die IME-Animation → Feld hängt an `WindowInsets.ime` | [GEMESSEN] |
| M-SCROLL | Mediathek-Scrollen | UIScrollView 0,998/ms → Compose `exponentialDecay(frictionMultiplier = 0,4767)`; Zurückfedern [GESCHÄTZT] | [BELEGT]/[ABGELEITET] |
| T-LIB | App-Mediathek-Layout (recording2, Ruhelage 9,9 s) | Feld 26/90, 350×47,33, Schrift 17; Kacheln 165,33 □, Seite 25,67, Abstand 20, Reihen 200,67, oben 168,67; Innenabstand 12,67, große Icons 64 (Abstand 12), Mini 25,67 (Abstand 6); Label-Grundlinie +16,67 unter Kachel | [GEMESSEN] |
| T-LIB-LIST | A–Z-Liste (12,4 s) | Feld rechts 302,33, „Cancel“ x 323 (17 pt); Kopf-Grundlinie 182, Zeilen 72 pt ab 189,67, Icon 48 bei x 26,33, Name x 91 (17 pt weiß), Haarlinie bis 371; Index x 394,33, 183–550, 27 Zeichen | [GEMESSEN] |
| T-LIB-CAT | Kategorie aufgeklappt (18,2 s) | kein Panel; Titel fett 34,5 bei x 30, Grundlinie 325,67; Raster = Homescreen-Raster (x 30, 92,67/100,33), oben 371 | [GEMESSEN] |
| M-LIB-LIST | Herunterziehen → Liste (10,1–11,9 / 12,97–13,55 s) | sofort beim Ziehen: Kacheln unscharf in 0,12 s, Liste ab 0,3 s (0,45 s), Abdunkeln 0,75 s (Helligkeit 171 → 113); Liste folgt Finger 1:1, Feld ⅓; Loslassen: Feder 0,5/1,0, Tastatur hoch; „Cancel“: Liste 0,17 s weg, Kacheln ab 0,19 s scharf (0,38 s) | [GEMESSEN] |
| M-LIB-CAT | Kategorie auf/zu (17,50–18,10 / 18,60–18,81 s) | Titel + Raster zoomen als Gruppe aus dem Mini-Cluster: Feder 0,54/0,9 (rms 0,005); die 4 Cluster-Icons fliegen einzeln aus ihren Mini-Plätzen; Mediathek schrumpft um (Mitte, 0,48 H) auf 0,88 und ist bei Fortschritt ≈ 0,55 weg; Zuklappen ≈ 0,32/1,0 | [GEMESSEN] |
| M-SEARCH-MORPH | „Search“-Pill → Suchfeld (recording5) | Pill bleibt beim Ziehen **scharf** (nicht in der verwischten Ebene), wandert mit ≈ ¼ von h mit (23 pt bei h = 94). Bei h ≈ **94 pt** (noch während des Ziehens) zieht sie sich in ≈ 0,3 s symmetrisch auf **volle Feldbreite (≈ 386 pt) × 47 pt** auf (frühere 338 pt = Fehlmessung, Zwischenstand); Feld = klares Glas, Lupe + Platzhalter links, Mikrofon rechts; bleibt auf ihrer Höhe, bis die Tastatur sie erreicht, dann fährt sie mit und dockt in voller Breite an. Zurück unter ≈ 62 pt. Tastatur lugt bei iOS schon beim Morph unten heraus (Android: nicht steuerbar). Feld klebt direkt über der Tastatur: `windowSoftInputMode=adjustResize` (+ Edge-to-Edge) liefert die IME-Insets Frame für Frame (mit adjustNothing kamen sie als Sprung); Insets werden in der Layout-Phase gelesen → kein Frame Verzug. Gemessen: Abstand konstant 9–10 pt während der ganzen Tastaturanimation | [GEMESSEN] |
| M-FOLDER-HANDOFF | Ordner: Übergang Icon ↔ Panel | Emulator-Aufnahme zeigte ein hängendes Rest-Panel (0,25 s) + hart verschwindendes Icon → Überblendung in der kleinsten Phase (bis Fortschritt 0,12) + feine Endschwelle | [ABGELEITET] |
| M-CTXMENU | Long-Press auf Icon → Kontextmenü (recording4) | Touch-Down: sofort 57 % dunkel; ab +0,20 s wächst das Icon langsam 1,0 → 1,23 (bis +0,73 s), ab +0,47 s blendet das Dunkel aus (bis +0,67 s, Icon dann ~6 % heller); bei **+0,82 s** öffnet das Menü: Icon in ~0,08 s auf 1,15 und 10 pt nach oben, Hintergrund in ~0,15 s auf −50 %; Menü wächst aus dem Icon. Schließen ~0,2 s. Einträge (Telefon): Remove App (rot), Require Face ID, Edit Home Screen, App-Aktionen | [GEMESSEN] |
| T-CTX | Kontextmenü-Layout (recording4 2,3 s) | Breite 250,67, links bündig mit angehobenem Icon (rechts bündig in der rechten Hälfte), 9 pt Abstand; Zeile 42 pt (+19,33 je weitere Textzeile), Innenabstand 10; Symbol Mitte x 40,67 (16,33 pt), Text x 64,67, 17 pt; Rot #FC7075; Hintergrund −52 %, nicht verwischt; Menü-Glas stark verwischt. Einträge auf Android: App entfernen, Home-Bildschirm bearbeiten, App-Info + native App-Shortcuts (LauncherApps, max. 4, Symbole einfarbig) | [GEMESSEN] |
| T-FOLDER-PANEL (Rev.) | Ordner-Panel ist klares Glas | IMG_3130: innen ≈ außen (132 vs. 136) → Aufhellung 0,03, Blur = Hintergrund-Blur 14, Abdunkelung als Tönung | [GEMESSEN] |
| M-LAUNCH | App öffnen | Fenster wächst vom Icon in ≈ **0,23 s** auf Vollbild, Homescreen dahinter blurt | [GEMESSEN] grob (Phase 6) |
| M-DRAG-LIFT / M-REFLOW / M-DROP / M-BADGE / M-MENU | Phase 2 | noch nicht im Material | [GESCHÄTZT] (Phase-0-Werte bleiben Startpunkt) |

### 3.4 Scroll-Physik  [BELEGT]
Deceleration `UIScrollView.DecelerationRate.normal` = 0.998/ms, `.fast` = 0.99/ms ([Apple](https://developer.apple.com/documentation/uikit/uiscrollview/decelerationrate-swift.struct/normal)) — für App-Mediathek/Heute-Ansicht.

---

## 4. Verhaltensdetails

| Verhalten | Status |
|---|---|
| Antippen: sofortiges Abdunkeln, keine Skalierung, Start beim Loslassen | [GEMESSEN] |
| Long-Press auf leeren Bereich im Bearbeitungsmodus → Glas-Menü (4 Einträge, Icon + Text, liegt über dem Widget, blurt darunter) | [GEMESSEN] IMG_3125 |
| Bearbeitungsmodus: Statusleiste weg, 2 Glas-Buttons oben, Badges an allen Icons + Widgets + Dock | [GEMESSEN] |
| Swipe nach unten → Suche; Swipe rechts von Seite 1 → Heute-Ansicht (Widget-Liste, IMG_3132) | [GEMESSEN] |
| Ordner: Hintergrund blurt stark, Titel groß über dem Panel | [GEMESSEN] |
| Icon-Tilt/Glitzer | in iOS 27 entfernt [BELEGT] |

---

## 5. Android-Grenzen
1. **Systemhintergrund nicht lesbar** (Android 13+, `READ_WALLPAPER_INTERNAL` privilegiert) → Launcher zeichnet **eigenen Wallpaper**. ([Docs](https://developer.android.com/reference/android/app/WallpaperManager), [cvzi](https://github.com/cvzi/getwallpaperdrawable))
2. Blur/Shader: `RenderEffect` (31) + AGSL (33) → minSdk 33.
3. App-Öffnen (Phase 6): keine Remote-Transition für Drittanbieter (`CONTROL_REMOTE_APP_TRANSITION_ANIMATIONS` = Signatur-Permission); möglich `ActivityOptions.makeScaleUpAnimation`/`makeClipRevealAnimation`.
4. Tastatur-Animation gehört dem System (s. M-SEARCH-KB).
5. Emulator: 60 Hz + Maus.

---

## 6. Bitte am iPhone prüfen

Erledigt durch Messung: Modell/Maße ✔ · Pill existiert in iOS 27 ✔ · Antipp-Feedback ✔ · Icon-Ecke runder ✔ · Undo/Redo nicht vorhanden ✔ · Standard-Icongröße ✔

Noch offen:
- [ ] iPhone **17 Pro** oder **16 Pro**? (Maße identisch — nur für die Doku)
- [ ] Icon antippen und den Finger **wegziehen** (ohne App zu starten): Blendet das Abdunkeln weich zurück oder springt es?
- [ ] Wie lange musst du für den Bearbeitungsmodus auf ein Icon / leere Fläche drücken? (Gefühl: ~0,5 s oder ~1 s?)
- [ ] Für Phase 2 später: eine zweite Aufnahme mit **Icon ziehen** (andere weichen aus), an den Rand ziehen (Seitenwechsel), Icon löschen, Ordner per Drüberziehen erstellen.
- [ ] Für Phase 1 hilfreich: eine Aufnahme mit **langsamem** Wischen (Finger halten, loslassen ohne Schwung, auch zurückschnappen lassen) und Wischen über den Rand der letzten Seite (Gummiband).

## Nachtrag recording6/7 (Wallpaper, Customise, Glas-Kalibrierung)

| ID | Was | Wert | Status |
|---|---|---|---|
| T-WPE | Wallpaper-Editor (recording6 7,7 s) | Vorschau = echter Homescreen 0,699× bei (60,33 / 131,33), Ecke ≈ 38; Cancel/Done-Kapseln 27,33 hoch, Mitte y 32; Kreise Ø 40 bei y 797, x 69 + 60,5·i, Trenner x 291, Blur x 332; Beschriftung 12 pt, Grundlinie 833 | [GEMESSEN] |
| T-WPE-SHEET | „Choose Colour“ (17,5 s) | Glasblatt ab y 539,67, seitlich 6; 6×3 Farbkreise Ø 33, Raster 57,5 ab (57,9 / +95,33); Regler bei +270,33; 18 Farben gesampelt (DesignTokens.wallpaperSwatches) | [GEMESSEN] |
| T-WPE-PHOTO | Fotos-Picker (29,4 s) | Blatt ab y 62; Glas-Kopf (X Ø 44, „Photos | Collections“, Chips 32 hoch, ausgewählt #1BA6FB); Raster 3 Spalten, Zellen im Bildschirmformat | [GEMESSEN grob] |
| T-CUST | Customise-Blatt (recording7 14,7 / 22,4 s) | Glasblatt seitlich 10, unten 864; oben 594,67 (Tinted: 406); Stil-Vorschauen 61 pt bei x 67,5/156/244,6/333; Beschriftung y 770 (582); Hell/Dunkel y 836,6 (823); Tinted-Regler bei 643 / 697 | [GEMESSEN] |
| T-GLASS (Rev.) | Glas auf Foto-Wallpaper (recording7 2,1 s, Katze) | im Dock fast scharf (Detail innen ≈ ½ außen) → Blur 0,6 dp; Sättigung innen ≈ 1,28× außen → 1,2 (vorher 1,5 = zu orange) | [GEMESSEN] |
| T-CTX.editTint | Edit-Menü-Glas | über hellem Wallpaper 145 statt 188 → Tönung 0,24 | [GEMESSEN] |
| T-LABEL (adaptiv) | Labels auf dunklem/buntem Wallpaper | weiß mit leichtem Schatten, sobald mittlere Helligkeit < 0,6 | [GESCHÄTZT] |

## Nachtrag recording8 (Heute-Ansicht, Widgets, Resize)

| ID | Was | Wert | Status |
|---|---|---|---|
| T-WIDGET | Widget-Größen | klein 164 × 164, breit 349,33, groß 349,33 × 364,67; ragt 3,67 pt über die Icon-Kanten; Eckenradius ≈ 22 (continuous); rasten nur in Spalte 0/2 ein | [GEMESSEN] |
| T-TODAY | Heute-Ansicht (12,5 s) | Widgets ohne Label, oben 91,67, Rand 26,33, Abstand 21,33; Wallpaper stark verwischt; Batterien-Widget = dunkles getöntes Glas (innen ≈ 35 % der Hintergrundhelligkeit) | [GEMESSEN] |
| T-WIDGET-HANDLE | Größengriff (39,55 s) | gebogene Glas-Bohne ≈ 13 pt dick, Mittellinie 3 pt innerhalb der Kante, Schenkel ≈ 26 pt | [GEMESSEN] |
| M-WIDGET-RESIZE | Resize per Griff (39,5–48 s) | Rahmen folgt dem Finger, Icons fließen live um (immer vom Ausgangslayout aus), Unschärfe = Abstand zur nächsten Größe (Nutzer-Beobachtung), Einrasten Feder 0,35/0,86 [GESCHÄTZT] | [GEMESSEN]/[GESCHÄTZT] |
| T-WIDGET-MENU | Widget-Kontextmenü (15,2 s) | Größenleiste oben (klein · mittel · groß · extragroß ausgegraut), aktuelle grau hinterlegt; Menü mittig unter dem Widget | [GEMESSEN] |
| T-PICKER | Widget-Picker (26,5 s) | Blatt ab 123 pt, Suchfeld Weiß 11 % (Feld 57 vs. Blatt 32 Helligkeit), Widgets in Originalgröße, Titel fett + Untertitel grau | [GEMESSEN] |

