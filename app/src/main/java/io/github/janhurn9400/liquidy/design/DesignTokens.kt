package io.github.janhurn9400.liquidy.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import io.github.janhurn9400.liquidy.tuning.Tunable
import io.github.janhurn9400.liquidy.tuning.tunable

/**
 * All design values (pt = dp). Every value refers to its ID in docs/RESEARCH.md §2.
 * Reference: iPhone 402 × 874 pt, measured from docs/reference/iphone/IMG_3123/3124.
 */
object DesignTokens {

    // ── Grid (T-GRID-*, T-ICON*) [MEASURED] ───────────────────────────────────
    val iconSize = dpToken("T-ICON", "Icon size", "Grid", 64f, 40f, 90f)
    val iconCorner = dpToken("T-ICON-R", "Icon corner radius (continuous)", "Grid", 16.33f, 0f, 32f)
    val gridSide = dpToken("T-GRID-SIDE", "Side margin", "Grid", 30f, 0f, 60f)
    val gridColPitch = dpToken("T-GRID-COLPITCH", "Column pitch centre-to-centre", "Grid", 92.67f, 60f, 120f)
    val gridTop = dpToken("T-GRID-TOP", "Top of row 1", "Grid", 90f, 40f, 160f)
    val gridRowPitch = dpToken("T-GRID-ROWPITCH", "Row pitch", "Grid", 100.33f, 70f, 130f)
    const val GRID_COLUMNS = 4 // T-GRID-COLS
    const val GRID_ROWS = 6    // T-GRID-ROWS

    // ── Label (T-LABEL-*) ────────────────────────────────────────────────────────
    // Baseline relative to the icon top [MEASURED]; font size such that "FaceTime" is ≈ 53 pt wide [DERIVED]
    val labelBaseline = dpToken("T-LABEL-BASELINE", "Label baseline from icon top", "Label", 80.33f, 64f, 100f)
    val labelFontSize = tunable("T-LABEL-SIZE", "Label font size", "Label", 12.25f, 9f, 15f, "sp")
    val labelLetterSpacing = tunable("T-LABEL-TRACKING", "Label tracking", "Label", 0f, -0.5f, 0.5f, "sp")
    val labelWidth = dpToken("T-LABEL-W", "Label max. width", "Label", 88f, 60f, 100f)
    // T-LABEL-COLOR — dark on a light wallpaper, warm [MEASURED] ≈ #493A36 (darkest 2 % of the glyphs)
    val labelColorOnLight = Color(0xFF493A36)
    /** Dark/colourful wallpaper → light text with a slight shadow (iOS adapts labels, recording7). Set from MainActivity. */
    var wallpaperIsDark by androidx.compose.runtime.mutableStateOf(false)
    val labelColor: Color get() = if (wallpaperIsDark) Color.White else labelColorOnLight
    val labelShadow: androidx.compose.ui.graphics.Shadow?
        get() = if (wallpaperIsDark) androidx.compose.ui.graphics.Shadow(Color(0x66000000), androidx.compose.ui.geometry.Offset(0f, 1f), 4f) else null
    /** Brightness (0…1) below which a wallpaper counts as dark [ESTIMATED]: cat ≈ 0.4 → white, grey ≈ 0.75 → dark. */
    // iOS 26/27: labels are almost always white with a shadow (Duo recording 10-08: white even on a light wallpaper);
    // dark text only on very light images
    const val WALLPAPER_DARK_LUMA = 0.82f

    // ── Search pill & page dots (T-PILL*, T-DOTS) [MEASURED] ─────────────────────────
    val pillWidth = dpToken("T-PILL.w", "Pill width", "Pill", 78.67f, 50f, 140f)
    val pillHeight = dpToken("T-PILL.h", "Pill height", "Pill", 30f, 20f, 44f)
    val pillGapToDock = dpToken("T-PILL.gap", "Gap pill → dock", "Pill", 20f, 0f, 50f)
    val pillContentColorOnLight = Color(0xFF6B605D)
    val pillContentColor: Color get() = if (wallpaperIsDark) Color(0xE6FFFFFF) else pillContentColorOnLight
    val pillIconSize = dpToken("T-PILL.icon", "Magnifier size", "Pill", 9.33f, 6f, 16f)
    val pillFontSize = tunable("T-PILL.font", "\"Search\" font size", "Pill", 12.2f, 9f, 17f, "sp")
    val dotSize = dpToken("T-DOTS.size", "Dot diameter", "Pill", 6.33f, 3f, 12f)
    val dotPitch = dpToken("T-DOTS.pitch", "Dot pitch", "Pill", 14f, 6f, 24f)
    val dotInactiveAlpha = tunable("T-DOTS.alpha", "Inactive dot – opacity", "Pill", 0.45f, 0f, 1f)

    // ── Dock (T-DOCK*) [MEASURED] ──────────────────────────────────────────────
    val dockSideInset = dpToken("T-DOCK-INSET.side", "Dock inset side", "Dock", 16.67f, 0f, 40f)
    val dockBottomInset = dpToken("T-DOCK-INSET.bottom", "Dock inset bottom", "Dock", 17f, 0f, 40f)
    val dockHeight = dpToken("T-DOCK.h", "Dock height", "Dock", 103.33f, 70f, 130f)
    val dockCorner = dpToken("T-DOCK-R", "Dock corner radius", "Dock", 38.33f, 0f, 60f)
    val dockIconPitch = dpToken("T-DOCK-ICONS.pitch", "Dock icon pitch", "Dock", 88.22f, 64f, 110f)
    const val DOCK_SLOTS = 4

