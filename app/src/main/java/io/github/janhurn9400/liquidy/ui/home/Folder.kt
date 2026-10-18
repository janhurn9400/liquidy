package io.github.janhurn9400.liquidy.ui.home

import kotlinx.coroutines.launch
import androidx.compose.animation.core.VectorConverter
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
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

/** Folder icon (T-FOLDER-ICON): glass squircle with 3×3 mini icons. */
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
    /** Dragged icon (lies above the glass source): refracts the icons below live instead of only the wallpaper. */
    liveBackdrop: Boolean = false,
    /** App flying into the folder right now: its mini slot stays empty until it has landed (M-MERGE-DROP). */
    hiddenApp: String? = null,
) {
    val phases = androidx.compose.runtime.remember(id) { jigglePhases(id) }
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    // Folder hint: the name fades out while an icon is held over it [MEASURED user iPhone 10-14, 10.62 s]
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
        // Folder hint [MEASURED user iPhone 10-14]: the folder glass itself grows to T-MERGE.scale and darkens,
        // the mini icons stay put
        val hint = androidx.compose.runtime.remember(id) { Animatable(0f) }
        androidx.compose.runtime.LaunchedEffect(mergeHint) { hint.animateTo(if (mergeHint) 1f else 0f, Motion.mergeHint.spec()) }
        // The same Liquid Glass as the dock (lens, light, tint) — source is only the wallpaper, because the icon itself
        // lies in the recorded icon layer; while dragging it lies above → real source (wallpaper + icons)
        val wb = io.github.janhurn9400.liquidy.glass.LocalWallpaperBlur.current
        val live = io.github.janhurn9400.liquidy.glass.LocalGlassBackdrop.current
        val backdrop = androidx.compose.runtime.remember(wallpaper, wb, liveBackdrop, live) {
            if (liveBackdrop && live != null) live else io.github.janhurn9400.liquidy.glass.WallpaperBackdrop(wallpaper, wb.blurred, wb.amount)
        }
        androidx.compose.runtime.CompositionLocalProvider(io.github.janhurn9400.liquidy.glass.LocalGlassBackdrop provides backdrop) {
            androidx.compose.foundation.layout.Box(Modifier.size(DesignTokens.iconSize.dp).graphicsLayer {
                // Read the jiggle → glass redraws every frame (background stays still)
                jiggleValues(jiggle(), clockSeconds(), phases, density)
            }.drawWithContent {
                // Light rim top/bottom like every app icon (T-OBJ-RIM) — the folder is an icon too
                drawContent()
                io.github.janhurn9400.liquidy.glass.drawObjectRim(this, io.github.janhurn9400.liquidy.design.ContinuousRoundedShape(DesignTokens.iconCorner.dp))
            }) {
                io.github.janhurn9400.liquidy.glass.LiquidGlass(
                    shape = io.github.janhurn9400.liquidy.glass.glassRect(DesignTokens.iconCorner.dp),
                    blackTint = DesignTokens.glassTint.value + DesignTokens.folderMergeDark.value * hint.value,
                    modifier = Modifier.mergeGrow { 1f + (DesignTokens.folderMergeScale.value - 1f) * hint.value },
                )
                // Pass the key (multiple icons have their own keys) — the flying slot stays empty
                MiniGrid(folder.apps.filter { apps[it] != null }.map { k -> if (k == hiddenApp) null else apps[k] })
            }
        }
    }
}

