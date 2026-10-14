package io.github.janhurn9400.liquidy.ui.home

import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import io.github.janhurn9400.liquidy.data.AppEntry
import io.github.janhurn9400.liquidy.data.SavedFolder
import io.github.janhurn9400.liquidy.design.ContinuousRoundedShape
import io.github.janhurn9400.liquidy.design.DesignTokens
import io.github.janhurn9400.liquidy.glass.LiquidGlass
import io.github.janhurn9400.liquidy.glass.glassRect
import io.github.janhurn9400.liquidy.glass.GlassSurface
import io.github.janhurn9400.liquidy.motion.Motion
import io.github.janhurn9400.liquidy.ui.Inter
import kotlin.math.roundToInt

/** Ordner-Icon (T-FOLDER-ICON): Glas-Squircle mit 3×3 Mini-Icons. */
@Composable
fun FolderIconView(
    id: String,
    folder: SavedFolder,
    apps: Map<String, AppEntry>,
    showLabel: Boolean,
    wallpaper: ImageBitmap,
    pressAlpha: () -> Float,
    jiggle: () -> Float,
    clockSeconds: () -> Float,
    mergeHint: Boolean,
    modifier: Modifier = Modifier,
    /** Gezogenes Icon (liegt über der Glasquelle): bricht live die Icons darunter statt nur das Wallpaper. */
    liveBackdrop: Boolean = false,
    /** App, die gerade in den Ordner fliegt: ihr Mini-Platz bleibt leer, bis sie gelandet ist (M-MERGE-DROP). */
    hiddenApp: String? = null,
) {
    val phases = androidx.compose.runtime.remember(id) { jigglePhases(id) }
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    // Ordner-Hinweis: Name blendet aus, solange ein Icon darüber gehalten wird [GEMESSEN Nutzer-iPhone 10-14, 10,62 s]
    val labelShown = androidx.compose.runtime.remember(id) { Animatable(1f) }
    androidx.compose.runtime.LaunchedEffect(mergeHint) { labelShown.animateTo(if (mergeHint) 0f else 1f, Motion.mergeHint.spec()) }
    IconCell(
        phaseKey = id,
        labelAlpha = { labelShown.value },
        label = if (showLabel) folder.name else null,
        wallpaper = wallpaper,
        pressAlpha = pressAlpha,
        jiggle = jiggle,
        clockSeconds = clockSeconds,
        badgeScale = { 0f },
        showBadge = false,
        clipIcon = false,
        modifier = modifier,
    ) {
        // Ordner-Hinweis [GEMESSEN Nutzer-iPhone 10-14]: das Ordner-Glas selbst wächst auf T-MERGE.scale und dunkelt ab,
        // die Mini-Icons bleiben stehen
        val hint = androidx.compose.runtime.remember(id) { Animatable(0f) }
        androidx.compose.runtime.LaunchedEffect(mergeHint) { hint.animateTo(if (mergeHint) 1f else 0f, Motion.mergeHint.spec()) }
        // Dasselbe Liquid Glass wie das Dock (Linse, Licht, Tönung) — Quelle nur das Wallpaper, weil das Icon selbst
        // in der aufgenommenen Icon-Ebene liegt; beim Ziehen liegt es darüber → echte Quelle (Wallpaper + Icons)
        val wb = io.github.janhurn9400.liquidy.glass.LocalWallpaperBlur.current
        val live = io.github.janhurn9400.liquidy.glass.LocalGlassBackdrop.current
        val backdrop = androidx.compose.runtime.remember(wallpaper, wb, liveBackdrop, live) {
            if (liveBackdrop && live != null) live else io.github.janhurn9400.liquidy.glass.WallpaperBackdrop(wallpaper, wb.blurred, wb.amount)
        }
        androidx.compose.runtime.CompositionLocalProvider(io.github.janhurn9400.liquidy.glass.LocalGlassBackdrop provides backdrop) {
            androidx.compose.foundation.layout.Box(Modifier.size(DesignTokens.iconSize.dp).graphicsLayer {
                // Wackeln lesen → Glas zeichnet in jedem Frame neu (Hintergrund bleibt stehen)
                jiggleValues(jiggle(), clockSeconds(), phases, density)
            }) {
                io.github.janhurn9400.liquidy.glass.LiquidGlass(
                    shape = io.github.janhurn9400.liquidy.glass.glassRect(DesignTokens.iconCorner.dp),
                    blackTint = DesignTokens.glassTint.value + DesignTokens.folderMergeDark.value * hint.value,
                    modifier = Modifier.mergeGrow { 1f + (DesignTokens.folderMergeScale.value - 1f) * hint.value },
                )
                // Schlüssel mitgeben (Mehrfach-Icons haben eigene Schlüssel) — der fliegende Platz bleibt leer
                MiniGrid(folder.apps.filter { apps[it] != null }.map { k -> if (k == hiddenApp) null else apps[k] })
            }
        }
    }
}

