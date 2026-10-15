package io.github.janhurn9400.liquidy.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import io.github.janhurn9400.liquidy.tuning.Tunable
import io.github.janhurn9400.liquidy.tuning.tunable

/**
 * Alle Design-Werte (pt = dp). Jeder Wert verweist auf seine ID in docs/RESEARCH.md §2.
 * Referenz: iPhone 402 × 874 pt, gemessen aus docs/reference/iphone/IMG_3123/3124.
 */
object DesignTokens {

    // ── Raster (T-GRID-*, T-ICON*) [GEMESSEN] ───────────────────────────────────
    val iconSize = dpToken("T-ICON", "Icon-Größe", "Raster", 64f, 40f, 90f)
    val iconCorner = dpToken("T-ICON-R", "Icon-Eckenradius (continuous)", "Raster", 16.33f, 0f, 32f)
    val gridSide = dpToken("T-GRID-SIDE", "Seitenrand", "Raster", 30f, 0f, 60f)
    val gridColPitch = dpToken("T-GRID-COLPITCH", "Spaltenabstand Mitte-Mitte", "Raster", 92.67f, 60f, 120f)
    val gridTop = dpToken("T-GRID-TOP", "Oberkante Reihe 1", "Raster", 90f, 40f, 160f)
    val gridRowPitch = dpToken("T-GRID-ROWPITCH", "Reihenabstand", "Raster", 100.33f, 70f, 130f)
    const val GRID_COLUMNS = 4 // T-GRID-COLS
    const val GRID_ROWS = 6    // T-GRID-ROWS

    // ── Label (T-LABEL-*) ────────────────────────────────────────────────────────
    // Grundlinie relativ zur Icon-Oberkante [GEMESSEN]; Schriftgröße so, dass „FaceTime“ ≈ 53 pt breit ist [ABGELEITET]
    val labelBaseline = dpToken("T-LABEL-BASELINE", "Label-Grundlinie ab Icon-Oberkante", "Label", 80.33f, 64f, 100f)
    val labelFontSize = tunable("T-LABEL-SIZE", "Label-Schriftgröße", "Label", 12.25f, 9f, 15f, "sp")
    val labelLetterSpacing = tunable("T-LABEL-TRACKING", "Label-Laufweite", "Label", 0f, -0.5f, 0.5f, "sp")
    val labelWidth = dpToken("T-LABEL-W", "Label max. Breite", "Label", 88f, 60f, 100f)
    // T-LABEL-COLOR — auf hellem Wallpaper dunkel, warm [GEMESSEN] ≈ #493A36 (dunkelste 2 % der Glyphen)
    val labelColorOnLight = Color(0xFF493A36)
    /** Dunkler/bunter Wallpaper → helle Schrift mit leichtem Schatten (iOS passt Labels an, recording7). Gesetzt aus MainActivity. */
    var wallpaperIsDark by androidx.compose.runtime.mutableStateOf(false)
    val labelColor: Color get() = if (wallpaperIsDark) Color.White else labelColorOnLight
    val labelShadow: androidx.compose.ui.graphics.Shadow?
        get() = if (wallpaperIsDark) androidx.compose.ui.graphics.Shadow(Color(0x66000000), androidx.compose.ui.geometry.Offset(0f, 1f), 4f) else null
    /** Helligkeit (0…1), unter der ein Wallpaper als dunkel gilt [GESCHÄTZT]: Katze ≈ 0,4 → weiß, Grau ≈ 0,75 → dunkel. */
    // iOS 26/27: Labels sind fast immer weiß mit Schatten (Duo-Aufnahme 10-08: auch auf hellem Wallpaper weiß);
    // dunkle Schrift nur auf sehr hellen Bildern
    const val WALLPAPER_DARK_LUMA = 0.82f

    // ── Such-Pill & Page-Dots (T-PILL*, T-DOTS) [GEMESSEN] ─────────────────────────
    val pillWidth = dpToken("T-PILL.w", "Pill-Breite", "Pill", 78.67f, 50f, 140f)
    val pillHeight = dpToken("T-PILL.h", "Pill-Höhe", "Pill", 30f, 20f, 44f)
    val pillGapToDock = dpToken("T-PILL.gap", "Abstand Pill → Dock", "Pill", 20f, 0f, 50f)
    val pillContentColorOnLight = Color(0xFF6B605D)
    val pillContentColor: Color get() = if (wallpaperIsDark) Color(0xE6FFFFFF) else pillContentColorOnLight
    val pillIconSize = dpToken("T-PILL.icon", "Lupe Größe", "Pill", 9.33f, 6f, 16f)
    val pillFontSize = tunable("T-PILL.font", "„Search“-Schriftgröße", "Pill", 12.2f, 9f, 17f, "sp")
    val dotSize = dpToken("T-DOTS.size", "Punkt-Durchmesser", "Pill", 6.33f, 3f, 12f)
    val dotPitch = dpToken("T-DOTS.pitch", "Punkt-Abstand", "Pill", 14f, 6f, 24f)
    val dotInactiveAlpha = tunable("T-DOTS.alpha", "Inaktiver Punkt – Deckkraft", "Pill", 0.45f, 0f, 1f)

    // ── Dock (T-DOCK*) [GEMESSEN] ──────────────────────────────────────────────
    val dockSideInset = dpToken("T-DOCK-INSET.side", "Dock-Einzug seitlich", "Dock", 16.67f, 0f, 40f)
    val dockBottomInset = dpToken("T-DOCK-INSET.bottom", "Dock-Einzug unten", "Dock", 17f, 0f, 40f)
    val dockHeight = dpToken("T-DOCK.h", "Dock-Höhe", "Dock", 103.33f, 70f, 130f)
    val dockCorner = dpToken("T-DOCK-R", "Dock-Eckenradius", "Dock", 38.33f, 0f, 60f)
    val dockIconPitch = dpToken("T-DOCK-ICONS.pitch", "Dock-Icon-Abstand", "Dock", 88.22f, 64f, 110f)
    const val DOCK_SLOTS = 4