/** 3×3 mini icons, measured dimensions (inset 10 pt, mini 13 pt, gap 16 pt). */
@Composable
private fun MiniGrid(apps: List<AppEntry?>) {
    val k = DesignTokens.folderMiniScale
    val mini = DesignTokens.folderMiniSize.dp * k
    // Mini icons have the same corner proportions as large icons
    val miniShape = ContinuousRoundedShape(DesignTokens.iconCorner.dp * (DesignTokens.folderMiniSize.value * k / DesignTokens.iconSize.value))
    apps.take(DesignTokens.FOLDER_COLUMNS * DesignTokens.FOLDER_ROWS).forEachIndexed { i, app ->
        if (app == null) return@forEachIndexed // flying in right now or unknown: the slot stays free
        val col = i % DesignTokens.FOLDER_COLUMNS
        val row = i / DesignTokens.FOLDER_COLUMNS
        StyledAppIcon(
            app = app,
            modifier = Modifier
                .offset(
                    x = (DesignTokens.folderMiniInset.dp + DesignTokens.folderMiniPitch.dp * col) * k,
                    y = (DesignTokens.folderMiniInset.dp + DesignTokens.folderMiniPitch.dp * row) * k,
                )
                .size(mini)
                .clip(miniShape),
        )
    }
}

/**
 * App held over app: behind the target the folder glass appears (like the folder hint: grows to T-MERGE.scale,
 * darkened) — looks like the folder that is about to be created.
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

/** Glass at [scale] × icon size around the centre — via the real size (layout), not graphicsLayer: otherwise the refracted
 *  background would be enlarged too (docs/GLASS.md). */
private fun Modifier.mergeGrow(scale: () -> Float): Modifier = this.layout { m, c ->
    val base = c.maxWidth
    val px = (base * scale()).roundToInt().coerceAtLeast(1)
    val p = m.measure(androidx.compose.ui.unit.Constraints.fixed(px, px))
    layout(base, base) { p.place((base - px) / 2, (base - px) / 2) }
}

// ── Open folder ───────────────────────────────────────────────────────────