    // ── Liquid Glass (T-GLASS*) — blur/tint [ESTIMATED], edge behaviour [MEASURED] qualitatively ──
    val glassBlur = dpToken("T-GLASS.blur", "Glass blur radius", "Glass", 0.6f, 0f, 40f) // [MEASURED] recording7 (cat): blades of grass in the dock almost sharp → 0.6 dp (was 1.5)
    // Lens (Kyant lens, docs/GLASS.md) [ESTIMATED] — the reference wallpaper is plain, refraction is barely measurable there
    val glassRefraction = dpToken("T-GLASS.refraction", "Refraction height (edge zone)", "Glass", 16f, 0f, 40f) // Kyant example 16 dp
    val glassRefractionAmount = tunable("T-GLASS.amount", "Refraction amount (× height)", "Glass", 3f, 0f, 6f, "×") // user: refraction was barely visible
    val glassRefractionCornerShare = tunable("T-GLASS.cornerShare", "Refraction zone max. share of corner radius", "Glass", 0.8f, 0.2f, 1f)
    val glassDepth = tunable("T-GLASS.depth", "Depth effect (0/1)", "Glass", 1f, 0f, 1f)
    val glassSaturation = tunable("T-GLASS.saturation", "Saturation boost", "Glass", 1.2f, 0.5f, 2.5f, "×") // [MEASURED] recording7: saturation in the dock ≈ 1.28× outside (was 1.5 → too orange)
    val glassLightAngle = tunable("T-GLASS.lightAngle", "Light direction", "Glass", 90f, 0f, 180f, "°") // 90° = top/bottom bright [MEASURED]
    val glassLightFalloff = tunable("T-GLASS.lightFalloff", "Light falloff", "Glass", 1f, 0f, 4f)
    // Apple: large elements (menus, folders, dialogs) = thicker glass → stronger lens [SOURCED qualitatively, factor ESTIMATED]
    val glassThicknessLarge = tunable("T-GLASS.thickLarge", "Glass thickness large elements (× dock)", "Glass", 1f, 0.5f, 4f, "×") // 2 gave a 24 pt refraction zone = visible double rim; the iOS folder has a narrow zone (IMG_3130)
    val folderPanelBlur = dpToken("T-FOLDER-PANEL.blur", "Folder panel blur", "Folders", 14f, 0f, 40f) // [MEASURED] IMG_3130: inside as blurred as the background (= T-FOLDER-BG.blur)
    val glassShadowRadius = dpToken("T-GLASS.shadowR", "Shadow radius", "Glass", 16f, 0f, 48f)
    val glassShadowAlpha = tunable("T-GLASS.shadowA", "Shadow opacity", "Glass", 0.06f, 0f, 0.4f)
    val glassInnerShadowRadius = dpToken("T-GLASS.innerR", "Inner shadow radius", "Glass", 6f, 0f, 30f)
    val glassInnerShadowAlpha = tunable("T-GLASS.innerA", "Inner shadow opacity", "Glass", 0.06f, 0f, 0.4f)
    // "Clear ↔ Tinted" slider (glass/GlassClarity) [MEASURED] user iPhone 10-14, dock detail against a blurred wallpaper:
    // middle σ ≈ 1.3 pt → radius ≈ 1.4 dp; tinted σ ≥ 4 pt, interior completely smooth → 13 dp [lower bound MEASURED, value ESTIMATED]
    val glassFrostMid = dpToken("T-GLASS.frostMid", "Glass slider middle: blur", "Glass", 1.4f, 0f, 10f)
    val glassFrostTinted = dpToken("T-GLASS.frostTinted", "Glass slider tinted: blur", "Glass", 13f, 0f, 40f)
    // Tinted = neutral grey veil: light gets darker, dark gets lighter, colours paler [MEASURED] user iPhone 10-14:
    // dock 204 → 196, search field 44 → 54, App Library field blue 252 → 224 → grey ≈ 140 (0.55) at ≈ 15 % opacity
    val glassTintedVeil = tunable("T-GLASS.tintedVeil", "Glass slider tinted: grey veil", "Glass", 0.15f, 0f, 0.6f)
    val glassTintedGrey = tunable("T-GLASS.tintedGrey", "Glass slider tinted: veil brightness", "Glass", 0.55f, 0f, 1f)
    val glassTint = tunable("T-GLASS.tint", "Glass darkening", "Glass", 0.08f, 0f, 0.5f)
    val glassWhiteTint = tunable("T-GLASS.white", "Glass brightening", "Glass", 0.0f, 0f, 0.5f)
    val glassRimWidth = dpToken("T-GLASS.rim", "Rim width", "Glass", 1.33f, 0f, 3f) // [MEASURED] recording7: soft transition ≈ 10 px instead of a line
    val glassRimHighlight = tunable("T-GLASS.rimLight", "Light rim top/bottom – opacity", "Glass", 0.38f, 0f, 1f) // user: line too hard; iOS has no hard line
    val glassRimShadow = tunable("T-GLASS.rimDark", "Dark side rim – opacity", "Glass", 0.12f, 0f, 1f) // iOS: no dark band below the light edge

    // ── Icon feedback (T-PRESS) [MEASURED]: 57 % brightness ≙ black α 0.43 ───────
    val pressDarkAlpha = tunable("T-PRESS.alpha", "Dimming on tap", "Icon", 0.43f, 0f, 1f)