    // ── Liquid Glass (T-GLASS*) — Blur/Tönung [GESCHÄTZT], Rand-Verhalten [GEMESSEN] qualitativ ──
    val glassBlur = dpToken("T-GLASS.blur", "Glas-Blur-Radius", "Glas", 0.6f, 0f, 40f) // [GEMESSEN] recording7 (Katze): Grashalme im Dock fast scharf → 0,6 dp (vorher 1,5)
    // Linse (Kyant lens, docs/GLASS.md) [GESCHÄTZT] — Referenz-Wallpaper ist einfarbig, Brechung dort kaum messbar
    val glassRefraction = dpToken("T-GLASS.refraction", "Brechungshöhe (Randzone)", "Glas", 16f, 0f, 40f) // Kyant-Beispiel 16 dp
    val glassRefractionAmount = tunable("T-GLASS.amount", "Brechungsstärke (× Höhe)", "Glas", 3f, 0f, 6f, "×") // Nutzer: Brechung war kaum sichtbar
    val glassRefractionCornerShare = tunable("T-GLASS.cornerShare", "Brechungszone max. Anteil am Eckenradius", "Glas", 0.8f, 0.2f, 1f)
    val glassDepth = tunable("T-GLASS.depth", "Tiefeneffekt (0/1)", "Glas", 1f, 0f, 1f)
    val glassSaturation = tunable("T-GLASS.saturation", "Farbverstärkung", "Glas", 1.2f, 0.5f, 2.5f, "×") // [GEMESSEN] recording7: Sättigung im Dock ≈ 1,28× außen (vorher 1,5 → zu orange)
    val glassLightAngle = tunable("T-GLASS.lightAngle", "Lichtrichtung", "Glas", 90f, 0f, 180f, "°") // 90° = oben/unten hell [GEMESSEN]
    val glassLightFalloff = tunable("T-GLASS.lightFalloff", "Licht-Abfall", "Glas", 1f, 0f, 4f)
    // Apple: große Elemente (Menüs, Ordner, Dialoge) = dickeres Glas → stärkere Linse [BELEGT qualitativ, Faktor GESCHÄTZT]
    val glassThicknessLarge = tunable("T-GLASS.thickLarge", "Glasdicke große Elemente (× Dock)", "Glas", 1f, 0.5f, 4f, "×") // 2 ergab 24-pt-Brechungszone = sichtbarer Doppelrand; iOS-Ordner hat schmale Zone (IMG_3130)
    val folderPanelBlur = dpToken("T-FOLDER-PANEL.blur", "Ordner-Panel Blur", "Ordner", 14f, 0f, 40f) // [GEMESSEN] IMG_3130: innen so unscharf wie der Hintergrund (= T-FOLDER-BG.blur)
    val glassShadowRadius = dpToken("T-GLASS.shadowR", "Schatten-Radius", "Glas", 16f, 0f, 48f)
    val glassShadowAlpha = tunable("T-GLASS.shadowA", "Schatten-Deckkraft", "Glas", 0.06f, 0f, 0.4f)
    val glassInnerShadowRadius = dpToken("T-GLASS.innerR", "Innerer Schatten Radius", "Glas", 6f, 0f, 30f)
    val glassInnerShadowAlpha = tunable("T-GLASS.innerA", "Innerer Schatten Deckkraft", "Glas", 0.06f, 0f, 0.4f)
    // Regler „Klar ↔ Getönt“ (glass/GlassClarity) [GEMESSEN] Nutzer-iPhone 10-14, Dock-Detail gegen weichgezeichnetes Wallpaper:
    // Mitte σ ≈ 1,3 pt → Radius ≈ 1,4 dp; getönt σ ≥ 4 pt, Inneres völlig glatt → 13 dp [Untergrenze GEMESSEN, Wert GESCHÄTZT]
    val glassFrostMid = dpToken("T-GLASS.frostMid", "Glas-Regler Mitte: Unschärfe", "Glas", 1.4f, 0f, 10f)
    val glassFrostTinted = dpToken("T-GLASS.frostTinted", "Glas-Regler getönt: Unschärfe", "Glas", 13f, 0f, 40f)
    // Getönt = neutral-grauer Schleier: Helles wird dunkler, Dunkles heller, Farben blasser [GEMESSEN] Nutzer-iPhone 10-14:
    // Dock 204 → 196, Suchfeld 44 → 54, Mediathek-Feld blau 252 → 224 → Grau ≈ 140 (0,55) bei ≈ 15 % Deckkraft
    val glassTintedVeil = tunable("T-GLASS.tintedVeil", "Glas-Regler getönt: grauer Schleier", "Glas", 0.15f, 0f, 0.6f)
    val glassTintedGrey = tunable("T-GLASS.tintedGrey", "Glas-Regler getönt: Helligkeit des Schleiers", "Glas", 0.55f, 0f, 1f)
    val glassTint = tunable("T-GLASS.tint", "Glas-Abdunkelung", "Glas", 0.08f, 0f, 0.5f)
    val glassWhiteTint = tunable("T-GLASS.white", "Glas-Aufhellung", "Glas", 0.0f, 0f, 0.5f)
    val glassRimWidth = dpToken("T-GLASS.rim", "Rand-Breite", "Glas", 1.33f, 0f, 3f) // [GEMESSEN] recording7: weicher Übergang ≈ 10 px statt Strich
    val glassRimHighlight = tunable("T-GLASS.rimLight", "Lichtsaum oben/unten – Deckkraft", "Glas", 0.38f, 0f, 1f) // Nutzer: Linie zu hart; iOS ohne harten Strich
    val glassRimShadow = tunable("T-GLASS.rimDark", "Dunkler Seitenrand – Deckkraft", "Glas", 0.12f, 0f, 1f) // iOS: kein dunkles Band unter der Lichtkante