/** Geometry of the open folder in screen px (panel rect, icon positions, title). */
class FolderGeometry(
    private val density: Float,
    private val width: Float,
    private val height: Float,
    private val home: HomeGeometry? = null,
    /** x of the folder icon (px): unfolded the folder opens above the half it's in. */
    private val iconX: Float? = null,
) {
    private fun px(v: Float) = v * density
    /** Duo unfolded [MEASURED 19:02 (video 1), 4.1 s]: 290 × 345 pt, centred above the grid of its half, top 160 pt. */
    private val duoOpen get() = home?.unfolded == true
    val panel: Rect
        get() {
            if (duoOpen) {
                val w = px(DUO_FOLDER_W); val h = px(DUO_FOLDER_H)
                val cx = home!!.duoFolderCenterX(iconX ?: width)
                // Top like on the Duo (160 pt), but never so low that the panel sticks out at the bottom; keep it in view sideways
                val top = minOf(px(DUO_FOLDER_TOP), height - h - px(DUO_FOLDER_MIN_BOTTOM)).coerceAtLeast(px(DUO_FOLDER_MIN_TOP))
                val left = (cx - w / 2).coerceIn(px(8f), width - w - px(8f))
                return Rect(Offset(left, top), Size(w, h))
            }
            val w = px(DesignTokens.folderPanelWidth.value)
            val h = px(DesignTokens.folderPanelHeight.value)
            return Rect(Offset((width - w) / 2, (height - h) / 2), Size(w, h))
        }

    /** Icon top-left in the panel (panel-local). */
    fun slot(i: Int): Offset {
        val col = i % DesignTokens.FOLDER_COLUMNS
        val row = i / DesignTokens.FOLDER_COLUMNS
        if (duoOpen) return Offset(px(DUO_FOLDER_GRID_LEFT + home!!.duoColPitchPt * col), px(DUO_FOLDER_GRID_TOP + home.duoRowPitchPt * row))
        return Offset(
            px(DesignTokens.folderGridLeft.value + DesignTokens.gridColPitch.value * col),
            px(DesignTokens.folderGridTop.value + DesignTokens.folderRowPitch.value * row),
        )
    }

    /** Rename capsule in edit mode: as wide as the panel, 19 pt above it, 59 pt tall [MEASURED video 1, 4.1 s]. */
    val titleRect: Rect get() = Rect(Offset(panel.left, panel.top - px(NAME_GAP + NAME_H)), Size(panel.width, px(NAME_H)))

    /** Area on the right of the rename field that belongs to the × button (px): margin + button + some slack. */
    val nameClearZone: Float get() = px(NAME_CLEAR_END + NAME_CLEAR + NAME_CLEAR_SLOP)

    /** Title left: same distance to the panel edge as on the iPhone (74.33 − 47.83 pt). */
    val titleLeft: Float get() = panel.left + px(DesignTokens.folderTitleX.value - (402f - DesignTokens.folderPanelWidth.value) / 2)

    /**
     * Rect of glass + content at progress p: from the folder icon square to the panel (width/height separately) — at p → 0
     * it matches the folder icon exactly, otherwise you'd see the crossfade during the (slowly settling) close.
     */
    fun rect(p: Float, iconTopLeft: Offset, iconScale: Float = 1f): Rect {
        val icon = px(DesignTokens.iconSize.value)
        // visible folder icon (smaller in the Duo dock, around the centre) — HomeGeometry.dockIconScale
        val s = icon * iconScale; val o = (icon - s) / 2
        val l0 = iconTopLeft.x + o; val t0 = iconTopLeft.y + o
        fun l(a: Float, b: Float) = a + (b - a) * p
        return Rect(l(l0, panel.left), l(t0, panel.top), l(l0 + s, panel.right), l(t0 + s, panel.bottom))
    }

    /** Centre of app icon [i] (index on the page) relative to [rect] at p: mini slot in the folder icon → panel slot. */
    fun iconCenter(i: Int, p: Float, iconScale: Float = 1f): Offset {
        val col = i % DesignTokens.FOLDER_COLUMNS; val row = i / DesignTokens.FOLDER_COLUMNS
        val mk = DesignTokens.folderMiniScale
        val half = px(DesignTokens.folderMiniSize.value) * mk / 2
        val mini = Offset(px(DesignTokens.folderMiniInset.value + DesignTokens.folderMiniPitch.value * col) * mk + half,
            px(DesignTokens.folderMiniInset.value + DesignTokens.folderMiniPitch.value * row) * mk + half) * iconScale
        val icon = px(DesignTokens.iconSize.value)
        val full = slot(i) + Offset(icon / 2, icon / 2)
        return mini + (full - mini) * p
    }

    /** Size of the app icons relative to full size at p: mini icon → full icon. */
    fun iconScale(p: Float, folderIconScale: Float = 1f): Float {
        val k = DesignTokens.folderMiniSize.value * DesignTokens.folderMiniScale / DesignTokens.iconSize.value * folderIconScale
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

    // Edit mode: title as a glass capsule with text field + clear button (rename) [video 1, 4.1 s]
    if (model.editing) FolderNameField(model, open, geo, density, folder.name)
    // Title: large, bold, white, left-aligned above the panel; fades in with progress
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

    // Glass: do NOT scale via graphicsLayer — otherwise the refracted background image is enlarged too and the wallpaper
    // seems to grow along. Instead real size/position per frame (layout phase), the glass always shows what lies behind.
    // Corner radius grows along: folder icon corner → panel corner (the size changes in the same frame, Kyant takes over the shape
    // on every size change — the pitfall "shape within the shape" only occurred at constant size)
    val pr = open.progress.value.coerceIn(0f, 1f)
    val iconCorner = DesignTokens.iconCorner.value * open.iconScale // folder in the Duo dock: smaller (dockIconScale)
    val corner = iconCorner + (DesignTokens.folderPanelCorner.value - iconCorner) * pr
    fun Modifier.atRect() = this.layout { m, _ ->
        val r = geo.rect(open.progress.value, open.iconTopLeft, open.iconScale)
        val p = m.measure(androidx.compose.ui.unit.Constraints.fixed(r.width.roundToInt().coerceAtLeast(1), r.height.roundToInt().coerceAtLeast(1)))
        layout(p.width, p.height) { p.place(r.left.roundToInt(), r.top.roundToInt()) }
    }
    // Fast glass: the home screen behind the panel doesn't move while the folder is open → snapshot it ONCE (sharp + blurred
    // to T-FOLDER-PANEL.blur) and let WallpaperGlass sample it; the live backdrop re-recorded the whole home screen and
    // re-blurred it every frame (57 % janky frames opening/closing on a Snapdragon 780G). Until the snapshot is ready
    // (1–2 frames) the live glass stands in.
    val liveSource = io.github.janhurn9400.liquidy.glass.LocalGlassBackdrop.current as? com.kyant.backdrop.backdrops.LayerBackdrop
    val panelDensity = androidx.compose.ui.platform.LocalDensity.current.density
    val snapshot by androidx.compose.runtime.produceState<Pair<ImageBitmap, ImageBitmap>?>(null, open, liveSource) {
        val layer = liveSource?.graphicsLayer ?: return@produceState
        val img = runCatching { layer.toImageBitmap() }.getOrNull() ?: return@produceState
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            val sharp = img.asAndroidBitmap().copy(android.graphics.Bitmap.Config.ARGB_8888, false).asImageBitmap()
            sharp to io.github.janhurn9400.liquidy.glass.WallpaperRenderer.softBlur(sharp, DesignTokens.folderPanelBlur.value * panelDensity)
        }
    }
    Box(Modifier.atRect().graphicsLayer { alpha = model.folderVisible(open) }) {
        // Glass values glide with progress to the folder icon glass (LiquidGlass default) — small, the panel looks
        // exactly like the icon, the swap at the end of closing is invisible
        val thickness = 1f + (DesignTokens.glassThicknessLarge.value - 1f) * pr
        val whiteTint = DesignTokens.glassWhiteTint.value + (DesignTokens.folderPanelTint.value - DesignTokens.glassWhiteTint.value) * pr
        // clear glass (IMG_3130: inside = outside): the source is not dimmed → the same dimming as tint
        val blackTint = DesignTokens.glassTint.value + (DesignTokens.folderBgDim.value - DesignTokens.glassTint.value) * pr
        val snap = snapshot
        if (snap != null) io.github.janhurn9400.liquidy.glass.WallpaperGlass(
            wallpaper = snap.first, shape = glassRect(Dp(corner)), modifier = Modifier.matchParentSize(), thickness = thickness,
            whiteTint = whiteTint, blackTint = blackTint,
            // blur glides from the icon glass to the panel blur = blend of the sharp and the blurred snapshot
            soft = snap.second, softAmount = { open.progress.value.coerceIn(0f, 1f) },
        ) else LiquidGlass(
            shape = glassRect(Dp(corner)),
            modifier = Modifier.matchParentSize(),
            thickness = thickness,
            blur = (DesignTokens.glassBlur.value + (DesignTokens.folderPanelBlur.value - DesignTokens.glassBlur.value) * pr).dp,
            whiteTint = whiteTint,
            blackTint = blackTint,
        )
    }

    // Icons: scaled out of the folder icon (M-FOLDER, progress 0…1)
    // Folder pages: icons are clipped at the panel edge [MEASURED user iPhone 10-14, 17 s]
    Box(Modifier.atRect().graphicsLayer { alpha = model.folderVisible(open); clip = true }) {
        // Page dots at the bottom of the panel: one page per 9 apps, in edit mode one more empty page (iOS) [video 1, 4.1 s]
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
        // Reordering: order without the dragged icon, a gap at its insertion slot — the others make way
        val fd = model.folderDrag
        val order = folder.apps.filter { model.apps[it] != null }.let { keys ->
            if (fd == null || fd.hoverIndex < 0) keys else (keys - fd.key).toMutableList().apply { add(fd.hoverIndex.coerceIn(0, size), fd.key) }
        }
        apps.forEachIndexed { i0, app -> androidx.compose.runtime.key(app.key) {
            val i = order.indexOf(app.key).takeIf { it >= 0 } ?: i0
            val pageOfIcon = i / perPage
            // Reflow springs (M-REFLOW); when opening/closing without dragging the icon stands exactly on its slot
            val slotAnim = androidx.compose.runtime.remember(app.key) { Animatable(geo.slot(i % perPage), Offset.VectorConverter) }
            androidx.compose.runtime.LaunchedEffect(i, fd != null) {
                if (fd != null) slotAnim.animateTo(geo.slot(i % perPage), Motion.reflow.spec()) else slotAnim.snapTo(geo.slot(i % perPage))
            }
            // lifted (being dragged in the folder) → hangs on the finger as a copy
            val hidden = model.folderDrag?.key == app.key ||
                // App is growing out of it or flying into it → slot empty (like on the home screen)
                model.flyingKey == app.key || io.github.janhurn9400.liquidy.data.AppLaunchTransition.launchingKey.value == app.key
            if (!hidden) AppIconView(
                app = app,
                showLabel = true,
                wallpaper = wallpaper,
                pressAlpha = { if (model.pressedKey == app.key) model.pressAlpha.value else 0f },
                jiggle = { model.jiggleAmount.value },
                clockSeconds = clockSeconds, // the same clock as the home screen — otherwise the icons just stand still at an angle
                badgeScale = { model.badgeScale.value },
                showBadge = model.editing || model.badgeScale.value > 0f, // editing: minus like on the home screen
                badgeOnGlass = true, // frosted glass from what lies behind the folder
                labelAlpha = { (open.progress.value / LABEL_FADE_UNTIL).coerceIn(0f, 1f) }, // mini icons have no names
                modifier = Modifier
                    // full cell size, even if the container is narrower than a cell while shrinking — otherwise
                    // Compose squeezes the cell in and shifts it by half the difference (the app slid out of the glass)
                    .wrapContentSize(androidx.compose.ui.Alignment.TopStart, unbounded = true)
                    .offset {
                        val pr = open.progress.value
                        val c = if (model.folderDrag != null) slotAnim.value + Offset(iconPx / 2, iconPx / 2)
                            else geo.iconCenter(i % perPage, pr, open.iconScale)
                        // page shifted by whole (current) widths (paging reads the value only in layout)
                        val dx = (pageOfIcon - model.folderPage.value) * geo.rect(pr, open.iconTopLeft, open.iconScale).width
                        IntOffset(cellOffsetX(c.x - iconPx / 2 + dx, density).roundToInt(), (c.y - iconPx / 2).roundToInt())
                    }
                    .graphicsLayer {
                        val k = geo.iconScale(open.progress.value, open.iconScale)
                        scaleX = k; scaleY = k
                        transformOrigin = TransformOrigin(0.5f, iconPx / 2 / size.height)
                    },
            )
        } }
    }
}