    // ── Edit mode (T-BADGE, T-EDIT-BTN) [MEASURED] IMG_3124 ─────────────────
    val badgeSize = dpToken("T-BADGE.size", "Minus badge diameter", "Editing", 26f, 14f, 40f)
    val badgeOffsetX = dpToken("T-BADGE.dx", "Badge centre from icon corner x", "Editing", 2.33f, -10f, 15f)
    val badgeOffsetY = dpToken("T-BADGE.dy", "Badge centre from icon corner y", "Editing", 3.17f, -10f, 15f)
    val badgeMinusWidth = dpToken("T-BADGE.minusW", "Minus length", "Editing", 12f, 4f, 20f)
    val badgeMinusHeight = dpToken("T-BADGE.minusH", "Minus thickness", "Editing", 2f, 1f, 4f)
    val badgeHitSlop = dpToken("T-BADGE.hit", "Badge hit zone (extra)", "Editing", 8f, 0f, 20f) // [ESTIMATED]
    val editButtonWidth = dpToken("T-EDIT-BTN.w", "Edit button width", "Editing", 66.67f, 40f, 100f)
    val editButtonHeight = dpToken("T-EDIT-BTN.h", "Edit button height", "Editing", 27.33f, 20f, 44f)
    val editButtonTop = dpToken("T-EDIT-BTN.y", "Edit button top", "Editing", 19f, 0f, 60f)
    val editButtonSide = dpToken("T-EDIT-BTN.x", "Edit button side", "Editing", 35.67f, 0f, 60f)
    val editGlyphSize = dpToken("T-EDIT-BTN.glyph", "Edit button glyph size", "Editing", 13.67f, 8f, 20f)
    val glyphColor = Color(0xFF493A36) // like T-LABEL-COLOR, measured on checkmark/brush
    // T-DRAG — dragged icon or widget. [MEASURED] user iPhone 10-16, Today view: dragged small widget 182 vs. 163 pt ≈ 1.12×
    val dragScale = tunable("T-DRAG.scale", "Dragged icon – scale", "Editing", 1.12f, 1f, 1.5f, "×")
    val edgeZone = dpToken("T-EDGE", "Edge zone for page change", "Editing", 24f, 8f, 60f) // [ESTIMATED]

    // ── Folder icon (T-FOLDER-ICON) [MEASURED] recording t = 12.1 s, "Utilities" ────────────
    val folderMiniInset = dpToken("T-FOLDER-ICON.inset", "Folder icon inset", "Folders", 10f, 2f, 20f)
    val folderMiniSize = dpToken("T-FOLDER-ICON.mini", "Mini icon size", "Folders", 13f, 6f, 20f)
    val folderMiniPitch = dpToken("T-FOLDER-ICON.pitch", "Mini icon gap", "Folders", 16f, 8f, 24f)
    /** The mini grid was measured for the 64 pt icon → scale with the current icon size (Duo: 60 pt), otherwise it sits off-centre. */
    val folderMiniScale: Float get() = iconSize.value / iconSize.tunable.default
    val folderIconTint = tunable("T-FOLDER-ICON.tint", "Folder icon glass brighten", "Folders", 0.12f, 0f, 0.6f)
    const val FOLDER_COLUMNS = 3
    const val FOLDER_ROWS = 3

    // ── Folder open (T-FOLDER-PANEL/GRID/TITLE/BG) [MEASURED] IMG_3130, unless marked ──
    val folderPanelWidth = dpToken("T-FOLDER-PANEL.w", "Folder panel width", "Folders", 306.33f, 200f, 380f)
    val folderPanelHeight = dpToken("T-FOLDER-PANEL.h", "Folder panel height", "Folders", 352.67f, 200f, 480f)
    val folderPanelCorner = dpToken("T-FOLDER-PANEL.r", "Folder panel corner radius", "Folders", 38.5f, 10f, 60f)
    val folderGridLeft = dpToken("T-FOLDER-GRID.left", "Folder grid inset left", "Folders", 29f, 0f, 60f)
    val folderGridTop = dpToken("T-FOLDER-GRID.top", "Folder grid inset top", "Folders", 28.67f, 0f, 60f)
    val folderRowPitch = dpToken("T-FOLDER-GRID.rowPitch", "Folder row pitch", "Folders", 104.33f, 70f, 130f)
    val folderTitleSize = tunable("T-FOLDER-TITLE.size", "Folder title size", "Folders", 34.5f, 20f, 44f, "sp")
    val folderTitleX = dpToken("T-FOLDER-TITLE.x", "Folder title left", "Folders", 74.33f, 0f, 120f)
    val folderTitleBaselineGap = dpToken("T-FOLDER-TITLE.gap", "Title baseline above panel", "Folders", 35.67f, 0f, 80f)
    val folderPanelTint = tunable("T-FOLDER-PANEL.tint", "Folder panel brighten", "Folders", 0.03f, 0f, 0.8f) // [MEASURED] IMG_3130: inside ≈ outside (132 vs. 136) → clear glass
    val folderBgBlur = dpToken("T-FOLDER-BG.blur", "Background blur with open folder", "Folders", 14f, 0f, 60f) // [DERIVED] visual comparison IMG_3130
    val folderBgDim = tunable("T-FOLDER-BG.dim", "Dim background", "Folders", 0.15f, 0f, 0.5f) // [MEASURED] ≈ −17 % brightness
    val folderMergeScale = tunable("T-MERGE.scale", "Folder hint size", "Folders", 1.22f, 1f, 1.5f, "×") // [MEASURED] edges 93→72 / 187→162 px (user iPhone 10-14)
    val folderMergeRadius = dpToken("T-MERGE.radius", "Folder intent: distance to icon centre", "Folders", 30f, 8f, 40f) // [MEASURED] triggered at ≈ 26 pt distance
    // Zone around an icon in which no reordering happens (folder intent has priority) [ESTIMATED] ≈ icon area
    val folderMergeNear = dpToken("T-MERGE.near", "Over icon: no reflow until distance", "Folders", 48f, 20f, 80f)
    // Folder hint darkens the glass [MEASURED] user iPhone 10-14: glass inside 75 → 53 with surroundings 96 ≈ +30 % black
    val folderMergeDark = tunable("T-MERGE.dark", "Folder hint: darken glass", "Folders", 0.3f, 0f, 0.6f)
    val folderMergeStill = dpToken("T-MERGE.still", "Folder intent: counts as standstill below", "Folders", 4f, 0f, 20f) // [ESTIMATED]