    // ── Icon-Feedback (T-PRESS) [GEMESSEN]: 57 % Helligkeit ≙ Schwarz α 0,43 ───────
    val pressDarkAlpha = tunable("T-PRESS.alpha", "Abdunkeln beim Antippen", "Icon", 0.43f, 0f, 1f)

    // ── Bearbeitungsmodus (T-BADGE, T-EDIT-BTN) [GEMESSEN] IMG_3124 ─────────────────
    val badgeSize = dpToken("T-BADGE.size", "Minus-Badge Durchmesser", "Bearbeiten", 26f, 14f, 40f)
    val badgeOffsetX = dpToken("T-BADGE.dx", "Badge-Mitte ab Icon-Ecke x", "Bearbeiten", 2.33f, -10f, 15f)
    val badgeOffsetY = dpToken("T-BADGE.dy", "Badge-Mitte ab Icon-Ecke y", "Bearbeiten", 3.17f, -10f, 15f)
    val badgeMinusWidth = dpToken("T-BADGE.minusW", "Minus-Länge", "Bearbeiten", 12f, 4f, 20f)
    val badgeMinusHeight = dpToken("T-BADGE.minusH", "Minus-Stärke", "Bearbeiten", 2f, 1f, 4f)
    val badgeHitSlop = dpToken("T-BADGE.hit", "Badge-Trefferzone (zusätzlich)", "Bearbeiten", 8f, 0f, 20f) // [GESCHÄTZT]
    val editButtonWidth = dpToken("T-EDIT-BTN.w", "Edit-Button Breite", "Bearbeiten", 66.67f, 40f, 100f)
    val editButtonHeight = dpToken("T-EDIT-BTN.h", "Edit-Button Höhe", "Bearbeiten", 27.33f, 20f, 44f)
    val editButtonTop = dpToken("T-EDIT-BTN.y", "Edit-Button oben", "Bearbeiten", 19f, 0f, 60f)
    val editButtonSide = dpToken("T-EDIT-BTN.x", "Edit-Button seitlich", "Bearbeiten", 35.67f, 0f, 60f)
    val editGlyphSize = dpToken("T-EDIT-BTN.glyph", "Edit-Button Symbolgröße", "Bearbeiten", 13.67f, 8f, 20f)
    val glyphColor = Color(0xFF493A36) // wie T-LABEL-COLOR, gemessen an Häkchen/Pinsel
    // T-DRAG — gezogenes Icon. [GESCHÄTZT]
    val dragScale = tunable("T-DRAG.scale", "Gezogenes Icon – Skalierung", "Bearbeiten", 1.12f, 1f, 1.5f, "×")
    val edgeZone = dpToken("T-EDGE", "Rand-Zone für Seitenwechsel", "Bearbeiten", 24f, 8f, 60f) // [GESCHÄTZT]

    // ── Ordner-Icon (T-FOLDER-ICON) [GEMESSEN] Aufnahme t = 12,1 s, „Utilities“ ────────────
    val folderMiniInset = dpToken("T-FOLDER-ICON.inset", "Ordner-Icon Innenrand", "Ordner", 10f, 2f, 20f)
    val folderMiniSize = dpToken("T-FOLDER-ICON.mini", "Mini-Icon Größe", "Ordner", 13f, 6f, 20f)
    val folderMiniPitch = dpToken("T-FOLDER-ICON.pitch", "Mini-Icon Abstand", "Ordner", 16f, 8f, 24f)
    /** Mini-Raster ist für das 64-pt-Icon gemessen → mit der aktuellen Icon-Größe skalieren (Duo: 60 pt), sonst sitzt es schief. */
    val folderMiniScale: Float get() = iconSize.value / iconSize.tunable.default
    val folderIconTint = tunable("T-FOLDER-ICON.tint", "Ordner-Icon Glas aufhellen", "Ordner", 0.12f, 0f, 0.6f)
    const val FOLDER_COLUMNS = 3
    const val FOLDER_ROWS = 3

    // ── Ordner geöffnet (T-FOLDER-PANEL/GRID/TITLE/BG) [GEMESSEN] IMG_3130, sofern nicht markiert ──
    val folderPanelWidth = dpToken("T-FOLDER-PANEL.w", "Ordner-Panel Breite", "Ordner", 306.33f, 200f, 380f)
    val folderPanelHeight = dpToken("T-FOLDER-PANEL.h", "Ordner-Panel Höhe", "Ordner", 352.67f, 200f, 480f)
    val folderPanelCorner = dpToken("T-FOLDER-PANEL.r", "Ordner-Panel Eckenradius", "Ordner", 38.5f, 10f, 60f)
    val folderGridLeft = dpToken("T-FOLDER-GRID.left", "Ordner-Raster Innenrand links", "Ordner", 29f, 0f, 60f)
    val folderGridTop = dpToken("T-FOLDER-GRID.top", "Ordner-Raster Innenrand oben", "Ordner", 28.67f, 0f, 60f)
    val folderRowPitch = dpToken("T-FOLDER-GRID.rowPitch", "Ordner-Reihenabstand", "Ordner", 104.33f, 70f, 130f)
    val folderTitleSize = tunable("T-FOLDER-TITLE.size", "Ordner-Titel Größe", "Ordner", 34.5f, 20f, 44f, "sp")
    val folderTitleX = dpToken("T-FOLDER-TITLE.x", "Ordner-Titel links", "Ordner", 74.33f, 0f, 120f)
    val folderTitleBaselineGap = dpToken("T-FOLDER-TITLE.gap", "Titel-Grundlinie über Panel", "Ordner", 35.67f, 0f, 80f)
    val folderPanelTint = tunable("T-FOLDER-PANEL.tint", "Ordner-Panel aufhellen", "Ordner", 0.03f, 0f, 0.8f) // [GEMESSEN] IMG_3130: innen ≈ außen (132 vs. 136) → klares Glas
    val folderBgBlur = dpToken("T-FOLDER-BG.blur", "Hintergrund-Blur bei offenem Ordner", "Ordner", 14f, 0f, 60f) // [ABGELEITET] Sichtvergleich IMG_3130
    val folderBgDim = tunable("T-FOLDER-BG.dim", "Hintergrund abdunkeln", "Ordner", 0.15f, 0f, 0.5f) // [GEMESSEN] ≈ −17 % Helligkeit
    val folderMergeScale = tunable("T-MERGE.scale", "Ordner-Hinweis Größe", "Ordner", 1.22f, 1f, 1.5f, "×") // [GEMESSEN] Kanten 93→72 / 187→162 px (Nutzer-iPhone 10-14)
    val folderMergeRadius = dpToken("T-MERGE.radius", "Ordner-Absicht: Abstand zur Icon-Mitte", "Ordner", 30f, 8f, 40f) // [GEMESSEN] ausgelöst bei ≈ 26 pt Abstand
    // Zone um ein Icon, in der nicht umsortiert wird (Ordner-Absicht hat Vorrang) [GESCHÄTZT] ≈ Icon-Fläche
    val folderMergeNear = dpToken("T-MERGE.near", "Über Icon: kein Ausweichen bis Abstand", "Ordner", 48f, 20f, 80f)
    // Ordner-Hinweis dunkelt das Glas ab [GEMESSEN] Nutzer-iPhone 10-14: Glas innen 75 → 53 bei Umgebung 96 ≈ +30 % Schwarz
    val folderMergeDark = tunable("T-MERGE.dark", "Ordner-Hinweis: Glas abdunkeln", "Ordner", 0.3f, 0f, 0.6f)
    val folderMergeStill = dpToken("T-MERGE.still", "Ordner-Absicht: gilt als Stillstand unter", "Ordner", 4f, 0f, 20f) // [GESCHÄTZT]