/** 3×3 Mini-Icons, Maße gemessen (Innenrand 10 pt, Mini 13 pt, Abstand 16 pt). */
@Composable
private fun MiniGrid(apps: List<AppEntry?>) {
    val mini = DesignTokens.folderMiniSize.dp
    // Mini-Icons haben dieselben Eckenproportionen wie große Icons
    val miniShape = ContinuousRoundedShape(DesignTokens.iconCorner.dp * (DesignTokens.folderMiniSize.value / DesignTokens.iconSize.value))
    apps.take(DesignTokens.FOLDER_COLUMNS * DesignTokens.FOLDER_ROWS).forEachIndexed { i, app ->
        if (app == null) return@forEachIndexed // fliegt gerade hinein bzw. unbekannt: Platz bleibt frei
        val col = i % DesignTokens.FOLDER_COLUMNS
        val row = i / DesignTokens.FOLDER_COLUMNS
        StyledAppIcon(
            app = app,
            modifier = Modifier
                .offset(
                    x = DesignTokens.folderMiniInset.dp + DesignTokens.folderMiniPitch.dp * col,
                    y = DesignTokens.folderMiniInset.dp + DesignTokens.folderMiniPitch.dp * row,
                )
                .size(mini)
                .clip(miniShape),
        )
    }
}

/**
 * App über App gehalten: hinter dem Ziel erscheint das Ordner-Glas (wie beim Ordner-Hinweis: wächst auf T-MERGE.scale,
 * abgedunkelt) — sieht aus wie der Ordner, der gleich entsteht.
 */
@Composable
fun MergeHintBackdrop(active: Boolean, wallpaper: ImageBitmap) {
    val hint = remember { Animatable(0f) }
    LaunchedEffect(active) { hint.animateTo(if (active) 1f else 0f, Motion.mergeHint.spec()) }
    if (hint.value <= 0.01f) return
    val wb = io.github.janhurn9400.liquidy.glass.LocalWallpaperBlur.current
    val backdrop = remember(wallpaper, wb) { io.github.janhurn9400.liquidy.glass.WallpaperBackdrop(wallpaper, wb.blurred, wb.amount) }
    androidx.compose.runtime.CompositionLocalProvider(io.github.janhurn9400.liquidy.glass.LocalGlassBackdrop provides backdrop) {
        io.github.janhurn9400.liquidy.glass.LiquidGlass(
            shape = io.github.janhurn9400.liquidy.glass.glassRect(DesignTokens.iconCorner.dp),
            blackTint = DesignTokens.glassTint.value + DesignTokens.folderMergeDark.value * hint.value,
            modifier = Modifier.size(DesignTokens.iconSize.dp).mergeGrow { DesignTokens.folderMergeScale.value * hint.value },
        )
    }
}

/** Glas auf [scale] × Icongröße um die Mitte — über die echte Größe (Layout), nicht graphicsLayer: sonst würde der gebrochene
 *  Hintergrund mitvergrößert (docs/GLASS.md). */
private fun Modifier.mergeGrow(scale: () -> Float): Modifier = this.layout { m, c ->
    val base = c.maxWidth
    val px = (base * scale()).roundToInt().coerceAtLeast(1)
    val p = m.measure(androidx.compose.ui.unit.Constraints.fixed(px, px))
    layout(base, base) { p.place((base - px) / 2, (base - px) / 2) }
}