    // ── Search (T-SEARCH) [MEASURED] IMG_3131 + recording 6.2–7.6 s ─────────────────────
    // Per pt of home offset h [MEASURED] recording3 (fits over all drags)
    val searchScalePerPt = tunable("T-SEARCH.scale", "Home screen shrinks per pt", "Search", 0.0011f, 0f, 0.004f, "×/pt")
    val searchDimPerPt = tunable("T-SEARCH.dim", "Dimming per pt", "Search", 0.0052f, 0f, 0.012f, "/pt") // brightness −1.06/pt at 188
    val searchDimMax = tunable("T-SEARCH.dimMax", "Dimming max (open)", "Search", 0.69f, 0f, 0.95f) // open: 59 of 188
    val searchOpenScale = tunable("T-SEARCH.openScale", "Home screen scale open", "Search", 0.92f, 0.7f, 1f, "×") // [MEASURED] recording3: offset −2 pt, scale 0.92
    val searchBlur = dpToken("T-SEARCH.blur", "Background blur while dragging", "Search", 12f, 0f, 60f) // visual comparison
    val searchBlurOpen = dpToken("T-SEARCH.blurOpen", "Background blur open", "Search", 20f, 0f, 60f) // open blurred more strongly (sharpness 0.2 vs 0.45)
    val searchBlockBlur = dpToken("T-SEARCH.blockBlur", "Suggestions blur while fading in", "Search", 6f, 0f, 40f) // visual comparison h = 52 (12 pt was too strong)
    val searchHeaderX = dpToken("T-SEARCH.headerX", "\"Siri Suggestions\" left", "Search", 17f, 0f, 40f)
    val searchHeaderBaseline = dpToken("T-SEARCH.headerY", "Header baseline", "Search", 87.67f, 40f, 140f)
    val searchHeaderSize = tunable("T-SEARCH.headerSize", "Header size", "Search", 15f, 12f, 22f, "sp") // [MEASURED] "Siri Suggestions" = 114 pt wide
    val searchCardSide = dpToken("T-SEARCH.cardSide", "Card side", "Search", 15.67f, 0f, 40f)
    val searchCardTop = dpToken("T-SEARCH.cardTop", "Card top", "Search", 98.33f, 60f, 160f)
    val searchCardHeight = dpToken("T-SEARCH.cardH", "Card height", "Search", 113.67f, 80f, 160f)
    val searchCardCorner = dpToken("T-SEARCH.cardR", "Card corner radius", "Search", 38f, 10f, 60f) // [ESTIMATED]
    val searchCardFill = Color(0x1AFFFFFF) // measured: card ≈ +18 brightness levels above the background
    val searchFieldSide = dpToken("T-SEARCH.fieldSide", "Search field side", "Search", 7.33f, 0f, 30f)
    val searchFieldHeight = dpToken("T-SEARCH.fieldH", "Search field height", "Search", 46.67f, 30f, 60f)
    val searchFieldGap = dpToken("T-SEARCH.fieldGap", "Search field above keyboard", "Search", 9.33f, 0f, 30f)
    val searchFieldTint = tunable("T-SEARCH.fieldTint", "Search field brighten", "Search", 0.18f, 0f, 0.8f) // [MEASURED] IMG_3131: field ≈ 105 vs. surroundings ≈ 63 brightness
    val searchFieldFontSize = tunable("T-SEARCH.fieldFont", "Search field font", "Search", 20f, 14f, 26f, "sp") // [ESTIMATED] from IMG_3131
    val searchFieldPadding = dpToken("T-SEARCH.fieldPad", "Search field padding", "Search", 16f, 4f, 30f) // [ESTIMATED]
    val searchFieldIcon = dpToken("T-SEARCH.fieldIcon", "Search field magnifier", "Search", 17f, 10f, 30f) // [MEASURED roughly] IMG_3131
    val searchFieldIconGap = dpToken("T-SEARCH.fieldIconGap", "Magnifier → text", "Search", 10f, 0f, 24f) // [ESTIMATED]
    val searchLabelGap = dpToken("T-SEARCH.labelGap", "Suggestion: icon → label", "Search", 5f, 0f, 16f) // [ESTIMATED]
    val searchMicSize = dpToken("T-SEARCH.mic", "Microphone glyph", "Search", 19f, 8f, 30f) // [MEASURED roughly] IMG_3131 ≈ 19 pt tall
    val searchGlyphColor = Color(0xF2FFFFFF) // magnifier + microphone in the field white (IMG_3131)
    val searchTextColor = Color.White
    val searchPlaceholderColor = Color(0x99FFFFFF)