    // ── Suche (T-SEARCH) [GEMESSEN] IMG_3131 + Aufnahme 6,2–7,6 s ─────────────────────
    // Je pt Homescreen-Versatz h [GEMESSEN] recording3 (Fits über alle Zieh-Vorgänge)
    val searchScalePerPt = tunable("T-SEARCH.scale", "Homescreen schrumpft je pt", "Suche", 0.0011f, 0f, 0.004f, "×/pt")
    val searchDimPerPt = tunable("T-SEARCH.dim", "Abdunkeln je pt", "Suche", 0.0052f, 0f, 0.012f, "/pt") // Helligkeit −1,06/pt bei 188
    val searchDimMax = tunable("T-SEARCH.dimMax", "Abdunkeln max (offen)", "Suche", 0.69f, 0f, 0.95f) // offen: 59 von 188
    val searchOpenScale = tunable("T-SEARCH.openScale", "Homescreen-Skalierung offen", "Suche", 0.92f, 0.7f, 1f, "×") // [GEMESSEN] recording3: Versatz −2 pt, Skala 0,92
    val searchBlur = dpToken("T-SEARCH.blur", "Hintergrund-Blur beim Ziehen", "Suche", 12f, 0f, 60f) // Sichtvergleich
    val searchBlurOpen = dpToken("T-SEARCH.blurOpen", "Hintergrund-Blur offen", "Suche", 20f, 0f, 60f) // offen schärfer verwischt (Schärfe 0,2 vs 0,45)
    val searchBlockBlur = dpToken("T-SEARCH.blockBlur", "Vorschläge Blur beim Einblenden", "Suche", 6f, 0f, 40f) // Sichtvergleich h = 52 (12 pt war zu stark)
    val searchHeaderX = dpToken("T-SEARCH.headerX", "„Siri Suggestions“ links", "Suche", 17f, 0f, 40f)
    val searchHeaderBaseline = dpToken("T-SEARCH.headerY", "Überschrift-Grundlinie", "Suche", 87.67f, 40f, 140f)
    val searchHeaderSize = tunable("T-SEARCH.headerSize", "Überschrift-Größe", "Suche", 15f, 12f, 22f, "sp") // [GEMESSEN] „Siri Suggestions“ = 114 pt breit
    val searchCardSide = dpToken("T-SEARCH.cardSide", "Karte seitlich", "Suche", 15.67f, 0f, 40f)
    val searchCardTop = dpToken("T-SEARCH.cardTop", "Karte oben", "Suche", 98.33f, 60f, 160f)
    val searchCardHeight = dpToken("T-SEARCH.cardH", "Karte Höhe", "Suche", 113.67f, 80f, 160f)
    val searchCardCorner = dpToken("T-SEARCH.cardR", "Karte Eckenradius", "Suche", 38f, 10f, 60f) // [GESCHÄTZT]
    val searchCardFill = Color(0x1AFFFFFF) // gemessen: Karte ≈ +18 Helligkeitsstufen über dem Hintergrund
    val searchFieldSide = dpToken("T-SEARCH.fieldSide", "Suchfeld seitlich", "Suche", 7.33f, 0f, 30f)
    val searchFieldHeight = dpToken("T-SEARCH.fieldH", "Suchfeld Höhe", "Suche", 46.67f, 30f, 60f)
    val searchFieldGap = dpToken("T-SEARCH.fieldGap", "Suchfeld über Tastatur", "Suche", 9.33f, 0f, 30f)
    val searchFieldTint = tunable("T-SEARCH.fieldTint", "Suchfeld aufhellen", "Suche", 0.18f, 0f, 0.8f) // [GEMESSEN] IMG_3131: Feld ≈ 105 vs. Umgebung ≈ 63 Helligkeit
    val searchFieldFontSize = tunable("T-SEARCH.fieldFont", "Suchfeld Schrift", "Suche", 20f, 14f, 26f, "sp") // [GESCHÄTZT] aus IMG_3131
    val searchFieldPadding = dpToken("T-SEARCH.fieldPad", "Suchfeld Innenabstand", "Suche", 16f, 4f, 30f) // [GESCHÄTZT]
    val searchFieldIcon = dpToken("T-SEARCH.fieldIcon", "Suchfeld Lupe", "Suche", 17f, 10f, 30f) // [GEMESSEN grob] IMG_3131
    val searchFieldIconGap = dpToken("T-SEARCH.fieldIconGap", "Lupe → Text", "Suche", 10f, 0f, 24f) // [GESCHÄTZT]
    val searchLabelGap = dpToken("T-SEARCH.labelGap", "Vorschlag: Icon → Label", "Suche", 5f, 0f, 16f) // [GESCHÄTZT]
    val searchMicSize = dpToken("T-SEARCH.mic", "Mikrofon-Symbol", "Suche", 19f, 8f, 30f) // [GEMESSEN grob] IMG_3131 ≈ 19 pt hoch
    val searchGlyphColor = Color(0xF2FFFFFF) // Lupe + Mikrofon im Feld weiß (IMG_3131)
    val searchTextColor = Color.White
    val searchPlaceholderColor = Color(0x99FFFFFF)