/** App being dragged in the open folder: on the finger, slightly lifted (like dragging on the home screen). */
@Composable
fun FolderDragCopy(model: HomeModel, density: Float, wallpaper: ImageBitmap) {
    val fd = model.folderDrag ?: return
    val app = model.apps[fd.key] ?: return
    val lift = androidx.compose.runtime.remember(fd) { Animatable(1f) }
    androidx.compose.runtime.LaunchedEffect(fd) { lift.animateTo(DesignTokens.dragScale.value, Motion.dragLift.spec()) }
    // Released: fly from the finger position into the slot (M-DROP), the lift goes back, then show the real icon
    val land = androidx.compose.runtime.remember(fd) { Animatable(Offset.Zero, Offset.VectorConverter) }
    val target = fd.landingAt
    androidx.compose.runtime.LaunchedEffect(target) {
        if (target == null) return@LaunchedEffect
        land.snapTo(fd.pointer - fd.grab)
        launch { lift.animateTo(1f, Motion.drop.spec()) }
        land.animateTo(target, Motion.drop.spec())
        if (model.folderDrag === fd) model.folderDrag = null
    }
    androidx.compose.foundation.layout.Box(
        Modifier
            .offset { val tl = if (target != null) land.value else fd.pointer - fd.grab; IntOffset(cellOffsetX(tl.x, density).roundToInt(), tl.y.roundToInt()) }
            .graphicsLayer { scaleX = lift.value; scaleY = lift.value; transformOrigin = TransformOrigin(0.5f, 0.5f) }
    ) {
        AppIconView(app = app, showLabel = false, wallpaper = wallpaper, pressAlpha = { 0f }, jiggle = { 0f },
            clockSeconds = { 0f }, badgeScale = { 0f }, showBadge = false)
    }
}