    // ── App Library (T-LIB) [MEASURED] recording2 (still frames 3x, rest position t = 9.9 s) ─────
    val libSearchTop = dpToken("T-LIB.searchTop", "App Library search field top", "App Library", 90f, 20f, 140f)
    val libSearchHeight = dpToken("T-LIB.searchH", "App Library search field height", "App Library", 47.33f, 30f, 60f)
    val libSearchSide = dpToken("T-LIB.searchSide", "App Library search field side", "App Library", 26f, 0f, 48f)
    val libSearchIcon = dpToken("T-LIB.searchIcon", "App Library search field magnifier", "App Library", 18f, 10f, 30f)
    val libSearchIconGap = dpToken("T-LIB.searchIconGap", "Magnifier → text", "App Library", 10.5f, 0f, 24f)
    val libSearchFont = tunable("T-LIB.searchFont", "Search field font", "App Library", 17f, 12f, 22f, "sp") // "App Library" ≈ 100 pt wide
    val libSearchTint = tunable("T-LIB.searchTint", "Search field brighten", "App Library", 0.32f, 0f, 0.8f) // field ≈ +30 brightness above the surroundings
    val libSearchTextColorOnLight = Color(0xFF3C302E) // darkest glyph pixels
    val libSearchTextColor: Color get() = if (wallpaperIsDark) Color(0xE6FFFFFF) else libSearchTextColorOnLight
    val libSide = dpToken("T-LIB.side", "App Library side margin", "App Library", 25.67f, 8f, 48f)
    val libGridTop = dpToken("T-LIB.gridTop", "First tile row top", "App Library", 168.67f, 100f, 240f)
    val libGap = dpToken("T-LIB.gap", "Tile gap horizontal", "App Library", 20f, 8f, 40f)
    val libRowPitch = dpToken("T-LIB.rowPitch", "Tile row pitch", "App Library", 200.67f, 150f, 260f)
    val libTileCorner = dpToken("T-LIB.tileR", "Tile corner radius", "App Library", 27.75f, 10f, 50f) // circle fit ≈ 29 pt, continuous
    val libTilePadding = dpToken("T-LIB.tilePad", "Tile padding", "App Library", 12.67f, 4f, 24f)
    val libTileIconGap = dpToken("T-LIB.iconGap", "Gap large icons", "App Library", 12f, 4f, 24f) // large icons = 64 pt
    val libMini = dpToken("T-LIB.mini", "Mini icon size", "App Library", 25.67f, 12f, 32f)
    val libMiniGap = dpToken("T-LIB.miniGap", "Mini icon gap", "App Library", 6f, 0f, 16f)
    val libTileTint = tunable("T-LIB.tint", "Tile glass brighten", "App Library", 0.05f, 0f, 0.6f) // tile ≈ +5 brightness
    val libLabelGap = dpToken("T-LIB.labelGap", "Tile → label baseline", "App Library", 16.67f, 8f, 40f)
    val libRubberBand = tunable("T-LIB.rubber", "Overscroll limit (× screen height)", "App Library", 1f, 0.2f, 2f)
    val libEdgeBlur = dpToken("T-LIB.edgeBlur", "Top edge: blur", "App Library", 10f, 0f, 30f) // scroll edge: tiles above the field blurred
    val libEdgeEnd = dpToken("T-LIB.edgeEnd", "Top edge: blur until", "App Library", 120f, 40f, 200f)
    // List (A–Z) [MEASURED] t = 12.4 s
    val libListFieldRight = dpToken("T-LIB-LIST.fieldRight", "Search field right (list)", "App Library", 302.33f, 200f, 380f)
    val libListFieldInset = dpToken("T-LIB-LIST.fieldInset", "Magnifier left (list)", "App Library", 18.67f, 4f, 40f)
    val libCancelX = dpToken("T-LIB-LIST.cancelX", "\"Cancel\" left", "App Library", 323f, 280f, 380f)
    val libListTop = dpToken("T-LIB-LIST.top", "List content top", "App Library", 150f, 100f, 220f)
    val libListHeader = dpToken("T-LIB-LIST.header", "Letter header height", "App Library", 40f, 20f, 60f)
    val libListHeaderBaseline = dpToken("T-LIB-LIST.headerBase", "Header baseline", "App Library", 32f, 10f, 50f)
    val libListHeaderFont = tunable("T-LIB-LIST.headerFont", "Header font", "App Library", 16f, 12f, 22f, "sp")
    val libListRow = dpToken("T-LIB-LIST.row", "Row height", "App Library", 72f, 40f, 100f)
    val libListIcon = dpToken("T-LIB-LIST.icon", "Row icon", "App Library", 48f, 24f, 64f)
    val libListSide = dpToken("T-LIB-LIST.side", "Rows left", "App Library", 26.33f, 0f, 48f)
    val libListTextX = dpToken("T-LIB-LIST.textX", "Name left", "App Library", 91f, 60f, 120f)
    val libListSepRight = dpToken("T-LIB-LIST.sepRight", "Separator until", "App Library", 371f, 300f, 402f)
    val libListFont = tunable("T-LIB-LIST.font", "Name font", "App Library", 17f, 12f, 22f, "sp")
    val libIndexX = dpToken("T-LIB-LIST.indexX", "Index centre", "App Library", 394.33f, 360f, 402f)
    val libIndexTop = dpToken("T-LIB-LIST.indexTop", "Index top", "App Library", 180f, 100f, 260f)
    val libIndexPitch = dpToken("T-LIB-LIST.indexPitch", "Index line pitch", "App Library", 13.6f, 8f, 20f)
    val libIndexFont = tunable("T-LIB-LIST.indexFont", "Index font", "App Library", 11f, 8f, 14f, "sp")
    val libListBlur = dpToken("T-LIB-LIST.blur", "Tiles behind blur", "App Library", 24f, 0f, 60f) // tiles stay visible as colour patches
    val libListDim = tunable("T-LIB-LIST.dim", "Dimming (list)", "App Library", 0.34f, 0f, 0.8f) // brightness 171 → 113
    val libListSeparator = Color(0x40FFFFFF)
    val libIndexColor = Color(0xE6FFFFFF) // index white (recording2 12.4 s)
    val libListPlaceholder = Color(0x99FFFFFF)
    val libListFieldTint = tunable("T-LIB-LIST.fieldTint", "Search field brighten (list)", "App Library", 0.18f, 0f, 0.8f) // field 156 vs. 130
    // Category expanded [MEASURED] t = 18.2 s: no panel, grid = home grid
    val libCatTitleX = dpToken("T-LIB-CAT.titleX", "Category title left", "App Library", 30f, 0f, 60f)
    val libCatTitleBaseline = dpToken("T-LIB-CAT.titleBase", "Category title baseline", "App Library", 325.67f, 200f, 400f)
    val libCatGridTop = dpToken("T-LIB-CAT.gridTop", "Category grid top", "App Library", 371f, 250f, 450f)
    val libCatBlur = dpToken("T-LIB-CAT.blur", "Category: background blur", "App Library", 24f, 0f, 60f)

