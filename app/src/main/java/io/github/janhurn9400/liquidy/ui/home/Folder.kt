package io.github.janhurn9400.liquidy.ui.home

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
) {
    val phases = androidx.compose.runtime.remember(id) { jigglePhases(id) }
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    IconCell(
        phaseKey = id,
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
        MergeHintBackdrop(mergeHint, wallpaper)
        // Dasselbe Liquid Glass wie das Dock (Linse, Licht, Tönung) — Quelle nur das Wallpaper, weil das Icon selbst
        // in der aufgenommenen Icon-Ebene liegt
        val wb = io.github.janhurn9400.liquidy.glass.LocalWallpaperBlur.current
        val backdrop = androidx.compose.runtime.remember(wallpaper, wb) { io.github.janhurn9400.liquidy.glass.WallpaperBackdrop(wallpaper, wb.blurred, wb.amount) }
        androidx.compose.runtime.CompositionLocalProvider(io.github.janhurn9400.liquidy.glass.LocalGlassBackdrop provides backdrop) {
            io.github.janhurn9400.liquidy.glass.LiquidGlass(
                shape = io.github.janhurn9400.liquidy.glass.glassRect(DesignTokens.iconCorner.dp),
                modifier = Modifier.size(DesignTokens.iconSize.dp).graphicsLayer {
                    // Wackeln lesen → Glas zeichnet in jedem Frame neu (Hintergrund bleibt stehen)
                    jiggleValues(jiggle(), clockSeconds(), phases, density)
                },
            ) {
                MiniGrid(folder.apps.mapNotNull { apps[it] })
            }
        }
    }
}