// ── Geöffneter Ordner ───────────────────────────────────────────────────────────

/** Geometrie des offenen Ordners in Bildschirm-px (Panel-Rechteck, Icon-Positionen, Titel). */
class FolderGeometry(
    private val density: Float,
    private val width: Float,
    private val height: Float,
    private val home: HomeGeometry? = null,
    /** x des Ordner-Icons (px): aufgeklappt öffnet der Ordner über der Hälfte, in der er liegt. */
    private val iconX: Float? = null,
) {
    private fun px(v: Float) = v * density
    /** Duo aufgeklappt [GEMESSEN 19:02 (Video 1), 4,1 s]: 290 × 345 pt, mittig über dem Raster seiner Hälfte, oben 160 pt. */
    private val duoOpen get() = home?.unfolded == true
    val panel: Rect
        get() {
            if (duoOpen) {
                val w = px(DUO_FOLDER_W); val h = px(DUO_FOLDER_H)
                val cx = home!!.duoFolderCenterX(iconX ?: width)
                // Oberkante wie am Duo (160 pt), aber nie so tief, dass das Panel unten hinausragt; seitlich im Bild halten
                val top = minOf(px(DUO_FOLDER_TOP), height - h - px(DUO_FOLDER_MIN_BOTTOM)).coerceAtLeast(px(DUO_FOLDER_MIN_TOP))
                val left = (cx - w / 2).coerceIn(px(8f), width - w - px(8f))
                return Rect(Offset(left, top), Size(w, h))
            }
            val w = px(DesignTokens.folderPanelWidth.value)
            val h = px(DesignTokens.folderPanelHeight.value)
            return Rect(Offset((width - w) / 2, (height - h) / 2), Size(w, h))
        }

    /** Icon-Oberkante-links im Panel (panel-lokal). */
    fun slot(i: Int): Offset {
        val col = i % DesignTokens.FOLDER_COLUMNS
        val row = i / DesignTokens.FOLDER_COLUMNS
        if (duoOpen) return Offset(px(DUO_FOLDER_GRID_LEFT + home!!.duoColPitchPt * col), px(DUO_FOLDER_GRID_TOP + home.duoRowPitchPt * row))
        return Offset(
            px(DesignTokens.folderGridLeft.value + DesignTokens.gridColPitch.value * col),
            px(DesignTokens.folderGridTop.value + DesignTokens.folderRowPitch.value * row),
        )
    }

    /** Umbenennen-Kapsel im Bearbeiten-Modus: so breit wie das Panel, 19 pt darüber, 59 pt hoch [GEMESSEN Video 1, 4,1 s]. */
    val titleRect: Rect get() = Rect(Offset(panel.left, panel.top - px(NAME_GAP + NAME_H)), Size(panel.width, px(NAME_H)))

    /** Bereich rechts im Umbenennen-Feld, der zum ×-Knopf gehört (px): Rand + Knopf + etwas Spielraum. */
    val nameClearZone: Float get() = px(NAME_CLEAR_END + NAME_CLEAR + NAME_CLEAR_SLOP)

    /** Titel links: gleicher Abstand zur Panel-Kante wie beim iPhone (74,33 − 47,83 pt). */
    val titleLeft: Float get() = panel.left + px(DesignTokens.folderTitleX.value - (402f - DesignTokens.folderPanelWidth.value) / 2)

    /**
     * Rechteck von Glas + Inhalt bei Fortschritt p: vom Ordner-Icon-Quadrat zum Panel (Breite/Höhe je für sich) — bei p → 0
     * deckt es sich exakt mit dem Ordner-Icon, sonst sieht man beim (langsam auslaufenden) Schließen die Überblendung.
     */
    fun rect(p: Float, iconTopLeft: Offset, iconScale: Float = 1f): Rect {
        val icon = px(DesignTokens.iconSize.value)
        // sichtbares Ordner-Icon (im Duo-Dock kleiner, um die Mitte) — HomeGeometry.dockIconScale
        val s = icon * iconScale; val o = (icon - s) / 2
        val l0 = iconTopLeft.x + o; val t0 = iconTopLeft.y + o
        fun l(a: Float, b: Float) = a + (b - a) * p
        return Rect(l(l0, panel.left), l(t0, panel.top), l(l0 + s, panel.right), l(t0 + s, panel.bottom))
    }

    /** Mitte des App-Icons [i] (Index auf der Seite) relativ zu [rect] bei p: Mini-Platz im Ordner-Icon → Panel-Platz. */
    fun iconCenter(i: Int, p: Float, iconScale: Float = 1f): Offset {
        val col = i % DesignTokens.FOLDER_COLUMNS; val row = i / DesignTokens.FOLDER_COLUMNS
        val half = px(DesignTokens.folderMiniSize.value) / 2
        val mini = Offset(px(DesignTokens.folderMiniInset.value + DesignTokens.folderMiniPitch.value * col) + half,
            px(DesignTokens.folderMiniInset.value + DesignTokens.folderMiniPitch.value * row) + half) * iconScale
        val icon = px(DesignTokens.iconSize.value)
        val full = slot(i) + Offset(icon / 2, icon / 2)
        return mini + (full - mini) * p
    }

    /** Größe der App-Icons relativ zur vollen Größe bei p: Mini-Icon → volles Icon. */
    fun iconScale(p: Float, folderIconScale: Float = 1f): Float {
        val k = DesignTokens.folderMiniSize.value / DesignTokens.iconSize.value * folderIconScale
        return k + (1f - k) * p
    }
}