    // ── Widgets (T-WIDGET) [MEASURED] recording6 0.2 s (weather + calendar, small) ─────────────
    val widgetSmall = dpToken("T-WIDGET.small", "Small widget (edge)", "Widgets", 164f, 120f, 200f) // 2×2
    val widgetWide = dpToken("T-WIDGET.wide", "Medium/large widget width", "Widgets", 349.33f, 300f, 402f) // 4 columns
    val widgetOverhang = dpToken("T-WIDGET.overhang", "Widget overhangs the icon edges", "Widgets", 3.67f, 0f, 10f) // left 26.33 instead of 30
    val widgetTopOffset = dpToken("T-WIDGET.top", "Widget top from icon top", "Widgets", 0.67f, -5f, 10f)
    val widgetCorner = dpToken("T-WIDGET.r", "Widget corner radius (continuous)", "Widgets", 22f, 10f, 40f) // curve ≈ 27 pt long
    val widgetLabelGap = dpToken("T-WIDGET.labelGap", "Widget label baseline below the widget", "Widgets", 16.33f, 8f, 30f) // [ESTIMATED]
    val widgetGlyph = Color.White
    // Today view (T-TODAY) [MEASURED] recording8 12.5 s
    val todayTop = dpToken("T-TODAY.top", "Today: first widget top", "Widgets", 91.67f, 40f, 160f)
    val todaySide = dpToken("T-TODAY.side", "Today: side margin", "Widgets", 26.33f, 0f, 48f)
    val todayGap = dpToken("T-TODAY.gap", "Today: gap between widgets", "Widgets", 21.33f, 4f, 40f)
    val todayBottom = dpToken("T-TODAY.bottom", "Today: space at the bottom", "Widgets", 40f, 0f, 200f)
    val todayBlur = dpToken("T-TODAY.blur", "Today: wallpaper blur", "Widgets", 30f, 0f, 60f) // visual comparison: colour patches only
    val widgetResizeBlur = dpToken("T-WIDGET.resizeBlur", "Content blur while resizing", "Widgets", 14f, 0f, 40f) // [ESTIMATED] visual comparison
    val widgetHandle = dpToken("T-WIDGET.handle", "Resize handle (arc) radius", "Widgets", 15f, 6f, 30f) // recording8 39.6 s
    val widgetHandleHit = dpToken("T-WIDGET.handleHit", "Resize handle hit radius", "Widgets", 26f, 10f, 50f)

    // App open (only with the Animation Module): corner radius of the card on screen [MEASURED] recording9 172–176
    // (circle fit ≈ 60–75 pt incl. Apple's continuous corner; stays constant during the whole motion)
    val appLaunchCorner = dpToken("T-APP-LAUNCH.r", "App open: card corner radius", "Widgets", 62f, 0f, 100f)