/** 3×3 Mini-Icons, Maße gemessen (Innenrand 10 pt, Mini 13 pt, Abstand 16 pt). */
@Composable
private fun MiniGrid(apps: List<AppEntry>) {
    val mini = DesignTokens.folderMiniSize.dp
    // Mini-Icons haben dieselben Eckenproportionen wie große Icons
    val miniShape = ContinuousRoundedShape(DesignTokens.iconCorner.dp * (DesignTokens.folderMiniSize.value / DesignTokens.iconSize.value))
    apps.take(DesignTokens.FOLDER_COLUMNS * DesignTokens.FOLDER_ROWS).forEachIndexed { i, app ->
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

/** Glas-Quadrat hinter dem Ziel-Icon, wenn ein gezogenes Icon darüber gehalten wird (M-MERGE, T-MERGE). */
@Composable
fun MergeHintBackdrop(active: Boolean, wallpaper: ImageBitmap) {
    val scale = remember { Animatable(0f) }
    LaunchedEffect(active) {
        scale.animateTo(if (active) DesignTokens.folderMergeScale.value else 0f, Motion.mergeHint.spec())
    }
    if (scale.value > 0.01f) {
        GlassSurface(
            wallpaper = wallpaper,
            shape = ContinuousRoundedShape(DesignTokens.iconCorner.dp),
            blur = Dp(0f),
            tint = 0f,
            whiteTint = DesignTokens.folderIconTint.value,
            modifier = Modifier
                .size(DesignTokens.iconSize.dp)
                .graphicsLayer { scaleX = scale.value; scaleY = scale.value },
        )
    }
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

    /** Titel links: gleicher Abstand zur Panel-Kante wie beim iPhone (74,33 − 47,83 pt). */
    val titleLeft: Float get() = panel.left + px(DesignTokens.folderTitleX.value - (402f - DesignTokens.folderPanelWidth.value) / 2)

    /** Skalierung + Mittelpunkt bei Fortschritt p: vom Ordner-Icon (gemessen: Skala 64/306,33) zur Bildschirmmitte. */
    fun transform(p: Float, iconTopLeft: Offset): Pair<Float, Offset> {
        val icon = px(DesignTokens.iconSize.value)
        val start = icon / panel.width
        val s = start + (1f - start) * p
        val from = iconTopLeft + Offset(icon / 2, icon / 2)
        val c = from + (panel.center - from) * p
        return s to c
    }
}

@Composable
fun FolderOverlay(
    model: HomeModel,
    open: OpenFolder,
    geo: FolderGeometry,
    density: Float,
    wallpaper: ImageBitmap,
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
    val corner = DesignTokens.folderPanelCorner.value
    Box(
        Modifier
            .layout { m, _ ->
                val (s, c) = geo.transform(open.progress.value, open.iconTopLeft)
                val w = (panel.width * s).roundToInt().coerceAtLeast(1)
                val h = (panel.height * s).roundToInt().coerceAtLeast(1)
                val p = m.measure(androidx.compose.ui.unit.Constraints.fixed(w, h))
                layout(p.width, p.height) { p.place((c.x - w / 2f).roundToInt(), (c.y - h / 2f).roundToInt()) }
            }
            .graphicsLayer { alpha = model.folderHandoff(open.progress.value) }
    ) {
        // konstanter Eckradius (Kyant übernimmt die Form nur einmal, animierter Radius → zwei Formen)
        LiquidGlass(
            shape = glassRect(Dp(corner)),
            modifier = Modifier.matchParentSize(),
            thickness = DesignTokens.glassThicknessLarge.value,
            blur = DesignTokens.folderPanelBlur.dp,
            whiteTint = DesignTokens.folderPanelTint.value,
            // klares Glas (IMG_3130: innen = außen): Quelle ist nicht abgedunkelt → dieselbe Abdunkelung als Tönung
            blackTint = DesignTokens.folderBgDim.value * open.progress.value.coerceIn(0f, 1f),
        )
    }

    // Icons: aus dem Ordner-Icon heraus skaliert (M-FOLDER, Fortschritt 0…1)
    Box(
        Modifier
            .offset { IntOffset(panel.left.roundToInt(), panel.top.roundToInt()) }
            .size(Dp(panel.width / density), Dp(panel.height / density))
            .graphicsLayer {
                alpha = model.folderHandoff(open.progress.value)
                val (s, c) = geo.transform(open.progress.value, open.iconTopLeft)
                scaleX = s; scaleY = s
                translationX = c.x - panel.center.x
                translationY = c.y - panel.center.y
                transformOrigin = TransformOrigin.Center
            }
    ) {
        // Seitenpunkte unten im Panel: je 9 Apps eine Seite, im Bearbeiten-Modus eine leere Seite mehr (iOS) [Video 1, 4,1 s]
        val perPage = DesignTokens.FOLDER_COLUMNS * DesignTokens.FOLDER_ROWS
        val pageCount = (apps.size + perPage - 1) / perPage + (if (model.editing) 1 else 0)
        if (pageCount > 1) androidx.compose.foundation.Canvas(Modifier.matchParentSize()) {
            val r = FOLDER_DOT_D / 2 * density; val pitch = FOLDER_DOT_PITCH * density
            val cy = size.height - FOLDER_DOT_FROM_BOTTOM * density
            val x0 = size.width / 2 - pitch * (pageCount - 1) / 2
            for (k in 0 until pageCount) drawCircle(Color.White.copy(alpha = if (k == 0) 1f else 0.4f), r, Offset(x0 + k * pitch, cy))
        }
        apps.take(DesignTokens.FOLDER_COLUMNS * DesignTokens.FOLDER_ROWS).forEachIndexed { i, app ->
            val tl = geo.slot(i)
            // App wächst gerade daraus bzw. fliegt hinein → Platz leer (wie auf dem Homescreen)
            if (model.flyingKey == app.key || io.github.janhurn9400.liquidy.data.AppLaunchTransition.launchingKey.value == app.key) return@forEachIndexed
            AppIconView(
                app = app,
                showLabel = true,
                wallpaper = wallpaper,
                pressAlpha = { if (model.pressedKey == app.key) model.pressAlpha.value else 0f },
                jiggle = { model.jiggleAmount.value },
                clockSeconds = { 0f },
                badgeScale = { model.badgeScale.value },
                showBadge = model.editing || model.badgeScale.value > 0f, // Bearbeiten: Minus wie auf dem Homescreen
                modifier = Modifier.offset {
                    IntOffset(cellOffsetX(tl.x, density).roundToInt(), tl.y.roundToInt())
                },
            )
        }
    }
}

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
            blur = DesignTokens.folderPanelBlur.dp, whiteTint = NAME_TINT, blackTint = 0f, dispersion = false)
        androidx.compose.foundation.layout.Row(
            Modifier.matchParentSize().padding(start = 20.dp, end = 14.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            androidx.compose.foundation.text.BasicTextField(
                value = name,
                onValueChange = { model.renameFolder(open.id, it) },
                singleLine = true,
                textStyle = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = NAME_FONT.sp, color = Color.White),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.White),
                modifier = Modifier.weight(1f),
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