    // ── App-Mediathek (T-LIB) [GEMESSEN] recording2 (Standbilder 3x, Ruhelage t = 9,9 s) ─────
    val libSearchTop = dpToken("T-LIB.searchTop", "Mediathek-Suchfeld oben", "Mediathek", 90f, 20f, 140f)
    val libSearchHeight = dpToken("T-LIB.searchH", "Mediathek-Suchfeld Höhe", "Mediathek", 47.33f, 30f, 60f)
    val libSearchSide = dpToken("T-LIB.searchSide", "Mediathek-Suchfeld seitlich", "Mediathek", 26f, 0f, 48f)
    val libSearchIcon = dpToken("T-LIB.searchIcon", "Mediathek-Suchfeld Lupe", "Mediathek", 18f, 10f, 30f)
    val libSearchIconGap = dpToken("T-LIB.searchIconGap", "Lupe → Text", "Mediathek", 10.5f, 0f, 24f)
    val libSearchFont = tunable("T-LIB.searchFont", "Suchfeld-Schrift", "Mediathek", 17f, 12f, 22f, "sp") // „App Library“ ≈ 100 pt breit
    val libSearchTint = tunable("T-LIB.searchTint", "Suchfeld aufhellen", "Mediathek", 0.32f, 0f, 0.8f) // Feld ≈ +30 Helligkeit über Umgebung
    val libSearchTextColorOnLight = Color(0xFF3C302E) // dunkelste Glyphen-Pixel
    val libSearchTextColor: Color get() = if (wallpaperIsDark) Color(0xE6FFFFFF) else libSearchTextColorOnLight
    val libSide = dpToken("T-LIB.side", "Mediathek Seitenrand", "Mediathek", 25.67f, 8f, 48f)
    val libGridTop = dpToken("T-LIB.gridTop", "Erste Kachelreihe oben", "Mediathek", 168.67f, 100f, 240f)
    val libGap = dpToken("T-LIB.gap", "Kachel-Abstand horizontal", "Mediathek", 20f, 8f, 40f)
    val libRowPitch = dpToken("T-LIB.rowPitch", "Kachelreihen-Abstand", "Mediathek", 200.67f, 150f, 260f)
    val libTileCorner = dpToken("T-LIB.tileR", "Kachel-Eckenradius", "Mediathek", 27.75f, 10f, 50f) // Kreis-Fit ≈ 29 pt, continuous
    val libTilePadding = dpToken("T-LIB.tilePad", "Kachel-Innenabstand", "Mediathek", 12.67f, 4f, 24f)
    val libTileIconGap = dpToken("T-LIB.iconGap", "Abstand große Icons", "Mediathek", 12f, 4f, 24f) // große Icons = 64 pt
    val libMini = dpToken("T-LIB.mini", "Mini-Icon Größe", "Mediathek", 25.67f, 12f, 32f)
    val libMiniGap = dpToken("T-LIB.miniGap", "Mini-Icon Abstand", "Mediathek", 6f, 0f, 16f)
    val libTileTint = tunable("T-LIB.tint", "Kachel-Glas aufhellen", "Mediathek", 0.05f, 0f, 0.6f) // Kachel ≈ +5 Helligkeit
    val libLabelGap = dpToken("T-LIB.labelGap", "Kachel → Label-Grundlinie", "Mediathek", 16.67f, 8f, 40f)
    val libRubberBand = tunable("T-LIB.rubber", "Überscroll-Grenze (×Bildschirmhöhe)", "Mediathek", 1f, 0.2f, 2f)
    val libEdgeBlur = dpToken("T-LIB.edgeBlur", "Oberer Rand: Blur", "Mediathek", 10f, 0f, 30f) // Scroll-Kante: Kacheln über dem Feld unscharf
    val libEdgeEnd = dpToken("T-LIB.edgeEnd", "Oberer Rand: Blur bis", "Mediathek", 120f, 40f, 200f)
    // Liste (A–Z) [GEMESSEN] t = 12,4 s
    val libListFieldRight = dpToken("T-LIB-LIST.fieldRight", "Suchfeld rechts (Liste)", "Mediathek", 302.33f, 200f, 380f)
    val libListFieldInset = dpToken("T-LIB-LIST.fieldInset", "Lupe links (Liste)", "Mediathek", 18.67f, 4f, 40f)
    val libCancelX = dpToken("T-LIB-LIST.cancelX", "„Cancel“ links", "Mediathek", 323f, 280f, 380f)
    val libListTop = dpToken("T-LIB-LIST.top", "Listen-Inhalt oben", "Mediathek", 150f, 100f, 220f)
    val libListHeader = dpToken("T-LIB-LIST.header", "Buchstaben-Kopf Höhe", "Mediathek", 40f, 20f, 60f)
    val libListHeaderBaseline = dpToken("T-LIB-LIST.headerBase", "Kopf-Grundlinie", "Mediathek", 32f, 10f, 50f)
    val libListHeaderFont = tunable("T-LIB-LIST.headerFont", "Kopf-Schrift", "Mediathek", 16f, 12f, 22f, "sp")
    val libListRow = dpToken("T-LIB-LIST.row", "Zeilenhöhe", "Mediathek", 72f, 40f, 100f)
    val libListIcon = dpToken("T-LIB-LIST.icon", "Zeilen-Icon", "Mediathek", 48f, 24f, 64f)
    val libListSide = dpToken("T-LIB-LIST.side", "Zeilen links", "Mediathek", 26.33f, 0f, 48f)
    val libListTextX = dpToken("T-LIB-LIST.textX", "Name links", "Mediathek", 91f, 60f, 120f)
    val libListSepRight = dpToken("T-LIB-LIST.sepRight", "Trennlinie bis", "Mediathek", 371f, 300f, 402f)
    val libListFont = tunable("T-LIB-LIST.font", "Namen-Schrift", "Mediathek", 17f, 12f, 22f, "sp")
    val libIndexX = dpToken("T-LIB-LIST.indexX", "Index Mitte", "Mediathek", 394.33f, 360f, 402f)
    val libIndexTop = dpToken("T-LIB-LIST.indexTop", "Index oben", "Mediathek", 180f, 100f, 260f)
    val libIndexPitch = dpToken("T-LIB-LIST.indexPitch", "Index Zeilenabstand", "Mediathek", 13.6f, 8f, 20f)
    val libIndexFont = tunable("T-LIB-LIST.indexFont", "Index-Schrift", "Mediathek", 11f, 8f, 14f, "sp")
    val libListBlur = dpToken("T-LIB-LIST.blur", "Kacheln dahinter Blur", "Mediathek", 24f, 0f, 60f) // Kacheln bleiben als Farbflecken sichtbar
    val libListDim = tunable("T-LIB-LIST.dim", "Abdunkeln (Liste)", "Mediathek", 0.34f, 0f, 0.8f) // Helligkeit 171 → 113
    val libListSeparator = Color(0x40FFFFFF)
    val libIndexColor = Color(0xE6FFFFFF) // Index weiß (recording2 12,4 s)
    val libListPlaceholder = Color(0x99FFFFFF)
    val libListFieldTint = tunable("T-LIB-LIST.fieldTint", "Suchfeld aufhellen (Liste)", "Mediathek", 0.18f, 0f, 0.8f) // Feld 156 vs. 130
    // Kategorie aufgeklappt [GEMESSEN] t = 18,2 s: kein Panel, Raster = Homescreen-Raster
    val libCatTitleX = dpToken("T-LIB-CAT.titleX", "Kategorie-Titel links", "Mediathek", 30f, 0f, 60f)
    val libCatTitleBaseline = dpToken("T-LIB-CAT.titleBase", "Kategorie-Titel Grundlinie", "Mediathek", 325.67f, 200f, 400f)
    val libCatGridTop = dpToken("T-LIB-CAT.gridTop", "Kategorie-Raster oben", "Mediathek", 371f, 250f, 450f)
    val libCatBlur = dpToken("T-LIB-CAT.blur", "Kategorie: Hintergrund-Blur", "Mediathek", 24f, 0f, 60f)