    // ── Context menu (T-CTX) [MEASURED] recording4 2.3 s (Phone in the dock, menu above) ─────────
    val ctxWidth = dpToken("T-CTX.w", "Menu width", "Context menu", 250.67f, 180f, 320f)
    val ctxCorner = dpToken("T-CTX.r", "Menu corner radius", "Context menu", 30f, 10f, 50f) // [ESTIMATED] curve ≈ 38 pt long, continuous
    val ctxRow = dpToken("T-CTX.row", "Row height", "Context menu", 42f, 30f, 60f)
    val ctxLine = dpToken("T-CTX.line", "Extra height per additional text line", "Context menu", 19.33f, 14f, 26f)
    val ctxPadV = dpToken("T-CTX.padV", "Padding top/bottom", "Context menu", 10f, 0f, 24f)
    val ctxGlyphX = dpToken("T-CTX.glyphX", "Glyph centre from left", "Context menu", 40.67f, 16f, 60f)
    val ctxGlyph = dpToken("T-CTX.glyph", "Glyph size", "Context menu", 16.33f, 10f, 26f)
    val ctxTextX = dpToken("T-CTX.textX", "Text left", "Context menu", 64.67f, 30f, 90f)
    val ctxTextRight = dpToken("T-CTX.textRight", "Text right margin", "Context menu", 20f, 4f, 40f) // [ESTIMATED] "View Most Recent Call" wraps
    val ctxFont = tunable("T-CTX.font", "Font", "Context menu", 17f, 12f, 22f, "sp")
    val ctxGap = dpToken("T-CTX.gap", "Gap menu ↔ lifted icon", "Context menu", 9f, 0f, 30f)
    val ctxSide = dpToken("T-CTX.side", "Minimum distance to screen edge", "Context menu", 16f, 0f, 40f)
    val ctxDim = tunable("T-CTX.dim", "Dim background", "Context menu", 0.52f, 0f, 0.9f) // brightness 204 → 98
    val ctxBlur = dpToken("T-CTX.blur", "Menu glass blur", "Context menu", 20f, 0f, 40f) // recording4: background behind the menu strongly blurred
    val ctxTint = tunable("T-CTX.tint", "Menu glass brighten", "Context menu", 0.05f, 0f, 0.5f) // menu ≈ +6 above the dimmed background
    val editMenuTint = tunable("T-CTX.editTint", "Edit menu darken", "Context menu", 0.24f, 0f, 0.8f) // recording6 4.9 s: 145 instead of 188 over a light wallpaper
    val ctxPressedFill = Color(0x1FFFFFFF) // row pressed [ESTIMATED]
    val ctxDestructive = Color(0xFFFC7075) // "Remove App" measured
    val ctxText = Color(0xFFFDFAFB)

    // ── Delete dialog (T-ALERT) [ESTIMATED] iOS 26/27 alert style ─────────────────────
    val alertWidth = dpToken("T-ALERT.w", "Dialog width", "Dialog", 300f, 240f, 360f)
    val alertCorner = dpToken("T-ALERT.r", "Dialog corner radius", "Dialog", 34f, 10f, 50f)
    val alertPadding = dpToken("T-ALERT.pad", "Dialog padding", "Dialog", 20f, 8f, 32f)
    val alertButtonHeight = dpToken("T-ALERT.btnH", "Dialog button height", "Dialog", 48f, 36f, 60f)
    val alertButtonGap = dpToken("T-ALERT.gap", "Dialog button gap", "Dialog", 8f, 0f, 16f)
    val alertTitleSize = tunable("T-ALERT.title", "Dialog title size", "Dialog", 17f, 12f, 22f, "sp")
    val alertBodySize = tunable("T-ALERT.body", "Dialog text size", "Dialog", 14f, 10f, 18f, "sp")
    val alertScrim = tunable("T-ALERT.scrim", "Dialog dimming", "Dialog", 0.2f, 0f, 0.6f)
    val alertTint = tunable("T-ALERT.tint", "Dialog glass brighten", "Dialog", 0.55f, 0f, 1f)
    val alertBlur = dpToken("T-ALERT.blur", "Dialog background blur", "Dialog", 20f, 0f, 40f)
    val alertButtonFill = Color(0x1F000000)
    val destructive = Color(0xFFFF3B30)
    val alertText = Color(0xFF1C1C1E)
    val alertSecondaryText = Color(0xFF3C3C43)