@Composable
fun FolderOverlay(
    model: HomeModel,
    open: OpenFolder,
    geo: FolderGeometry,
    density: Float,
    wallpaper: ImageBitmap,
    clockSeconds: () -> Float,
) {
    val folder = model.folderFor(open.id) ?: return
    val panel = geo.panel
    val apps = folder.apps.mapNotNull { model.apps[it] }

    // Bearbeiten-Modus: Titel als Glas-Kapsel mit Textfeld + Löschknopf (Umbenennen) [Video 1, 4,1 s]
    if (model.editing) FolderNameField(model, open, geo, density, folder.name)
    // Titel: groß, fett, weiß, linksbündig über dem Panel; blendet mit dem Fortschritt ein
    else Layout(
        content = {
            BasicText(
                folder.name,
                style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = DesignTokens.folderTitleSize.value.sp, color = Color.White),
                maxLines = 1,
            )
        },
        modifier = Modifier.graphicsLayer { alpha = open.progress.value.coerceIn(0f, 1f) },
    ) { m, c ->
        val t = m[0].measure(Constraints(maxWidth = c.maxWidth))
        layout(c.maxWidth, c.maxHeight) {
            val baseline = panel.top - DesignTokens.folderTitleBaselineGap.value * density
            t.place(
                geo.titleLeft.roundToInt(),
                (baseline - t[FirstBaseline]).roundToInt(),
            )
        }
    }

    // Glas: NICHT per graphicsLayer skalieren — sonst wird das gebrochene Hintergrundbild mitvergrößert und das Wallpaper
    // scheint mitzuwachsen. Stattdessen echte Größe/Position je Frame (Layout-Phase), das Glas zeigt immer, was dahinter liegt.
    // Eckradius wächst mit: Ordner-Icon-Ecke → Panel-Ecke (Größe ändert sich im selben Bild mit, Kyant übernimmt die Form
    // bei jeder Größenänderung — der Fallstrick „Form in der Form“ trat nur bei gleichbleibender Größe auf)
    val pr = open.progress.value.coerceIn(0f, 1f)
    val iconCorner = DesignTokens.iconCorner.value * open.iconScale // Ordner im Duo-Dock: kleiner (dockIconScale)
    val corner = iconCorner + (DesignTokens.folderPanelCorner.value - iconCorner) * pr
    fun Modifier.atRect() = this.layout { m, _ ->
        val r = geo.rect(open.progress.value, open.iconTopLeft, open.iconScale)
        val p = m.measure(androidx.compose.ui.unit.Constraints.fixed(r.width.roundToInt().coerceAtLeast(1), r.height.roundToInt().coerceAtLeast(1)))
        layout(p.width, p.height) { p.place(r.left.roundToInt(), r.top.roundToInt()) }
    }
    Box(Modifier.atRect().graphicsLayer { alpha = model.folderVisible(open) }) {
        LiquidGlass(
            shape = glassRect(Dp(corner)),
            modifier = Modifier.matchParentSize(),
            // Glas-Werte gleiten mit dem Fortschritt zum Ordner-Icon-Glas (LiquidGlass-Standard) — klein sieht das Panel
            // genauso aus wie das Icon, der Tausch am Ende des Schließens ist unsichtbar
            thickness = 1f + (DesignTokens.glassThicknessLarge.value - 1f) * pr,
            blur = (DesignTokens.glassBlur.value + (DesignTokens.folderPanelBlur.value - DesignTokens.glassBlur.value) * pr).dp,
            whiteTint = DesignTokens.glassWhiteTint.value + (DesignTokens.folderPanelTint.value - DesignTokens.glassWhiteTint.value) * pr,
            // klares Glas (IMG_3130: innen = außen): Quelle ist nicht abgedunkelt → dieselbe Abdunkelung als Tönung
            blackTint = DesignTokens.glassTint.value + (DesignTokens.folderBgDim.value - DesignTokens.glassTint.value) * pr,
        )
    }

    // Icons: aus dem Ordner-Icon heraus skaliert (M-FOLDER, Fortschritt 0…1)
    // Ordnerseiten: Icons werden am Panelrand abgeschnitten [GEMESSEN Nutzer-iPhone 10-14, 17 s]
    Box(Modifier.atRect().graphicsLayer { alpha = model.folderVisible(open); clip = true }) {
        // Seitenpunkte unten im Panel: je 9 Apps eine Seite, im Bearbeiten-Modus eine leere Seite mehr (iOS) [Video 1, 4,1 s]
        val perPage = DesignTokens.FOLDER_COLUMNS * DesignTokens.FOLDER_ROWS
        val pageCount = (apps.size + perPage - 1) / perPage + (if (model.editing) 1 else 0)
        if (pageCount > 1) androidx.compose.foundation.Canvas(Modifier.matchParentSize().graphicsLayer { alpha = open.progress.value.coerceIn(0f, 1f) }) {
            val r = FOLDER_DOT_D / 2 * density; val pitch = FOLDER_DOT_PITCH * density
            val cy = size.height - FOLDER_DOT_FROM_BOTTOM * density
            val x0 = size.width / 2 - pitch * (pageCount - 1) / 2
            val active = model.folderPage.value
            for (k in 0 until pageCount) {
                val on = (1f - kotlin.math.abs(k - active)).coerceIn(0f, 1f)
                drawCircle(Color.White.copy(alpha = 0.4f + 0.6f * on), r, Offset(x0 + k * pitch, cy))
            }
        }
        val iconPx = DesignTokens.iconSize.value * density
        apps.forEachIndexed { i, app ->
            val pageOfIcon = i / perPage
            // angehoben (wird im Ordner gezogen) → hängt als Kopie am Finger
            if (model.folderDrag?.key == folder.apps.getOrNull(i)) return@forEachIndexed
            // App wächst gerade daraus bzw. fliegt hinein → Platz leer (wie auf dem Homescreen)
            if (model.flyingKey == app.key || io.github.janhurn9400.liquidy.data.AppLaunchTransition.launchingKey.value == app.key) return@forEachIndexed
            AppIconView(
                app = app,
                showLabel = true,
                wallpaper = wallpaper,
                pressAlpha = { if (model.pressedKey == app.key) model.pressAlpha.value else 0f },
                jiggle = { model.jiggleAmount.value },
                clockSeconds = clockSeconds, // dieselbe Uhr wie der Homescreen — sonst stehen die Icons nur schräg still
                badgeScale = { model.badgeScale.value },
                showBadge = model.editing || model.badgeScale.value > 0f, // Bearbeiten: Minus wie auf dem Homescreen
                badgeOnGlass = true, // Milchglas aus dem, was hinter dem Ordner liegt
                labelAlpha = { (open.progress.value / LABEL_FADE_UNTIL).coerceIn(0f, 1f) }, // Mini-Icons haben keine Namen
                modifier = Modifier
                    // volle Zellengröße, auch wenn der Behälter beim Schrumpfen schmaler ist als eine Zelle — sonst zwängt
                    // Compose die Zelle hinein und verschiebt sie um die halbe Differenz (App rutschte aus dem Glas)
                    .wrapContentSize(androidx.compose.ui.Alignment.TopStart, unbounded = true)
                    .offset {
                        val pr = open.progress.value
                        val c = geo.iconCenter(i % perPage, pr, open.iconScale)
                        // Seite verschoben um ganze (aktuelle) Breiten (Blättern liest den Wert erst im Layout)
                        val dx = (pageOfIcon - model.folderPage.value) * geo.rect(pr, open.iconTopLeft, open.iconScale).width
                        IntOffset(cellOffsetX(c.x - iconPx / 2 + dx, density).roundToInt(), (c.y - iconPx / 2).roundToInt())
                    }
                    .graphicsLayer {
                        val k = geo.iconScale(open.progress.value, open.iconScale)
                        scaleX = k; scaleY = k
                        transformOrigin = TransformOrigin(0.5f, iconPx / 2 / size.height)
                    },
            )
        }
    }
}