    // ── Widgets (T-WIDGET) [GEMESSEN] recording6 0,2 s (Wetter + Kalender, klein) ─────────────
    val widgetSmall = dpToken("T-WIDGET.small", "Kleines Widget (Kante)", "Widgets", 164f, 120f, 200f) // 2×2
    val widgetWide = dpToken("T-WIDGET.wide", "Mittleres/großes Widget Breite", "Widgets", 349.33f, 300f, 402f) // 4 Spalten
    val widgetOverhang = dpToken("T-WIDGET.overhang", "Widget ragt über die Icon-Kanten", "Widgets", 3.67f, 0f, 10f) // links 26,33 statt 30
    val widgetTopOffset = dpToken("T-WIDGET.top", "Widget-Oberkante ab Icon-Oberkante", "Widgets", 0.67f, -5f, 10f)
    val widgetCorner = dpToken("T-WIDGET.r", "Widget-Eckenradius (continuous)", "Widgets", 22f, 10f, 40f) // Rundung ≈ 27 pt lang
    val widgetLabelGap = dpToken("T-WIDGET.labelGap", "Widget-Label-Grundlinie unter dem Widget", "Widgets", 16.33f, 8f, 30f) // [GESCHÄTZT]
    val widgetGlyph = Color.White
    // Heute-Ansicht (T-TODAY) [GEMESSEN] recording8 12,5 s
    val todayTop = dpToken("T-TODAY.top", "Heute: erstes Widget oben", "Widgets", 91.67f, 40f, 160f)
    val todaySide = dpToken("T-TODAY.side", "Heute: Rand seitlich", "Widgets", 26.33f, 0f, 48f)
    val todayGap = dpToken("T-TODAY.gap", "Heute: Abstand zwischen Widgets", "Widgets", 21.33f, 4f, 40f)
    val todayBottom = dpToken("T-TODAY.bottom", "Heute: Platz unten", "Widgets", 40f, 0f, 200f)
    val todayBlur = dpToken("T-TODAY.blur", "Heute: Wallpaper-Blur", "Widgets", 30f, 0f, 60f) // Sichtvergleich: nur Farbflecken
    val widgetResizeBlur = dpToken("T-WIDGET.resizeBlur", "Inhalt-Blur beim Größe ändern", "Widgets", 14f, 0f, 40f) // [GESCHÄTZT] Sichtvergleich
    val widgetHandle = dpToken("T-WIDGET.handle", "Größengriff (Bogen) Radius", "Widgets", 15f, 6f, 30f) // recording8 39,6 s
    val widgetHandleHit = dpToken("T-WIDGET.handleHit", "Größengriff Trefferradius", "Widgets", 26f, 10f, 50f)