    // ── Wallpaper editor (T-WPE) [MEASURED] recording6 7.7 / 17.5 s ─────────────────────────
    val wpePreviewX = dpToken("T-WPE.previewX", "Preview left", "Wallpaper", 60.33f, 20f, 100f)
    val wpePreviewY = dpToken("T-WPE.previewY", "Preview top", "Wallpaper", 131.33f, 60f, 200f)
    val wpePreviewScale = tunable("T-WPE.previewScale", "Preview scale", "Wallpaper", 0.699f, 0.4f, 1f, "×") // 281 / 402
    val wpePreviewCorner = dpToken("T-WPE.previewR", "Preview corner radius", "Wallpaper", 38f, 0f, 60f) // [ESTIMATED] ≈ display corner × 0.7
    val wpeTopY = dpToken("T-WPE.topY", "Cancel/Done centre y", "Wallpaper", 32f, 10f, 60f)
    val wpeTopSide = dpToken("T-WPE.topSide", "Cancel/Done side", "Wallpaper", 39.33f, 0f, 60f) // capsule edge (centre 73 / 329 pt)
    val wpeButtonW = dpToken("T-WPE.btnW", "Cancel/Done width", "Wallpaper", 67.33f, 40f, 100f) // both equally wide
    val wpeButtonH = dpToken("T-WPE.btnH", "Cancel/Done height", "Wallpaper", 27.33f, 20f, 40f) // = T-EDIT-BTN
    val wpeButtonPad = dpToken("T-WPE.btnPad", "Cancel/Done padding", "Wallpaper", 11f, 4f, 24f)
    val wpeButtonFont = tunable("T-WPE.btnFont", "Cancel/Done font", "Wallpaper", 17f, 11f, 20f, "sp") // cap height ≈ 12.7 pt
    val wpeBarY = dpToken("T-WPE.barY", "Circles centre y", "Wallpaper", 797f, 700f, 860f)
    val wpeBarFirstX = dpToken("T-WPE.barX", "first circle centre x", "Wallpaper", 69f, 20f, 120f)
    val wpeBarPitch = dpToken("T-WPE.barPitch", "Circle pitch", "Wallpaper", 60.5f, 40f, 80f)
    val wpeBarDividerX = dpToken("T-WPE.dividerX", "Divider x", "Wallpaper", 291f, 250f, 330f)
    val wpeBarBlurX = dpToken("T-WPE.blurX", "Blur centre x", "Wallpaper", 332f, 290f, 380f)
    val wpeCircle = dpToken("T-WPE.circle", "Circle diameter", "Wallpaper", 40f, 28f, 56f)
    val wpeLabelY = dpToken("T-WPE.labelY", "Caption baseline", "Wallpaper", 833f, 800f, 870f)
    val wpeLabelFont = tunable("T-WPE.labelFont", "Caption font", "Wallpaper", 12f, 9f, 16f, "sp")
    val wpeSheetTop = dpToken("T-WPE-SHEET.top", "Colour sheet top", "Wallpaper", 539.67f, 400f, 700f)
    val wpeSheetSide = dpToken("T-WPE-SHEET.side", "Colour sheet side", "Wallpaper", 6f, 0f, 30f)
    val wpeSheetCorner = dpToken("T-WPE-SHEET.r", "Colour sheet corner radius", "Wallpaper", 40f, 10f, 60f) // [ESTIMATED]
    val wpeSheetTitleY = dpToken("T-WPE-SHEET.titleY", "Title centre y (from sheet)", "Wallpaper", 36.33f, 10f, 60f)
    val wpeSwatchX = dpToken("T-WPE-SHEET.swatchX", "first colour centre x", "Wallpaper", 57.9f, 20f, 90f)
    val wpeSwatchPitch = dpToken("T-WPE-SHEET.pitch", "Colour grid pitch", "Wallpaper", 57.5f, 40f, 70f)
    val wpeSwatchTop = dpToken("T-WPE-SHEET.swatchY", "first row centre y (from sheet)", "Wallpaper", 95.33f, 60f, 140f)
    val wpeSwatch = dpToken("T-WPE-SHEET.swatch", "Colour circle diameter", "Wallpaper", 33f, 20f, 44f)
    val wpeSliderY = dpToken("T-WPE-SHEET.sliderY", "Slider centre y (from sheet)", "Wallpaper", 270.33f, 200f, 320f)
    val wpeSliderX = dpToken("T-WPE-SHEET.sliderX", "Slider left", "Wallpaper", 41.8f, 10f, 80f)
    val wpeSliderH = dpToken("T-WPE-SHEET.sliderH", "Slider track height", "Wallpaper", 14.5f, 4f, 24f)
    val wpeThumb = dpToken("T-WPE-SHEET.thumb", "Slider thumb", "Wallpaper", 32f, 20f, 44f)
    /** 18 colours from the iOS colour sheet, sampled (recording6 17.5 s). */
    val wallpaperSwatches = intArrayOf(
        0xFF47D3FE.toInt(), 0xFF6BA2FE.toInt(), 0xFFB185FD.toInt(), 0xFFE14FFE.toInt(), 0xFFFF6BA0.toInt(), 0xFFFE8B7F.toInt(),
        0xFFFF9E73.toInt(), 0xFFFEC66F.toInt(), 0xFFFEE370.toInt(), 0xFFFFFE91.toInt(), 0xFFFFFE8D.toInt(), 0xFFBBF48B.toInt(),
        0xFFACC0A4.toInt(), 0xFF9BB8C0.toInt(), 0xFFC0A9BF.toInt(), 0xFFC9BDB0.toInt(), 0xFFFFFFFF.toInt(), 0xFF000000.toInt(),
    )

    // ── Tinted icon style, Light variant (T-TINT) [MEASURED] user iPhone 10-16 (iOS 27, hue 315, saturation slider at max):
    // icons AND widgets get the same opaque plate hsv(h, 0.69, 0.54 → 0.51 top to bottom); icon glyphs almost white with
    // a hint of the hue (S 0.07–0.21, V 0.85–1.0); widget text white. Saturations scale with the saturation slider.
    const val TINT_PLATE_S = 0.69f
    const val TINT_PLATE_V_TOP = 0.54f
    const val TINT_PLATE_V_BOTTOM = 0.51f
    const val TINT_GLYPH_S = 0.12f
    const val TINT_GLYPH_V = 0.95f
    /** Widgets: content brightness → plate…glyph; × this so light content (≈ 0.85) already reaches the glyph colour. */
    const val TINT_WIDGET_GAIN = 1.15f
    /** Icons: motif brightness gain and minimum lift towards the glyph colour (iOS glyphs are clearly lighter than the
     *  plate everywhere, mostly near white) [DERIVED from the side-by-side comparison with the user's iPhone 10-16]. */
    const val TINT_ICON_GAIN = 1.3f
    const val TINT_ICON_FLOOR = 0.35f

    // ── Wallpaper (own generated gradient; colours sampled from IMG_3123) ───
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