/** App, die im offenen Ordner gezogen wird: am Finger, leicht angehoben (wie das Ziehen auf dem Homescreen). */
@Composable
fun FolderDragCopy(model: HomeModel, density: Float, wallpaper: ImageBitmap) {
    val fd = model.folderDrag ?: return
    val app = model.apps[fd.key] ?: return
    val lift = androidx.compose.runtime.remember(fd) { Animatable(1f) }
    androidx.compose.runtime.LaunchedEffect(fd) { lift.animateTo(DesignTokens.dragScale.value, Motion.dragLift.spec()) }
    androidx.compose.foundation.layout.Box(
        Modifier
            .offset { val tl = fd.pointer - fd.grab; IntOffset(cellOffsetX(tl.x, density).roundToInt(), tl.y.roundToInt()) }
            .graphicsLayer { scaleX = lift.value; scaleY = lift.value; transformOrigin = TransformOrigin(0.5f, 0.5f) }
    ) {
        AppIconView(app = app, showLabel = false, wallpaper = wallpaper, pressAlpha = { 0f }, jiggle = { 0f },
            clockSeconds = { 0f }, badgeScale = { 0f }, showBadge = false)
    }
}

private const val LABEL_FADE_UNTIL = 0.35f // App-Namen im Panel sind unter diesem Fortschritt ausgeblendet
private const val DUO_FOLDER_W = 290f
private const val DUO_FOLDER_H = 345f
private const val DUO_FOLDER_TOP = 160f
private const val DUO_FOLDER_MIN_BOTTOM = 40f
private const val DUO_FOLDER_MIN_TOP = 100f // Platz für Titel/Umbenennen-Kapsel darüber
private const val DUO_FOLDER_GRID_LEFT = 26f // Icon-Oberkante-links (erste Mitte 56 pt vom Rand)
private const val DUO_FOLDER_GRID_TOP = 24f  // erste Reihe Mitte 54 pt unter der Oberkante
private const val NAME_H = 59f
private const val FOLDER_DOT_D = 7f
private const val FOLDER_DOT_PITCH = 15f
private const val FOLDER_DOT_FROM_BOTTOM = 17f // [GEMESSEN] Punkte 488 pt bei Panel-Unterkante 506
private const val NAME_TINT = 0.22f
private const val NAME_GAP = 19f
private const val NAME_FONT = 26f
private const val NAME_CLEAR = 24f
private const val NAME_CLEAR_END = 14f   // rechter Innenrand der Kapsel (wie das padding unten)
private const val NAME_CLEAR_SLOP = 12f
private const val NAME_PAD_START = 20f   // linker Innenrand der Kapsel bis zum Text