    // App öffnen (nur mit Magisk-Modul): Eckenradius der Karte auf dem Bildschirm [GEMESSEN] recording9 172–176
    // (Kreis-Fit ≈ 60–75 pt inkl. Apples kontinuierlicher Ecke; bleibt während der ganzen Bewegung konstant)
    val appLaunchCorner = dpToken("T-APP-LAUNCH.r", "App öffnen: Karten-Eckenradius", "Widgets", 62f, 0f, 100f)

    // ── Kontextmenü (T-CTX) [GEMESSEN] recording4 2,3 s (Telefon im Dock, Menü darüber) ─────────
    val ctxWidth = dpToken("T-CTX.w", "Menü-Breite", "Kontextmenü", 250.67f, 180f, 320f)
    val ctxCorner = dpToken("T-CTX.r", "Menü-Eckenradius", "Kontextmenü", 30f, 10f, 50f) // [GESCHÄTZT] Kurve ≈ 38 pt lang, continuous
    val ctxRow = dpToken("T-CTX.row", "Zeilenhöhe", "Kontextmenü", 42f, 30f, 60f)
    val ctxLine = dpToken("T-CTX.line", "Zusatzhöhe je weitere Textzeile", "Kontextmenü", 19.33f, 14f, 26f)
    val ctxPadV = dpToken("T-CTX.padV", "Innenabstand oben/unten", "Kontextmenü", 10f, 0f, 24f)
    val ctxGlyphX = dpToken("T-CTX.glyphX", "Symbol-Mitte von links", "Kontextmenü", 40.67f, 16f, 60f)
    val ctxGlyph = dpToken("T-CTX.glyph", "Symbol-Größe", "Kontextmenü", 16.33f, 10f, 26f)
    val ctxTextX = dpToken("T-CTX.textX", "Text links", "Kontextmenü", 64.67f, 30f, 90f)
    val ctxTextRight = dpToken("T-CTX.textRight", "Text rechter Rand", "Kontextmenü", 20f, 4f, 40f) // [GESCHÄTZT] „View Most Recent Call“ bricht um
    val ctxFont = tunable("T-CTX.font", "Schrift", "Kontextmenü", 17f, 12f, 22f, "sp")
    val ctxGap = dpToken("T-CTX.gap", "Abstand Menü ↔ angehobenes Icon", "Kontextmenü", 9f, 0f, 30f)
    val ctxSide = dpToken("T-CTX.side", "Mindestabstand zum Bildschirmrand", "Kontextmenü", 16f, 0f, 40f)
    val ctxDim = tunable("T-CTX.dim", "Hintergrund abdunkeln", "Kontextmenü", 0.52f, 0f, 0.9f) // Helligkeit 204 → 98
    val ctxBlur = dpToken("T-CTX.blur", "Menü-Glas Blur", "Kontextmenü", 20f, 0f, 40f) // recording4: Hintergrund hinter dem Menü stark verwischt
    val ctxTint = tunable("T-CTX.tint", "Menü-Glas aufhellen", "Kontextmenü", 0.05f, 0f, 0.5f) // Menü ≈ +6 über abgedunkeltem Grund
    val editMenuTint = tunable("T-CTX.editTint", "Edit-Menü abdunkeln", "Kontextmenü", 0.24f, 0f, 0.8f) // recording6 4,9 s: 145 statt 188 über hellem Wallpaper
    val ctxPressedFill = Color(0x1FFFFFFF) // Zeile gedrückt [GESCHÄTZT]
    val ctxDestructive = Color(0xFFFC7075) // „Remove App“ gemessen
    val ctxText = Color(0xFFFDFAFB)

    // ── Lösch-Dialog (T-ALERT) [GESCHÄTZT] iOS-26/27-Alert-Stil ─────────────────────
    val alertWidth = dpToken("T-ALERT.w", "Dialog-Breite", "Dialog", 300f, 240f, 360f)
    val alertCorner = dpToken("T-ALERT.r", "Dialog-Eckenradius", "Dialog", 34f, 10f, 50f)
    val alertPadding = dpToken("T-ALERT.pad", "Dialog-Innenabstand", "Dialog", 20f, 8f, 32f)
    val alertButtonHeight = dpToken("T-ALERT.btnH", "Dialog-Button Höhe", "Dialog", 48f, 36f, 60f)
    val alertButtonGap = dpToken("T-ALERT.gap", "Dialog-Button Abstand", "Dialog", 8f, 0f, 16f)
    val alertTitleSize = tunable("T-ALERT.title", "Dialog-Titel Größe", "Dialog", 17f, 12f, 22f, "sp")
    val alertBodySize = tunable("T-ALERT.body", "Dialog-Text Größe", "Dialog", 14f, 10f, 18f, "sp")
    val alertScrim = tunable("T-ALERT.scrim", "Dialog-Abdunkelung", "Dialog", 0.2f, 0f, 0.6f)
    val alertTint = tunable("T-ALERT.tint", "Dialog-Glas aufhellen", "Dialog", 0.55f, 0f, 1f)
    val alertBlur = dpToken("T-ALERT.blur", "Dialog-Hintergrund-Blur", "Dialog", 20f, 0f, 40f)
    val alertButtonFill = Color(0x1F000000)
    val destructive = Color(0xFFFF3B30)
    val alertText = Color(0xFF1C1C1E)
    val alertSecondaryText = Color(0xFF3C3C43)