private const val LABEL_FADE_UNTIL = 0.35f // app names in the panel are hidden below this progress
private const val DUO_FOLDER_W = 290f
private const val DUO_FOLDER_H = 345f
private const val DUO_FOLDER_TOP = 160f
private const val DUO_FOLDER_MIN_BOTTOM = 40f
private const val DUO_FOLDER_MIN_TOP = 100f // space for title/rename capsule above
private const val DUO_FOLDER_GRID_LEFT = 26f // icon top-left (first centre 56 pt from the edge)
private const val DUO_FOLDER_GRID_TOP = 24f  // first row centre 54 pt below the top
private const val NAME_H = 59f
private const val FOLDER_DOT_D = 7f
private const val FOLDER_DOT_PITCH = 15f
private const val FOLDER_DOT_FROM_BOTTOM = 17f // [MEASURED] dots at 488 pt with panel bottom at 506
private const val NAME_TINT = 0.22f
private const val NAME_GAP = 19f
private const val NAME_FONT = 26f
private const val NAME_CLEAR = 24f
private const val NAME_CLEAR_END = 14f   // right inner margin of the capsule (like the padding below)
private const val NAME_CLEAR_SLOP = 12f
private const val NAME_PAD_START = 20f   // left inner margin of the capsule up to the text

/** Rename field: glass capsule above the panel, the name (bold) on the left, a round clear button on the right. */
@androidx.compose.runtime.Composable
private fun FolderNameField(model: HomeModel, open: OpenFolder, geo: FolderGeometry, density: Float, name: String) {
    val r = geo.titleRect
    Box(
        Modifier
            .offset { IntOffset(r.left.roundToInt(), r.top.roundToInt()) }
            .size(Dp(r.width / density), Dp(r.height / density))
            .graphicsLayer { alpha = open.progress.value.coerceIn(0f, 1f) }
    ) {
        // light and milky like the original (not dark/distorted): strongly blurred, brightened, without colour fringes
        LiquidGlass(shape = io.github.janhurn9400.liquidy.glass.glassCapsule(), modifier = Modifier.matchParentSize(),
            blur = DesignTokens.folderPanelBlur.dp, whiteTint = NAME_TINT, blackTint = 0f)
        androidx.compose.foundation.layout.Row(
            Modifier.matchParentSize().padding(start = NAME_PAD_START.dp, end = NAME_CLEAR_END.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            // Focus comes from the gesture arena (it lies above): tapping the field → folderRenaming
            val focus = androidx.compose.runtime.remember { androidx.compose.ui.focus.FocusRequester() }
            val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
            val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
            // own text state with cursor; if the name changes from outside (× button), cursor to the end
            var field by androidx.compose.runtime.remember(open.id) {
                androidx.compose.runtime.mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(name, androidx.compose.ui.text.TextRange(name.length)))
            }
            if (field.text != name) field = androidx.compose.ui.text.input.TextFieldValue(name, androidx.compose.ui.text.TextRange(name.length))
            val textLayout = androidx.compose.runtime.remember { arrayOfNulls<androidx.compose.ui.text.TextLayoutResult>(1) }
            androidx.compose.runtime.LaunchedEffect(model.folderRenaming, model.folderRenameTap) {
                if (!model.folderRenaming) { focusManager.clearFocus(); keyboard?.hide(); return@LaunchedEffect }
                // Cursor where tapped (the text starts after the capsule's left inner margin); behind the text → end
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