/** Umbenennen-Feld: Glas-Kapsel über dem Panel, links der Name (fett), rechts ein runder Löschknopf. */
@androidx.compose.runtime.Composable
private fun FolderNameField(model: HomeModel, open: OpenFolder, geo: FolderGeometry, density: Float, name: String) {
    val r = geo.titleRect
    Box(
        Modifier
            .offset { IntOffset(r.left.roundToInt(), r.top.roundToInt()) }
            .size(Dp(r.width / density), Dp(r.height / density))
            .graphicsLayer { alpha = open.progress.value.coerceIn(0f, 1f) }
    ) {
        // hell-milchig wie im Original (nicht dunkel/verzerrt): stark verwischt, aufgehellt, ohne Farbsaum
        LiquidGlass(shape = io.github.janhurn9400.liquidy.glass.glassCapsule(), modifier = Modifier.matchParentSize(),
            blur = DesignTokens.folderPanelBlur.dp, whiteTint = NAME_TINT, blackTint = 0f)
        androidx.compose.foundation.layout.Row(
            Modifier.matchParentSize().padding(start = NAME_PAD_START.dp, end = NAME_CLEAR_END.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            // Fokus kommt von der Gesten-Arena (sie liegt darüber): Tippen aufs Feld → folderRenaming
            val focus = androidx.compose.runtime.remember { androidx.compose.ui.focus.FocusRequester() }
            val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
            val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
            // eigener Text-Zustand mit Cursor; ändert sich der Name von außen (×-Knopf), Cursor ans Ende
            var field by androidx.compose.runtime.remember(open.id) {
                androidx.compose.runtime.mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(name, androidx.compose.ui.text.TextRange(name.length)))
            }
            if (field.text != name) field = androidx.compose.ui.text.input.TextFieldValue(name, androidx.compose.ui.text.TextRange(name.length))
            val textLayout = androidx.compose.runtime.remember { arrayOfNulls<androidx.compose.ui.text.TextLayoutResult>(1) }
            androidx.compose.runtime.LaunchedEffect(model.folderRenaming, model.folderRenameTap) {
                if (!model.folderRenaming) { focusManager.clearFocus(); keyboard?.hide(); return@LaunchedEffect }
                // Cursor dorthin, wo getippt wurde (Text beginnt nach dem linken Innenrand der Kapsel); hinter dem Text → Ende
                val tap = model.folderRenameTap
                val layout = textLayout[0]
                if (tap != null && layout != null) {
                    val local = Offset(tap.x - r.left - NAME_PAD_START * density, layout.size.height / 2f)
                    val at = layout.getOffsetForPosition(local).coerceIn(0, field.text.length)
                    field = field.copy(selection = androidx.compose.ui.text.TextRange(at))
                }
                focus.requestFocus(); keyboard?.show()
            }
            androidx.compose.runtime.DisposableEffect(Unit) { onDispose { model.folderRenaming = false; keyboard?.hide() } }
            androidx.compose.foundation.text.BasicTextField(
                value = field,
                onValueChange = { field = it; if (it.text != name) model.renameFolder(open.id, it.text) },
                onTextLayout = { textLayout[0] = it },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { model.folderRenaming = false }),
                textStyle = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = NAME_FONT.sp, color = Color.White),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.White),
                modifier = Modifier.weight(1f).focusRequester(focus),
            )
            androidx.compose.foundation.Canvas(
                Modifier.size(NAME_CLEAR.dp).clickable(
                    interactionSource = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                ) { model.renameFolder(open.id, "") }
            ) {
                val c = center; val rad = size.minDimension / 2
                drawCircle(Color.White.copy(alpha = 0.55f), rad, c)
                val a = rad * 0.38f; val sw = rad * 0.16f
                drawLine(Color(0xFF3A3A3C), c + Offset(-a, -a), c + Offset(a, a), sw, androidx.compose.ui.graphics.StrokeCap.Round)
                drawLine(Color(0xFF3A3A3C), c + Offset(a, -a), c + Offset(-a, a), sw, androidx.compose.ui.graphics.StrokeCap.Round)
            }
        }
    }
}