    // ── Wallpaper-Editor (T-WPE) [GEMESSEN] recording6 7,7 / 17,5 s ─────────────────────────
    val wpePreviewX = dpToken("T-WPE.previewX", "Vorschau links", "Wallpaper", 60.33f, 20f, 100f)
    val wpePreviewY = dpToken("T-WPE.previewY", "Vorschau oben", "Wallpaper", 131.33f, 60f, 200f)
    val wpePreviewScale = tunable("T-WPE.previewScale", "Vorschau-Skala", "Wallpaper", 0.699f, 0.4f, 1f, "×") // 281 / 402
    val wpePreviewCorner = dpToken("T-WPE.previewR", "Vorschau-Eckenradius", "Wallpaper", 38f, 0f, 60f) // [GESCHÄTZT] ≈ Display-Ecke × 0,7
    val wpeTopY = dpToken("T-WPE.topY", "Cancel/Done Mitte y", "Wallpaper", 32f, 10f, 60f)
    val wpeTopSide = dpToken("T-WPE.topSide", "Cancel/Done seitlich", "Wallpaper", 39.33f, 0f, 60f) // Kapsel-Rand (Mitte 73 / 329 pt)
    val wpeButtonW = dpToken("T-WPE.btnW", "Cancel/Done Breite", "Wallpaper", 67.33f, 40f, 100f) // beide gleich breit
    val wpeButtonH = dpToken("T-WPE.btnH", "Cancel/Done Höhe", "Wallpaper", 27.33f, 20f, 40f) // = T-EDIT-BTN
    val wpeButtonPad = dpToken("T-WPE.btnPad", "Cancel/Done Innenabstand", "Wallpaper", 11f, 4f, 24f)
    val wpeButtonFont = tunable("T-WPE.btnFont", "Cancel/Done Schrift", "Wallpaper", 17f, 11f, 20f, "sp") // Versalhöhe ≈ 12,7 pt
    val wpeBarY = dpToken("T-WPE.barY", "Kreise Mitte y", "Wallpaper", 797f, 700f, 860f)
    val wpeBarFirstX = dpToken("T-WPE.barX", "erster Kreis Mitte x", "Wallpaper", 69f, 20f, 120f)
    val wpeBarPitch = dpToken("T-WPE.barPitch", "Kreis-Abstand", "Wallpaper", 60.5f, 40f, 80f)
    val wpeBarDividerX = dpToken("T-WPE.dividerX", "Trenner x", "Wallpaper", 291f, 250f, 330f)
    val wpeBarBlurX = dpToken("T-WPE.blurX", "Blur Mitte x", "Wallpaper", 332f, 290f, 380f)
    val wpeCircle = dpToken("T-WPE.circle", "Kreis-Durchmesser", "Wallpaper", 40f, 28f, 56f)
    val wpeLabelY = dpToken("T-WPE.labelY", "Beschriftung Grundlinie", "Wallpaper", 833f, 800f, 870f)
    val wpeLabelFont = tunable("T-WPE.labelFont", "Beschriftung Schrift", "Wallpaper", 12f, 9f, 16f, "sp")
    val wpeSheetTop = dpToken("T-WPE-SHEET.top", "Farbblatt oben", "Wallpaper", 539.67f, 400f, 700f)
    val wpeSheetSide = dpToken("T-WPE-SHEET.side", "Farbblatt seitlich", "Wallpaper", 6f, 0f, 30f)
    val wpeSheetCorner = dpToken("T-WPE-SHEET.r", "Farbblatt Eckenradius", "Wallpaper", 40f, 10f, 60f) // [GESCHÄTZT]
    val wpeSheetTitleY = dpToken("T-WPE-SHEET.titleY", "Titel Mitte y (ab Blatt)", "Wallpaper", 36.33f, 10f, 60f)
    val wpeSwatchX = dpToken("T-WPE-SHEET.swatchX", "erste Farbe Mitte x", "Wallpaper", 57.9f, 20f, 90f)
    val wpeSwatchPitch = dpToken("T-WPE-SHEET.pitch", "Farbraster-Abstand", "Wallpaper", 57.5f, 40f, 70f)
    val wpeSwatchTop = dpToken("T-WPE-SHEET.swatchY", "erste Reihe Mitte y (ab Blatt)", "Wallpaper", 95.33f, 60f, 140f)
    val wpeSwatch = dpToken("T-WPE-SHEET.swatch", "Farbkreis-Durchmesser", "Wallpaper", 33f, 20f, 44f)
    val wpeSliderY = dpToken("T-WPE-SHEET.sliderY", "Regler Mitte y (ab Blatt)", "Wallpaper", 270.33f, 200f, 320f)
    val wpeSliderX = dpToken("T-WPE-SHEET.sliderX", "Regler links", "Wallpaper", 41.8f, 10f, 80f)
    val wpeSliderH = dpToken("T-WPE-SHEET.sliderH", "Regler-Spur Höhe", "Wallpaper", 14.5f, 4f, 24f)
    val wpeThumb = dpToken("T-WPE-SHEET.thumb", "Regler-Knopf", "Wallpaper", 32f, 20f, 44f)
    /** 18 Farben aus dem iOS-Farbblatt, gesampelt (recording6 17,5 s). */
    val wallpaperSwatches = intArrayOf(
        0xFF47D3FE.toInt(), 0xFF6BA2FE.toInt(), 0xFFB185FD.toInt(), 0xFFE14FFE.toInt(), 0xFFFF6BA0.toInt(), 0xFFFE8B7F.toInt(),
        0xFFFF9E73.toInt(), 0xFFFEC66F.toInt(), 0xFFFEE370.toInt(), 0xFFFFFE91.toInt(), 0xFFFFFE8D.toInt(), 0xFFBBF48B.toInt(),
        0xFFACC0A4.toInt(), 0xFF9BB8C0.toInt(), 0xFFC0A9BF.toInt(), 0xFFC9BDB0.toInt(), 0xFFFFFFFF.toInt(), 0xFF000000.toInt(),
    )

    // ── Wallpaper (eigener, generierter Verlauf; Farben aus IMG_3123 gesampelt) ───
    val wallpaperBase = Color(0xFFC3C1C0)
    val wallpaperLight = Color(0xFFD4D2CF)
    val wallpaperDark = Color(0xFFB8B6B6)

    fun touch() = Unit
}

/** Tunable in pt = dp. */
class DpToken(val tunable: Tunable) {
    val dp: Dp get() = tunable.value.dp
    val value: Float get() = tunable.value
}

private fun dpToken(id: String, label: String, group: String, default: Float, min: Float, max: Float) =
    DpToken(tunable(id, label, group, default, min, max, "pt"))
