package io.github.janhurn9400.liquidy.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.width
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import io.github.janhurn9400.liquidy.data.AppEntry
import io.github.janhurn9400.liquidy.design.ContinuousRoundedShape
import io.github.janhurn9400.liquidy.design.DesignTokens
import io.github.janhurn9400.liquidy.glass.LiquidGlass
import io.github.janhurn9400.liquidy.glass.LocalGlassBackdrop
import io.github.janhurn9400.liquidy.glass.glassRect
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import androidx.compose.runtime.CompositionLocalProvider
import io.github.janhurn9400.liquidy.motion.Motion
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sign

private sealed interface Hit {
    data class Icon(val key: String, val iconTopLeft: Offset) : Hit
    data class Badge(val key: String) : Hit
    data object EditDone : Hit
    data object PageDots : Hit
    data object EditCustomize : Hit
    data class WidgetHandle(val key: String) : Hit
    data object Today : Hit
    data object Empty : Hit
    /** Blurred home page next to the open App Library: swiping only, no tap/hold (no app, no editing). */
    data object Covered : Hit
    data object Pill : Hit
    data class Library(val hit: io.github.janhurn9400.liquidy.ui.library.LibraryHit) : Hit
}

private enum class Mode { Pending, Paging, Vertical, LibScroll, LibPull, TodayScroll, TodayDrag, Menu, Drag, Resize, StackScroll, WidgetTouch, Consumed }

/**
 * Home screen: wallpaper, pages (4×6), dock, pill, edit mode.
 * One central gesture arena (ARCHITECTURE): tap, long press, paging, dragging, badge, edit buttons.
 */
@Composable
fun HomeScreen(
    model: HomeModel,
    pager: PagerState,
    wallpaper: ImageBitmap,
    onLaunch: (AppEntry, Rect) -> Unit,
    /** false: Android shows the same image behind the window → draw our own only for blur effects. */
    drawWallpaper: Boolean = true,
    /** Wallpaper editor: smoothly animated blur of the preview (0…1). */
    wallpaperBlur: () -> Float = { 0f },
) {
    val hapticView = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.SideEffect { model.haptic = { io.github.janhurn9400.liquidy.data.Haptics.tick(hapticView) } }
    val scope = rememberCoroutineScope()
    val view = androidx.compose.ui.platform.LocalView.current
    val density = LocalDensity.current.density
    val display = model.display
    val dragging = model.drag != null
    // App Library = page after the last home page (not in edit mode)
    // Read fresh (functions instead of values): the gesture arena (pointerInput) is created only once and would otherwise
    // keep the page count/App Library position from creation → taps hit old positions, holding in the App Library didn't work
    fun libIndexNow() = model.display.pages.size
    fun libEnabledNow() = !model.editing || model.leavingLibrary
    // Today view on the left (not in edit mode — there page −1 would not be a valid drag target)
    // Today view reachable in edit mode too — just not while dragging icons (page −1 is not a drop target)
    val screenIsWide = androidx.compose.ui.platform.LocalConfiguration.current.let { it.screenWidthDp > it.screenHeightDp }
    pager.firstPage = if (model.drag != null) 0 else -1
    pager.pageCount = display.pages.size +
        (if (dragging && display.pages.lastOrNull()?.isNotEmpty() == true) 1 else 0) +
        (if (libEnabledNow()) 1 else 0) -
        // Duo unfolded: two pages visible → the last position shows [last page | App Library]
        (if (io.github.janhurn9400.liquidy.data.FoldableStyle.duoStyle && screenIsWide) 1 else 0)

    // Pages gone (empty page removed, app uninstalled, reload): don't leave the position behind the last page
    LaunchedEffect(pager.pageCount) {
        val last = (pager.pageCount - 1).coerceAtLeast(0).toFloat()
        if (!pager.isDragging && pager.position.value > last) pager.position.animateTo(last, androidx.compose.animation.core.spring(1f, 380f))
    }

    // Jiggle clock: runs only while jiggling; read only in graphicsLayer → no recomposition
    val clock = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(model.editing) {
        if (!model.editing && model.jiggleAmount.value == 0f) return@LaunchedEffect
        val start = withFrameNanos { it }
        while (isActive && (model.editing || model.jiggleAmount.value > 0f)) {
            withFrameNanos { clock.floatValue = (it - start) / 1e9f }
        }
    }

    // Glass source: wallpaper + page icons are recorded; dock, pill, folders, dialogs refract this content
    // Side blur: Today view (left) and App Library (right) blur the wallpaper equally
    /** App Library share of the side blur (0 on the home pages, 1 on the App Library). */
    fun libBlur(): Float {
        val pos = pager.position.value
        // read fresh (not the values captured on first composition — the apps weren't loaded yet then)
        val libIndex = model.display.pages.size
        // Duo unfolded: the App Library comes into view one page earlier (two pages visible) — with only one
        // page that means directly from the Today view (position −1 → 0); so don't tie it to pos ≥ 0
        val shift = if (io.github.janhurn9400.liquidy.data.FoldableStyle.duoStyle && screenIsWide) 1 else 0
        return if (!model.editing || model.leavingLibrary) (pos - (libIndex - 1 - shift)).coerceIn(0f, 1f) else 0f
    }
    fun sideBlur(): Float {
        val pos = pager.position.value
        // Duo: wallpaper next to the Today page stays sharp (recording 10-08 21:28, 31 s — sharp wallpaper edge)
        // Duo unfolded: Today blurs only its own page (TodayPage); folded screen-wide like iPhone
        val today = if (io.github.janhurn9400.liquidy.data.FoldableStyle.duoStyle && screenIsWide) 0f else (-pos).coerceIn(0f, 1f)
        return maxOf(today, libBlur())
    }
    /** Only the App Library share of [sideBlur] (0 on the Today page). */
    fun sideBlurLibOnly(): Float = libBlur()

    val blurredWallpaper by androidx.compose.runtime.produceState<ImageBitmap?>(null, wallpaper) {
        value = io.github.janhurn9400.liquidy.glass.WallpaperRenderer.softBlur(wallpaper, DesignTokens.todayBlur.value * density)
    }
    val wallpaperBlur = remember(blurredWallpaper) { io.github.janhurn9400.liquidy.glass.WallpaperBlur(blurredWallpaper, ::sideBlur) }
    // Glass source: wallpaper (side blur blended in) + page icons — glass refracts the same background you see
    val glassBackdrop = rememberLayerBackdrop(onDraw = remember(wallpaper, blurredWallpaper) { {
        drawImage(wallpaper)
        val a = sideBlur(); val bw = blurredWallpaper
        if (bw != null && a > 0f) drawImage(bw, alpha = a)
        drawContent()
    } })
    CompositionLocalProvider(LocalGlassBackdrop provides glassBackdrop, LocalWallpaper provides wallpaper,
        io.github.janhurn9400.liquidy.glass.LocalWallpaperBlur provides wallpaperBlur) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val navBarPx = androidx.compose.foundation.layout.WindowInsets.navigationBars
            .getBottom(androidx.compose.ui.platform.LocalDensity.current).toFloat()
        // Height of the Android status bar (even when hidden) — measure for our own iOS status bar
        @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
        val statusBarPx = androidx.compose.foundation.layout.WindowInsets.statusBarsIgnoringVisibility
            .getTop(androidx.compose.ui.platform.LocalDensity.current).toFloat()
        // iPhone Duo style (foldable, chosen in setup): own geometry; icons 60 pt like on the Duo [MEASURED]
        val duo = io.github.janhurn9400.liquidy.data.FoldableStyle.duoStyle
        LaunchedEffect(duo) { DesignTokens.iconSize.tunable.value = if (duo) DUO_ICON else DesignTokens.iconSize.tunable.default }
        val geo = remember(w, h, density, navBarPx, duo) { HomeGeometry(density, w, h, navBarPx, duo) }
        geo.dockCount = model.display.dock.size
        // Dock slots: Duo with "More Apps in Dock" as many as fit into the right column, otherwise 4 like iOS
        model.dockMax = if (geo.duo && io.github.janhurn9400.liquidy.data.FoldableStyle.moreDockApps) geo.duoDockCapacity else DesignTokens.DOCK_SLOTS
        // Duo: dock glass grows/shrinks with the same spring as the reflowing icons (M-REFLOW) instead of jumping
        val dockSlots = remember { androidx.compose.animation.core.Animatable(model.display.dock.size.toFloat()) }
        val dockSize = model.display.dock.size
        LaunchedEffect(dockSize) { dockSlots.animateTo(dockSize.toFloat(), Motion.reflow.spec()) }
        /** Paging step: one page (unfolded 448 pt, otherwise screen width). */
        val pw = geo.pageWidth
        // Duo: glass source for the App Library tiles (they lie outside the main source, above the dock)
        val libPageBackdrop = if (geo.duo) com.kyant.backdrop.backdrops.rememberLayerBackdrop() else null
        // Folding (Duo, recording 10-08 21:29, 62.2–63.6 s): outer display appears dimmed + blurred, brightens in ≈ 0.85 s
        // and then sharpens in ≈ 0.45 s. Unfolding: inner display sharp immediately (no own transition).
        val foldIn = remember { androidx.compose.animation.core.Animatable(0f) }
        var wasUnfolded by remember { androidx.compose.runtime.mutableStateOf(geo.unfolded) }
        LaunchedEffect(geo.unfolded) {
            if (wasUnfolded && !geo.unfolded) {
                foldIn.snapTo(1f)
                foldIn.animateTo(0f, androidx.compose.animation.core.tween(FOLD_IN_MS, easing = androidx.compose.animation.core.LinearEasing))
            }
            wasUnfolded = geo.unfolded
        }
        val libGeo = remember(w, h, density, duo) { io.github.janhurn9400.liquidy.ui.library.LibraryGeometry(density, w, h, duo) }
        /** 1 on the home pages, 0 on the App Library: dock + pill fade out. */
        /** Page overview: home content disappears immediately (video: ≈ 3 frames), the card takes over. */
        // Page overview ↔ home screen [MEASURED Duo 19:05:45, 21.9 s @ 30 fps]: on close the real home screen
        // (dock, ✓/brush, minus, labels) fades in smoothly over ≈ 130–230 ms, the zoomed card is gone after ≈ 260 ms; no hard cut.
        // Home screen is fully there BEFORE the card above fades out → no dark in-between frame (PageOverview: FOCUS_FADE_*)
        fun overviewHide(): Float = if (!model.pagesOverviewOpen) 1f
            else ((OVERVIEW_HOME_HIDDEN - model.pagesOverview.value) / (OVERVIEW_HOME_HIDDEN - OVERVIEW_HOME_FULL)).coerceIn(0f, 1f)
        fun homeChromeAlpha(): Float {
            // Duo: dock + magnifier sit in the right column and stay visible next to the App Library (recording 35.5 s)
            val lib = if (!libEnabledNow() || geo.duo) 1f else (libIndexNow() - pager.position.value).coerceIn(0f, 1f)
            // Today view: dock + pill fade out — not on Duo (right column stays, recording 31 s)
            val today = if (geo.duo) 1f else (1f + pager.position.value).coerceIn(0f, 1f)
            return minOf(lib, today)
        }

        /** Duo (unfolded/folded): 0…1, how far the App Library is in view (dock/magnifier/dots react to it). */
        // Ramp: page + dock are already fully blurred when the App Library has come in ≈ 30 %
        // [MEASURED 10:40: 15.42 s ≈ 10 % slightly, 15.44 s ≈ 29 % fully blurred]
        fun duoLibAmount(): Float = if (geo.duo && libEnabledNow()) {
            val k = (sideBlurLibOnly() / DUO_LIB_BLUR_FULL_AT).coerceIn(0f, 1f); k * (2f - k)
        } else 0f
        // Duo: in the wallpaper editor the preview shows no dock and no magnifier [MEASURED 10:40, 84.5 s]
        val duoWpeHide by androidx.compose.animation.core.animateFloatAsState(
            if (geo.duo && model.wallpaperEditorOpen) 1f else 0f, androidx.compose.animation.core.tween(DUO_WPE_FADE_MS), label = "duoWpeHide")
        // Duo folded, search open: dock stays at first, slides out to the right after a short time, back on close
        val duoSearchDockOut = remember { androidx.compose.animation.core.Animatable(0f) }
        val duoSearchDockKey = geo.duo && !geo.unfolded && model.searchOpen
        LaunchedEffect(duoSearchDockKey) {
            if (duoSearchDockKey) {
                delay(DUO_SEARCH_DOCK_DELAY_MS)
                duoSearchDockOut.animateTo(1f, androidx.compose.animation.core.tween(DUO_SEARCH_DOCK_OUT_MS, easing = androidx.compose.animation.core.FastOutSlowInEasing))
            } else duoSearchDockOut.animateTo(0f, androidx.compose.animation.core.tween(DUO_SEARCH_DOCK_IN_MS, easing = androidx.compose.animation.core.FastOutSlowInEasing))
        }
        fun duoCatAmount(): Float = if (geo.duo) (model.openCategory?.progress?.value ?: 0f).coerceIn(0f, 1f) else 0f

        fun pageOffsetPx(page: Int) = geo.pageShift(page - pager.position.value)
        /** Open the page overview; Duo unfolded with Today on the left: current page is on the right → card grows from the right half. */
        fun openOverview() {
            model.overviewLeftPage = pager.position.value.roundToInt()
            model.overviewZoom.clear()
            model.openPagesOverview(pager.currentPage)
        }

        // ── Dragged icon: size ──────────────────────────────────────────────────
        // Duo: above the dock it has dock size (48 pt), otherwise grid size — blend smoothly so it doesn't jump when
        // dropping or pulling out; when picked up from the dock it starts at dock size
        val dragKey = model.drag?.key
        val dragOverDock by remember { derivedStateOf { geo.duo && model.drag?.hover is DropTarget.Dock } }
        val dragDockScale = remember { androidx.compose.animation.core.Animatable(1f) }
        val dragScaleFor = remember { arrayOfNulls<String>(1) }
        LaunchedEffect(dragKey, dragOverDock) {
            val t = if (dragOverDock) geo.dockIconScale else 1f
            if (dragKey != dragScaleFor[0]) { dragScaleFor[0] = dragKey; dragDockScale.snapTo(t) }
            else dragDockScale.animateTo(t, Motion.reflow.spec())
        }

        // ── Dragging an icon (shared by home gestures and App Library category) ──────────
        val dragEdgeJob = remember { arrayOfNulls<Job>(1) }
        /** Finger moves while dragging: update target, at the edge page on after M-EDGE or create a new page. */
        fun iconDragMove(pos: Offset) {
            // unfolded (Duo): page under the finger — two pages are visible at once
            val page = if (geo.unfolded) (pager.position.value.roundToInt() + if (pos.x > w / 2) 1 else 0).coerceAtLeast(0)
                else pager.position.value.roundToInt().coerceAtLeast(0)
            model.updateDrag(pos, geo, page, pageOffsetPx(page))
            // Page change at the edge (M-EDGE), not above the dock
            val x = pos.x
            val right = geo.pagesRightEdge
            val overDock = geo.inDockZone(pos)
            // Duo: dock column is at the right edge → strip in front of it (and the left one equally) wider; a full dock (or widget)
            // accepts nothing and acts like the edge — otherwise nothing happened when dragging to the right
            // Duo: on the right everything from the page edge to the screen edge counts as edge (also above/below the dock) —
            // only the dock glass itself accepts icons (user 10-14: the narrow strip was hard to hit)
            val rightZone = if (geo.duo) geo.edgeZone * 2f else geo.edgeZone
            val dockIsEdge = geo.duo && overDock && model.drag?.key?.let { !model.dockAccepts(it) } == true
            val atEdge = (!overDock && (x < rightZone || x > right - rightZone)) || dockIsEdge
            if (atEdge && dragEdgeJob[0] == null) {
                val dir = if (x < rightZone && !dockIsEdge) -1 else 1
                dragEdgeJob[0] = scope.launch {
                    while (isActive) {
                        // real position (can be −1: Today view left, page 0 right)
                        val cur = pager.position.value.roundToInt()
                        // Peek (M-EDGE, measured): small jolt immediately, then steadily onwards until after the
                        // hold time haptics + page change come. Before the first page (new page in front) no peek.
                        val peeks = !(dir < 0 && cur <= 0) && (cur + dir) in 0 until pager.pageCount
                        val start = System.nanoTime()
                        val peekJob = if (peeks) launch {
                            val perPt = density / geo.pageWidth
                            pager.position.animateTo(cur + dir * Motion.edgePeekNudge.value * perPt, Motion.edgeNudge.spec())
                            // creep on steadily for the rest of the hold time
                            val rest = (Motion.edgeFlipDelay.value - (System.nanoTime() - start) / 1_000_000f).coerceAtLeast(1f)
                            pager.position.animateTo(cur + dir * Motion.edgePeekMax.value * perPt,
                                androidx.compose.animation.core.tween(rest.roundToInt(), easing = androidx.compose.animation.core.LinearEasing))
                        } else null
                        delay(Motion.edgeFlipDelay.value.toLong())
                        peekJob?.cancel()
                        model.haptic() // iOS: noticeable tick exactly at the page change
                        // left before the first page: create a new page in front (view stays, then over)
                        if (dir < 0 && cur <= 0) {
                            if (model.insertPageFront()) {
                                pager.position.snapTo(pager.position.value + 1f)
                                pager.animateTo(0, 0f)
                                model.drag?.let { dr -> model.updateDrag(dr.pointer, geo, 0, pageOffsetPx(0)) }
                            }
                            break
                        }
                        val target = (cur + dir).coerceIn(0, pager.pageCount - 1)
                        if (target == cur) break
                        pager.animateTo(target, 0f)
                        model.drag?.let { dr -> model.updateDrag(dr.pointer, geo, target, pageOffsetPx(target)) }
                        delay((Motion.edgeFlipRepeat.value - Motion.edgeFlipDelay.value).coerceAtLeast(0f).toLong())
                    }
                }
            } else if (!atEdge && dragEdgeJob[0] != null) {
                dragEdgeJob[0]?.cancel(); dragEdgeJob[0] = null
                // Edge left before the change: page springs back from the peek
                val p = pager.position.value
                if (p != p.roundToInt().toFloat()) scope.launch { pager.animateTo(p.roundToInt()) }
            }
        }
        /** Screen top-left of an icon on the visible pages or in the dock. */
        fun iconScreenTopLeft(key: String): Offset? {
            val d = model.display
            d.dock.indexOf(key).takeIf { it >= 0 }?.let { return geo.dockPosition(it, d.dock.size) }
            d.pages.forEachIndexed { page, keys ->
                val slot = keys.indexOf(key); if (slot < 0) return@forEachIndexed
                val cell = model.displayCells.getOrNull(page)?.getOrNull(slot) ?: return null
                if (cell < 0) return null
                return geo.slotPosition(cell) + Offset(pageOffsetPx(page), 0f)
            }
            return null
        }
        fun iconDragEnd() {
            dragEdgeJob[0]?.cancel(); dragEdgeJob[0] = null
            // Dropping on a folder/app: icon flies into its mini slot (M-MERGE-DROP) instead of disappearing
            val d = model.drag
            val target = d?.mergeTarget
            val fly = if (d != null && target != null && model.apps[d.key] != null) {
                val slot = if (isFolder(target)) model.folders[target]?.apps?.size ?: 0 else 1
                val folderTl = iconScreenTopLeft(target)
                if (folderTl == null || slot >= DesignTokens.FOLDER_COLUMNS * DesignTokens.FOLDER_ROWS) null else {
                    val s = d.lift.value * dragDockScale.value
                    val c = d.pointer - d.grabOffset + Offset(geo.icon / 2, geo.icon / 2)
                    val from = Rect(c - Offset(geo.icon * s / 2, geo.icon * s / 2), Size(geo.icon * s, geo.icon * s))
                    // Folder in the dock: visible icon smaller (geo.iconRect / dockIconScale) → scale mini slots along
                    val box = geo.iconRect(folderTl, target in model.display.dock)
                    val k = box.width / geo.icon * DesignTokens.folderMiniScale
                    val mini = DesignTokens.folderMiniSize.value * density * k
                    val tl = box.topLeft + Offset(
                        (DesignTokens.folderMiniInset.value + DesignTokens.folderMiniPitch.value * (slot % DesignTokens.FOLDER_COLUMNS)) * density * k,
                        (DesignTokens.folderMiniInset.value + DesignTokens.folderMiniPitch.value * (slot / DesignTokens.FOLDER_COLUMNS)) * density * k)
                    FolderFly(d.key, target, from, Rect(tl, Size(mini, mini)))
                }
            } else null
            model.drop(geo, pager.position.value)
            // new folder from two apps: target is now the folder that appeared in place of the target app
            fly?.let { f -> model.flyIntoFolder(FolderFly(f.key, model.folderContaining(f.key) ?: f.folder, f.from, f.to)) }
        }
        /** Pull an app out of the App Library (overview or category): edit mode, pager swipes to the last page. */
        fun startLibraryDrag(appKey: String, pointer: Offset, iconTopLeft: Offset) {
            model.closeContextMenu(animated = false)
            model.leavingLibrary = true
            model.enterEdit()
            // if the app is already on the home screen → another icon (iOS allows several), the old one stays
            model.startDrag(model.newIconKey(appKey), pointer, iconTopLeft)
            val target = (model.display.pages.size - 1).coerceAtLeast(0)
            scope.launch {
                runCatching { pager.animateTo(target, 0f) }
                model.leavingLibrary = false
            }
        }
        // Target for gesture navigation: icon of the app (page/dock) or its folder on the current page
        model.iconLocator = locator@{ cn, user ->
            val d = model.display
            fun matches(k: String) = model.apps[k]?.let { it.component == cn && it.user == user } == true
            fun holds(k: String) = matches(k) || (isFolder(k) && model.folders[k]?.apps?.any(::matches) == true)
            val iconSize = Size(geo.icon, geo.icon)
            // Open folder (app was opened from it, iOS leaves it open) → slot of the app in the folder panel
            model.openFolder?.let { of ->
                val apps = model.folderFor(of.id)?.apps.orEmpty().take(DesignTokens.FOLDER_COLUMNS * DesignTokens.FOLDER_ROWS)
                val i = apps.indexOfFirst(::matches)
                if (i >= 0) {
                    val fg = FolderGeometry(density, w, h, geo, of.iconTopLeft.x)
                    return@locator Rect(fg.panel.topLeft + fg.slot(i), iconSize)
                }
            }
            // Duo: dock icons are smaller (48 pt instead of 60) → shrink the target rect around the icon centre, otherwise the big
            // icon flies back and jumps to dock size at the end
            fun dockRect(i: Int): Rect {
                val tl = geo.dockPosition(i, d.dock.size)
                if (!geo.duo) return Rect(tl, iconSize)
                val s = geo.icon * geo.dockIconScale
                val c = tl + Offset(geo.icon / 2, geo.icon / 2)
                return Rect(c - Offset(s / 2, s / 2), Size(s, s))
            }
            // several icons of the same app: the tapped one first (dock or visible pages), otherwise the first match
            val pref = model.launchedIconKey?.takeIf(::matches)
            if (pref != null) {
                d.dock.indexOf(pref).takeIf { it >= 0 }?.let { return@locator dockRect(it) }
                for (pg in pager.currentPage until pager.currentPage + geo.pagesVisible) {
                    val cells = model.displayCells.getOrNull(pg) ?: continue
                    val slot = d.pages.getOrNull(pg)?.indexOf(pref) ?: -1
                    if (slot >= 0 && (cells.getOrNull(slot) ?: -1) >= 0)
                        return@locator Rect(geo.slotPosition(cells[slot]) + Offset(pageOffsetPx(pg), 0f), iconSize)
                }
            }
            d.dock.indexOfFirst(::holds).takeIf { it >= 0 }?.let { return@locator dockRect(it) }
            val page = pager.currentPage
            // Opened from a Today widget and the Today view is open → window flies into this widget
            io.github.janhurn9400.liquidy.data.AppLaunchTransition.widgetOrigins[cn.packageName]?.takeIf { it in model.today && pager.position.value < -0.5f }?.let { wk ->
                val off = Offset((-1f - pager.position.value) * geo.sideStride, -model.todayScroll.value)
                TodayLayout.place(model.today, model::sizeOf, geo, density).firstOrNull { it.first == wk }?.let { return@locator it.second.translate(off) }
            }
            // Duo unfolded: two pages visible at once → search the right page too (otherwise the window flew
            // only to the centre for apps on the right page)
            for (pg in page until page + geo.pagesVisible) {
                val cells = model.displayCells.getOrNull(pg) ?: continue
                // Opened from a widget and the widget is on this page → window flies into the widget
                io.github.janhurn9400.liquidy.data.AppLaunchTransition.widgetOrigins[cn.packageName]?.let { wk ->
                    val spec = model.widgets[wk]
                    val slot = d.pages.getOrNull(pg)?.indexOf(wk) ?: -1
                    if (spec != null && slot >= 0 && (cells.getOrNull(slot) ?: -1) >= 0)
                        return@locator geo.widgetRect(geo.slotPosition(cells[slot]) + Offset(pageOffsetPx(pg), 0f), spec.cols, spec.rows)
                }
                d.pages.getOrNull(pg)?.forEachIndexed { slot, k ->
                    if (holds(k) && cells.getOrNull(slot)?.let { it >= 0 } == true)
                        return@locator Rect(geo.slotPosition(cells[slot]) + Offset(pageOffsetPx(pg), 0f), iconSize)
                }
            }
            null
        }
        /** Today widget under [p] (screen) with screen rect, or null. */
        fun todayWidgetAt(p: Offset): Pair<String, Rect>? {
            val off = Offset((-1f - pager.position.value) * geo.sideStride, -model.todayScroll.value)
            return TodayLayout.place(model.today, model::sizeOf, geo, density)
                .firstOrNull { it.second.translate(off).contains(p) }?.let { it.first to it.second.translate(off) }
        }
        fun todayMaxScroll(): Float {
            val rects = TodayLayout.place(model.today, model::sizeOf, geo, density)
            return (TodayLayout.contentHeight(rects, density, duoUnfolded = geo.duo && geo.unfolded) - h).coerceAtLeast(0f)
        }
        // visible as soon as the App Library reaches into the screen (unfolded two pages are visible)
        // same state object across page count changes (no key on it) → up to date in the gesture arena too
        val libraryOnScreen by remember(pager, geo) { derivedStateOf { pager.position.value > model.display.pages.size - geo.pagesVisible } }
        // Return to the App Library only when it is fully in view (not half swiped out)
        model.libraryVisible = { libEnabledNow() && abs(pager.position.value - (model.display.pages.size - geo.pagesVisible + 1)) < 0.05f }

        // Reads model.display fresh (not the state captured when the pointerInput was created)
        /** Screen rect of a widget on a home page (for landing animation/shock wave). */
        fun screenRectOf(key: String): Rect? {
            val d = model.display; val spec = model.widgets[key] ?: return null
            d.pages.forEachIndexed { page, keys ->
                val slot = keys.indexOf(key); if (slot < 0) return@forEachIndexed
                val cell = model.displayCells.getOrNull(page)?.getOrNull(slot) ?: return null
                if (cell < 0) return null
                return geo.widgetRect(geo.slotPosition(cell) + Offset(pageOffsetPx(page), 0f), spec.cols, spec.rows)
            }
            return null
        }

        fun hitTest(p: Offset): Hit {
            val display = model.display
            // Duo: right column (buttons, magnifier) is above everything → check first
            if (geo.duo) {
                if (model.editing && geo.editButtonRect(left = false).inflate(6f * density).contains(p)) return Hit.EditDone
                if (model.editing && geo.editButtonRect(left = true).inflate(6f * density).contains(p)) return Hit.EditCustomize
                // Editing: dots sit in a glass capsule → page overview (magnifier is gone then) [Duo, video 6, 11 s]
                if (model.editing && duoDotsCapsule(geo, pager.position.value, density).inflate(6f * density).contains(p)) return Hit.PageDots
                if (duoLibAmount() < 0.5f && (geo.unfolded || pager.position.value > -0.5f) && !model.editing && geo.pillRect.inflate(8f * density).contains(p)) return Hit.Pill
            }
            if (pager.position.value < 0f && p.x < -pager.position.value * geo.sideStride) {
                if (model.editing) {
                    val off = Offset((-1f - pager.position.value) * geo.sideStride, -model.todayScroll.value)
                    // Resize handle bottom right (before the badge), like on the home screen
                    TodayLayout.place(model.today, model::sizeOf, geo, density).firstOrNull { (_, r) ->
                        (r.bottomRight + off - p).getDistance() <= DesignTokens.widgetHandleHit.value * density
                    }?.let { return Hit.WidgetHandle(it.first) }
                    TodayLayout.place(model.today, model::sizeOf, geo, density).firstOrNull { (_, r) ->
                        val c = r.topLeft + off + Offset(DesignTokens.badgeOffsetX.value * density, DesignTokens.badgeOffsetY.value * density)
                        (c - p).getDistance() <= geo.badgeHitRadius
                    }?.let { return Hit.Badge(it.first) }
                }
                return Hit.Today
            }
            if (geo.duo && duoLibAmount() < 0.5f && (geo.unfolded || pager.position.value > -0.5f) && !model.editing && geo.pillRect.inflate(8f * density).contains(p)) return Hit.Pill
            if (libEnabledNow()) {
                val libOffset = geo.libraryOffset(libIndexNow() - pager.position.value)
                // only if the App Library is really in view — Duo unfolded it otherwise starts at 2 × 448 pt, i.e.
                // exactly under the dock (taps on the dock landed in the invisible App Library)
                if (libraryOnScreen && libOffset < w && p.x >= libOffset) {
                    return Hit.Library(io.github.janhurn9400.liquidy.ui.library.libraryHitTest(model, libGeo, p, libOffset))
                }
                // Duo: left of the open App Library is the blurred page — nothing may be tapped there (user 10-15)
                if (geo.duo && libraryOnScreen && duoLibAmount() > LIB_COVERS_AT && p.x < libOffset) return Hit.Covered
            }
            if (model.editing) {
                if (geo.editButtonRect(left = false).contains(p)) return Hit.EditDone
                if (geo.editButtonRect(left = true).contains(p)) return Hit.EditCustomize
            }
            // Duo: magnifier is in the right column → check before icons/App Library (otherwise a cell covers it)
            if (geo.duo && duoLibAmount() < 0.5f && (geo.unfolded || pager.position.value > -0.5f) && !model.editing && geo.pillRect.inflate(8f * density).contains(p)) return Hit.Pill
            val icons = buildList {
                val pos = pager.position.value
                for (page in floor(pos).toInt()..(ceil(pos).toInt() + geo.pagesVisible - 1)) {
                    val cells = model.displayCells.getOrNull(page)
                    display.pages.getOrNull(page)?.forEachIndexed { slot, key ->
                        val cell = cells?.getOrNull(slot) ?: -1
                        if (cell >= 0) add(key to geo.slotPosition(cell) + Offset(pageOffsetPx(page), 0f))
                    }
                }
                display.dock.forEachIndexed { i, key -> add(key to geo.dockPosition(i, display.dock.size)) }
            }
            if (model.editing) {
                // Resize handle bottom right on widgets (before the badge/icon hit)
                icons.firstOrNull { (id, tl) ->
                    val w = model.widgets[id] ?: return@firstOrNull false
                    val r = geo.widgetRect(tl, w.cols, w.rows)
                    (r.bottomRight - p).getDistance() <= DesignTokens.widgetHandleHit.value * density
                }?.let { return Hit.WidgetHandle(it.first) }
                icons.firstOrNull { (id, tl) -> !isFolder(id) && (geo.badgeCenter(if (isWidget(id)) tl + geo.widgetOffset else tl) - p).getDistance() <= geo.badgeHitRadius }
                    ?.let { return Hit.Badge(it.first) }
            }
            icons.firstOrNull { (id, tl) ->
                val w = model.widgets[id]
                if (w != null) geo.widgetRect(tl, w.cols, w.rows).contains(p) else geo.cellHitRect(tl).contains(p)
            }?.let { return Hit.Icon(it.first, it.second) }
            if (!model.editing && geo.pillRect.contains(p)) return Hit.Pill
            if (model.editing && geo.pillRect.contains(p)) return Hit.PageDots // iOS: dots in edit mode → page overview
            return Hit.Empty
        }

        /** App or folder icon; the open folder is replaced by its panel. */
        @Composable
        fun Item(key: String, showLabel: Boolean, inMenu: Boolean = false) {
            // In the context menu a copy floats above the dimming → hide the original
            if (!inMenu && model.contextMenu?.key == key) return
            // App window is flying into this icon right now → the system's icon surface stands in its place.
            // Widget: hide only the surface, the name stays (otherwise it appears at the end with a hard cut)
            // also: widget is turning into the settings sheet (the copy in the sheet stands in its place)
            val flyingHere = !inMenu && (model.flyingKey == key || model.widgetConfigKey == key)
            if (flyingHere && !isWidget(key)) return
            if (!inMenu && model.stackEditKey == key) return
            // App is growing out of this icon/widget (open animation) → slot is empty like on iOS
            if (!inMenu && io.github.janhurn9400.liquidy.data.AppLaunchTransition.launchingKey.value == key) return
            val press = { if (model.pressedKey == key) model.pressAlpha.value else 0f }
            val merge = model.drag?.mergeTarget == key
            Box(Modifier.graphicsLayer {
                // M-CTXMENU: icon grows while holding (around the icon centre, not the cell centre with label)
                val s = if (model.pressedKey == key || model.pressScaleKey == key) model.pressScale.value else 1f
                scaleX = s; scaleY = s
                transformOrigin = TransformOrigin(0.5f, DesignTokens.iconSize.value / 2 / (size.height / density))
            }) {
            if (isWidget(key)) {
                val spec = model.widgets[key] ?: return@Box
                val landing = model.widgetLanding?.takeIf { it.key == key }
                Box(Modifier.graphicsLayer {
                    // Flight from the picker: from preview centre to slot centre, lands at ≈ 1.12× and settles to 1.0
                    landing ?: return@graphicsLayer
                    alpha = 0f // the flying copy (above everything) does the whole motion; afterwards the real widget is there
                }) {
                WidgetCell(key, spec, geo, density, wallpaper, jiggle = { model.jiggleAmount.value }, clockSeconds = { clock.floatValue },
                    badgeScale = { model.badgeOf(key) }, showBadge = model.editing || model.badgeScale.value > 0f,
                    pressAlpha = press, resize = model.resize?.takeIf { it.key == key }, showLabel = showLabel, hideBody = flyingHere, stackModel = model)
                }
            } else if (isFolder(key)) {
                val folder = model.folders[key] ?: return
                // Open folder: icon crossfades into the panel in its smallest phase (instead of disappearing hard)
                val open = model.openFolder?.takeIf { it.id == key }
                FolderIconView(
                    modifier = Modifier.graphicsLayer { alpha = if (open == null) 1f else 1f - model.folderVisible(open) },
                    id = key, folder = folder, apps = model.apps, showLabel = showLabel, wallpaper = wallpaper,
                    pressAlpha = press, jiggle = { model.jiggleAmount.value }, clockSeconds = { clock.floatValue },
                    mergeHint = merge,
                    hiddenApp = model.folderFly?.takeIf { it.folder == key }?.key,
                )
            } else {
                val app = model.apps[key] ?: return
                Box {
                    Box(Modifier.offset(x = (DesignTokens.labelWidth.dp - DesignTokens.iconSize.dp) / 2)) {
                        MergeHintBackdrop(merge, wallpaper)
                    }
                    AppIconView(
                        app = app,
                        showLabel = showLabel,
                        wallpaper = wallpaper,
                        pressAlpha = press,
                        jiggle = { model.jiggleAmount.value },
                        clockSeconds = { clock.floatValue },
                        badgeScale = { model.badgeOf(key) },
                        showBadge = model.editing || model.badgeScale.value > 0f,
                    )
                }
            }
        }
        }

        val openFolder = model.openFolder
        val folderGeo = remember(w, h, density, geo, openFolder?.iconTopLeft?.x) { FolderGeometry(density, w, h, geo, openFolder?.iconTopLeft?.x) }

        // Background blur (px) for folders (T-FOLDER-BG) and search (M-SEARCH.blurRamp, T-SEARCH.blur/blurOpen)
        /** Only the search share of the background blur (px). */
        fun searchBlurPx(): Float {
            val ramp = (model.searchProgress.value / Motion.searchBlurRamp.value).coerceIn(0f, 1f)
            return maxOf(ramp * DesignTokens.searchBlur.value, model.searchOpenAmount.value.coerceIn(0f, 1f) * DesignTokens.searchBlurOpen.value) * density
        }
        fun backgroundBlurPx(includeToday: Boolean = false, withSearch: Boolean = true): Float {
            val pf = model.openFolder?.progress?.value ?: 0f
            val hs = model.searchProgress.value
            val ramp = (hs / Motion.searchBlurRamp.value).coerceIn(0f, 1f)
            val searchBlur = maxOf(ramp * DesignTokens.searchBlur.value, model.searchOpenAmount.value.coerceIn(0f, 1f) * DesignTokens.searchBlurOpen.value)
            val cat = (model.openCategory?.progress?.value ?: 0f) * DesignTokens.libCatBlur.value
            // Today view/App Library blur only the wallpaper, not the icons sliding out (recording8 4.2 s)
            val today = if (includeToday) sideBlur() * DesignTokens.todayBlur.value else 0f
            // App open/close: sharp/blur OFF for now (user: check more closely later) — see M-HOME-RETURN/M-APP-OPEN
            // Edit stack: home screen blurred in ≈ 2 frames (frame 287→288), independent of the slower panel
            val stackEdit = (model.stackEdit.value * 4f).coerceIn(0f, 1f) * DesignTokens.folderBgBlur.value
            val config = model.widgetConfig.value * DesignTokens.folderBgBlur.value // widget settings: home screen blurred
            if (io.github.janhurn9400.liquidy.tuning.DebugOptions.blurOff) return 0f
            return maxOf(pf * DesignTokens.folderBgBlur.value, if (withSearch) searchBlur else 0f, cat, today, stackEdit, config) * density
        }

        // Background = always the Android wallpaper behind the transparent window (no own launcher image).
        // Blur (folder, search, Today, App Library) is done by Android itself: window background blur blurs its wallpaper.
        val window = remember(view) {
            var c = view.context
            while (c is android.content.ContextWrapper && c !is android.app.Activity) c = c.baseContext
            (c as? android.app.Activity)?.window
        }
        LaunchedEffect(window) { window?.setBackgroundBlurRadius(0) }
        // At rest Android shows its wallpaper behind the transparent window. As soon as the background should blur
        // (folder, search, Today, App Library …) we lay our congruent copy (= Android's crop) blurred
        // on top — that looks the same on every device (Android's window blur isn't available everywhere).
        // We always draw ourselves when we don't know Android's image ([drawWallpaper]) and in the editor.
        // Key: otherwise the computation from the first composition (e.g. unfolded/folded) would stay frozen
        val blurNow by remember(geo, screenIsWide) { derivedStateOf { backgroundBlurPx(includeToday = true) > 0.5f } }
        val forceCopy = io.github.janhurn9400.liquidy.tuning.DebugOptions.wallpaperCopy.value >= 0.5f
        if (drawWallpaper || blurNow || forceCopy) Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val r = maxOf(backgroundBlurPx(includeToday = true),
                        wallpaperBlur() * io.github.janhurn9400.liquidy.glass.WallpaperRenderer.PREVIEW_BLUR_RADIUS_PX)
                    renderEffect = if (r > 0.5f) BlurEffect(r, r, TileMode.Clamp) else null
                }
        ) {
            drawImage(wallpaper)
            val k = wallpaperBlur()
            if (k > 0f) drawRect(Color.Black.copy(alpha = io.github.janhurn9400.liquidy.glass.WallpaperRenderer.PREVIEW_DIM * k))
        }

        // Customise › sun: wallpaper dimmed in dark mode (iOS) — below everything else
        val ia = io.github.janhurn9400.liquidy.design.IconAppearance
        if (ia.dimWallpaper && ia.systemDark) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = DesignTokens.WALLPAPER_DARK_DIM)))

        // Home content (icons, dock, pill): blurred + shifted/shrunk for search. Decal = transparent outside.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // Folding: first only dimmed+blurred, from 62 % of the sequence it sharpens
                    val f = foldIn.value
                    val foldBlur = (f / FOLD_SHARPEN_SHARE).coerceIn(0f, 1f) * FOLD_BLUR_DP * density
                    // Duo: during search the dock column stays sharp and still [video 5, 13.1 s] → search blur/offset
                    // then only on pages + Today (below), not on this shared layer
                    val r = maxOf(backgroundBlurPx(withSearch = !geo.duo), foldBlur)
                    renderEffect = if (r > 0.5f) BlurEffect(r, r, TileMode.Decal) else null
                    alpha = overviewHide()
                    if (!geo.duo) {
                        val hs = model.searchProgress.value // home offset h (pt)
                        translationY = hs * density
                        val sc = model.searchContentScale()
                        scaleX = sc; scaleY = sc
                    }
                }
        ) {

        // ── Page icons (+ App Library): source of the glass ──────────────────────────
        Box(Modifier.fillMaxSize().layerBackdrop(glassBackdrop)) {
        val visiblePages by remember(pager, geo) {
            derivedStateOf { floor(pager.position.value).toInt()..(ceil(pager.position.value).toInt() + geo.pagesVisible - 1) }
        }
        // Page overview: hide icons already INSIDE the glass source — otherwise the glass cards refract the real page
        Box(Modifier.fillMaxSize().graphicsLayer {
            alpha = overviewHide()
            // Duo unfolded: page left of the App Library blurs (recording 35.5 s)
            val lb = maxOf(if (geo.unfolded) duoLibAmount() * DUO_LIB_PAGE_BLUR * density else 0f, if (geo.duo) searchBlurPx() else 0f)
            renderEffect = if (lb > 0.5f) BlurEffect(lb, lb, TileMode.Decal) else null
            if (geo.duo) {
                translationY = model.searchProgress.value * density
                val sc = model.searchContentScale(); scaleX = sc; scaleY = sc
            }
        }) {
        display.pages.forEachIndexed { page, keys ->
            if (page in visiblePages) Box(Modifier.fillMaxSize().graphicsLayer {
                // Page overview: every visible page zooms with ITS card (Duo: left and right at once)
                // Always read progress → block runs every frame, even if the card is registered only one frame later
                val prog = model.pagesOverview.value
                val z = if (model.pagesOverviewOpen && prog > 0f) model.overviewZoom[page]?.invoke() else null
                if (z != null && z.second.width > 0f) {
                    val (card, full) = z
                    val sc = card.width / full.width
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                    scaleX = sc; scaleY = sc
                    translationX = card.left - full.left * sc; translationY = card.top - full.top * sc
                }
            }) {
                val cells = model.displayCells.getOrNull(page)
                keys.forEachIndexed { slot, key ->
                    val cell = cells?.getOrNull(slot) ?: -1
                    if (cell < 0) return@forEachIndexed
                    // key(): composition follows the icon, not the list slot → reordering animates correctly
                    if (key != model.drag?.key) key(key) {
                        IconSlot(model, key, Container.Page(page), geo.slotPosition(cell), pager, geo::pageShift, density) {
                            Item(key, showLabel = true)
                        }
                    }
                }
            }
        }

        } // end overview fade

        // ── App Library ─────────────────────────────────────────────────────────
        // Duo: draw the App Library only AFTER the dock (below) — the dock lies behind it and blurs along
        // Stays composed while off screen (only not drawn): composing ~10 tiles with ~70 icons on every swipe into the
        // App Library cost several janky frames each time (measured on a Snapdragon 780G, 10-18)
        if (libEnabledNow() && !geo.duo) Box(Modifier.fillMaxSize().graphicsLayer { alpha = if (libIndexNow() in visiblePages) 1f else 0f }) {
            io.github.janhurn9400.liquidy.ui.library.LibraryPage(model, libGeo, wallpaper, density) { geo.libraryOffset(libIndexNow() - pager.position.value) }
        }
        } // end glass source

        // ── App Library: scroll edge + glass search field (outside the source so it refracts the tiles) ──
        if (libEnabledNow() && libraryOnScreen && !geo.duo) {
            io.github.janhurn9400.liquidy.ui.library.LibraryChrome(model, libGeo, density) { geo.libraryOffset(libIndexNow() - pager.position.value) }
        }

        // Duo search [MEASURED video 5, 12.5 → 13.1 s]: background (also behind the dock) ≈ 40 % darker, dock icons
        // unchanged bright → dim here, below the dock
        if (geo.duo && model.searchVisible) Canvas(Modifier.fillMaxSize()) {
            drawRect(Color.Black.copy(alpha = model.searchDim() * (DUO_SEARCH_DIM / DesignTokens.searchDimMax.value)))
        }

        // ── Dock ────────────────────────────────────────────────────────────────
        // Duo unfolded, App Library (recording 10:40, 15.3–16.2 s): dock including glass stays and blurs like the
        // page next to it – it lies BELOW the incoming App Library layer; category open (16.79–16.92 s): dock slides out to the right
        Box(Modifier.fillMaxSize().graphicsLayer {
            // Duo: with the A–Z list open the index sits in the dock column → dock away [video 2, 18 s]
            alpha = homeChromeAlpha() * (1f - duoWpeHide) * (if (geo.duo && model.libListOpen) (1f - model.libList.value).coerceIn(0f, 1f) else 1f)
            val lib = duoLibAmount(); val cat = duoCatAmount()
            // Category or (folded) open search push the dock away to the right
            // unfolded: dock slides out to the right immediately when pulling down to search (follows the finger, fully out at
            // half dimming) and back on close [Duo, user 10-11]; folded only delayed (duoSearchDockOut)
            val unfoldedSearch = if (geo.duo && geo.unfolded && model.searchVisible)
                (model.searchDim() / (DesignTokens.searchDimMax.value * DUO_SEARCH_DOCK_FULL_AT)).coerceIn(0f, 1f).let { it * it * (3f - 2f * it) } else 0f
            translationX = maxOf(cat, duoSearchDockOut.value, unfoldedSearch) * (size.width - geo.dockRect.left + DUO_DOCK_OUT_MARGIN * density).coerceAtLeast(DUO_CAT_DOCK_SHIFT * density)
            val b = lib * DUO_LIB_PAGE_BLUR * density
            renderEffect = if (b > 0.5f) BlurEffect(b, b, TileMode.Decal) else null
        }) {
        LiquidGlass(
            // Duo: narrow vertical glass bar with its own radius (tile corners ≈ 22 pt)
            shape = glassRect(if (geo.duo) androidx.compose.ui.unit.Dp(DUO_DOCK_CORNER) else DesignTokens.dockCorner.dp),
            // Duo: clear light rim all around (reference 10:40, 13 s)
            rimAlpha = if (geo.duo) DUO_DOCK_RIM else null,
            onWallpaper = true, // nothing moves under the dock
            // Read the rect only in layout: the spring then runs without recomposing the home screen
            modifier = Modifier.layout { m, _ ->
                val r = geo.dockRectFor(dockSlots.value)
                val p = m.measure(androidx.compose.ui.unit.Constraints.fixed(r.width.roundToInt(), r.height.roundToInt()))
                layout(p.width, p.height) { p.place(r.left.roundToInt(), r.top.roundToInt()) }
            },
        )
        display.dock.forEachIndexed { i, key ->
            if (key != model.drag?.key) key(key) {
                IconSlot(model, key, Container.Dock, geo.dockPosition(i, display.dock.size), pager, geo::pageShift, density) {
                    // Duo: dock icons smaller than the grid (≈ 48 instead of 60 pt, lots of glass around) [MEASURED] 21:29, 44 s
                    if (geo.duo) Box(Modifier.graphicsLayer {
                        val sc = geo.dockIconScale; scaleX = sc; scaleY = sc
                        transformOrigin = TransformOrigin(0.5f, DesignTokens.iconSize.value / 2 / (size.height / density))
                    }) { Item(key, showLabel = false) } else Item(key, showLabel = false)
                }
            }
        }

        } // end dock

        // Duo (unfolded and folded): App Library as a layer ABOVE the (blurred) dock
        // own glass source so the search field keeps refracting the tiles (it lies outside the main source)
        if (libEnabledNow() && geo.duo) {
            if (libraryOnScreen) Box(Modifier.fillMaxSize().then(if (libPageBackdrop != null) Modifier.layerBackdrop(libPageBackdrop) else Modifier)) {
                io.github.janhurn9400.liquidy.ui.library.LibraryPage(model, libGeo, wallpaper, density) { geo.libraryOffset(libIndexNow() - pager.position.value) }
            }
        }

        // ── Edit buttons at the top ─────────────────────────────────────────────────────
        if (model.editing || model.badgeScale.value > 0f) {
            // Duo: the brush itself becomes the droplet → while the menu is open there is no button there (video 4)
            EditButtons(geo, wallpaper, density, showLeft = !(geo.duo && model.editMenuOpen),
                popIn = if (geo.duo) ({ model.editButtons.value }) else null) { model.badgeScale.value }
        }
        // ── Today view (left of page 0) — inside the home content: when pulling down for search it slides
        // down the same way, shrinks and blurs like the icons (recording8 5–7 s)
        val todayVisible by remember(pager) { derivedStateOf { pager.position.value < -0.001f } }
        if (todayVisible) Box(Modifier.fillMaxSize().zIndex(-1f).graphicsLayer {
            if (geo.duo) {
                val b = searchBlurPx(); renderEffect = if (b > 0.5f) BlurEffect(b, b, TileMode.Decal) else null
                translationY = model.searchProgress.value * density
                val sc = model.searchContentScale(); scaleX = sc; scaleY = sc
            }
        }) { TodayPage(model, geo, density, wallpaper, clockSeconds = { clock.floatValue }) { (-1f - pager.position.value) * geo.sideStride } }
        } // end home content

        // Folding: dimming (measured brightness 105 → 142, i.e. ≈ 26 % darker at the start), brightening linearly
        if (foldIn.value > 0f) Canvas(Modifier.fillMaxSize()) { drawRect(Color.Black.copy(alpha = FOLD_DIM * foldIn.value)) }

        // Search: dimming proportional to progress (T-SEARCH.dim)
        if (model.searchVisible) {
            // Duo: dimming lies BELOW the dock (see before "Dock") — here iPhone only
            if (!geo.duo) Canvas(Modifier.fillMaxSize()) { drawRect(Color.Black.copy(alpha = model.searchDim())) }
        }

        // ── Pill / dots / search field morph — outside the blurred layer (stays sharp while dragging, recording5) ──
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // In the Today view the pill is gone, but the search field appears when pulling down (morph)
                    alpha = maxOf(homeChromeAlpha(), model.searchMorph.value.coerceIn(0f, 1f)) * overviewHide() *
                        (1f - model.stackEdit.value.coerceIn(0f, 1f)) // Edit Stack: pill gone
                    // blurred with the home screen when a folder is open (IMG_3130)
                    val r = (model.openFolder?.progress?.value ?: 0f) * DesignTokens.folderBgBlur.value * density
                    renderEffect = if (r > 0.5f) BlurEffect(r, r, TileMode.Decal) else null
                }
        ) {
            // Duo: in the App Library magnifier + dots disappear (recording 10:40, from 15.6 s – the App Library search field is at the top)
            // folded: on the Today page dots + magnifier are gone [MEASURED 19:07, 5 s]
            if (geo.duo) Box(Modifier.fillMaxSize().graphicsLayer {
                val today = if (geo.unfolded) 0f else (-pager.position.value).coerceIn(0f, 1f)
                alpha = (1f - duoLibAmount()) * (1f - duoWpeHide) * (1f - today)
            }) {
                // Dots belong to the home content: when pulling down to search they slide, shrink and blur with
                // the pages (same layer values as the pages) [Duo, user 10-11]
                Box(Modifier.fillMaxSize().graphicsLayer {
                    val b = searchBlurPx(); renderEffect = if (b > 0.5f) BlurEffect(b, b, TileMode.Decal) else null
                    translationY = model.searchProgress.value * density
                    val sc = model.searchContentScale(); scaleX = sc; scaleY = sc
                }) { DuoDots(geo, pager, density, dotCount = display.pages.size.coerceAtLeast(1)) { model.badgeScale.value } }
                DuoSearchButton(model, geo, density)
            }
            SearchPill(
                model = model,
                pager = pager,
                screenWidth = pxToDp(w, density),
                screenHeight = pxToDp(h, density),
                y = pxToDp(geo.pillTop, density),
                forceDots = model.editing,
                dotCount = display.pages.size + if (dragging && display.pages.lastOrNull()?.isNotEmpty() == true) 1 else 0,
                duoButton = if (geo.duo) geo.pillRect else null,
                duoFieldWidth = pxToDp(geo.duoSearchWidth, density),
                duoFieldCenterX = pxToDp(geo.duoSearchCenterX, density),
                duoFieldRestY = pxToDp(geo.duoSearchRestY, density),
                onLaunch = onLaunch,
            )
        }

        // ── Edit Stack ─────────────────────────────────────────────────────
        model.stackEditKey?.let { sk ->
            Canvas(Modifier.fillMaxSize()) { drawRect(Color.Black.copy(alpha = 0.18f * (model.stackEdit.value * 4f).coerceIn(0f, 1f))) }
            StackEditor(model, sk, geo, density, wallpaper, w, h)
        }

        // ── Page overview ("Edit Pages") ────────────────────────────────
        LaunchedEffect(model.pagesOverviewRequested) {
            if (model.pagesOverviewRequested) { model.pagesOverviewRequested = false; openOverview() }
        }
        if (model.pagesOverviewOpen) {
            PageOverview(model, geo, density, wallpaper, w, h) { page ->
                // Duo unfolded: if the page was visible on the right, it stays on the right (no jump to the left) — card zooms there
                // (real position: −1 = Today left, page 0 right)
                val right = geo.unfolded && page == pager.position.value.roundToInt() + 1
                model.overviewLeftPage = if (right) page - 1 else page
                scope.launch { pager.position.snapTo((if (right) page - 1 else page).toFloat()) }
            }
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = model.pagesOverview.value }) {
                EditButtons(geo, wallpaper, density, showLeft = false) { 1f }
            }
        }

        // ── Open folder ────────────────────────────────────────────────────────
        if (openFolder != null) {
            Canvas(Modifier.fillMaxSize()) {
                drawRect(Color.Black.copy(alpha = DesignTokens.folderBgDim.value * openFolder.progress.value.coerceIn(0f, 1f)))
            }
            FolderOverlay(model, openFolder, folderGeo, density, wallpaper) { clock.floatValue }
            FolderDragCopy(model, density, wallpaper)
        }

        // ── Dragged icon (above everything) ──────────────────────────────────────────
        model.drag?.let { d ->
            model.widgets[d.key]?.let { spec ->
                Box(
                    Modifier
                        .offset { val tl = d.pointer - d.grabOffset; IntOffset(cellOffsetX(tl.x, density).roundToInt(), tl.y.roundToInt()) }
                        .graphicsLayer { val s = d.lift.value; scaleX = s; scaleY = s }
                ) {
                    WidgetCell(d.key, spec, geo, density, wallpaper, jiggle = { 0f }, clockSeconds = { 0f }, badgeScale = { 0f }, showBadge = false, pressAlpha = { 0f })
                }
            }
            // App or folder (folders were missing here → nothing was visible while dragging)
            val app = model.apps[d.key]
            val folder = model.folders[d.key]
            if (app != null || folder != null) Box(
                Modifier
                    .offset {
                        val tl = d.pointer - d.grabOffset
                        IntOffset(cellOffsetX(tl.x, density).roundToInt(), tl.y.roundToInt())
                    }
                    .graphicsLayer {
                        val s = d.lift.value * dragDockScale.value
                        scaleX = s; scaleY = s
                        transformOrigin = TransformOrigin(0.5f, 0.5f)
                    }
            ) {
                if (app != null) AppIconView(
                    app = app, showLabel = false, wallpaper = wallpaper,
                    pressAlpha = { 0f }, jiggle = { 0f }, clockSeconds = { 0f },
                    badgeScale = { 0f }, showBadge = false,
                ) else if (folder != null) FolderIconView(
                    id = d.key, folder = folder, apps = model.apps, showLabel = false, wallpaper = wallpaper,
                    pressAlpha = { 0f }, jiggle = { 0f }, clockSeconds = { 0f }, mergeHint = false,
                    liveBackdrop = true,
                )
            }
        }

        // ── Pick-up: original fades out at the origin, its badge shrinks away (M-BADGE-DRAG) ──
        model.pickupGhost?.let { g ->
            val app = model.apps[g.key]; val folder = model.folders[g.key]
            Box(
                Modifier
                    .offset { IntOffset(cellOffsetX(g.topLeft.x, density).roundToInt(), g.topLeft.y.roundToInt()) }
                    .graphicsLayer {
                        alpha = g.fade.value
                        if (g.dock && geo.duo) {
                            val sc = geo.dockIconScale; scaleX = sc; scaleY = sc
                            transformOrigin = TransformOrigin(0.5f, DesignTokens.iconSize.value / 2 / (size.height / density))
                        }
                    }
            ) {
                if (app != null) AppIconView(app = app, showLabel = !g.dock, wallpaper = wallpaper, pressAlpha = { 0f },
                    jiggle = { 0f }, clockSeconds = { 0f }, badgeScale = { model.badgeScale.value * g.fade.value }, showBadge = model.editing)
                else if (folder != null) FolderIconView(id = g.key, folder = folder, apps = model.apps, showLabel = !g.dock,
                    wallpaper = wallpaper, pressAlpha = { 0f }, jiggle = { 0f }, clockSeconds = { 0f }, mergeHint = false)
            }
        }

        // ── Icon flies into the folder (M-MERGE-DROP): from the position at release to mini size into the free slot ──
        model.folderFly?.let { f ->
            model.apps[f.key]?.let { app ->
                Box(
                    Modifier
                        .offset {
                            val t = f.t.value
                            val c = f.from.center + (f.to.center - f.from.center) * t
                            IntOffset(cellOffsetX(c.x - geo.icon / 2, density).roundToInt(), (c.y - geo.icon / 2).roundToInt())
                        }
                        .graphicsLayer {
                            val t = f.t.value
                            val s = (f.from.width + (f.to.width - f.from.width) * t) / geo.icon
                            scaleX = s; scaleY = s
                            transformOrigin = TransformOrigin(0.5f, 0.5f)
                        }
                ) {
                    AppIconView(
                        app = app, showLabel = false, wallpaper = wallpaper,
                        pressAlpha = { 0f }, jiggle = { 0f }, clockSeconds = { 0f },
                        badgeScale = { 0f }, showBadge = false,
                    )
                }
            }
        }

        val arenaCoords = remember { arrayOfNulls<androidx.compose.ui.layout.LayoutCoordinates>(1) }
        fun arenaToWindow(p: Offset): Offset = arenaCoords[0]?.takeIf { it.isAttached }?.localToWindow(p) ?: p
        // ── Gesten-Arena ──────────────────────────────────────────────────────────
        // (disabled while the page overview is open — the overview below gets the touches)
        if (!model.pagesOverviewOpen && model.stackEditKey == null) Box(
            Modifier
                .fillMaxSize()
                .onGloballyPositioned { arenaCoords[0] = it }
                .pointerInput(geo) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (model.pendingRemoval != null) return@awaitEachGesture
                        model.openFolder?.let { of ->
                            // compute fresh: the gesture arena is created only once (otherwise position of the FIRST opened folder)
                            handleFolderGesture(model, of, FolderGeometry(density, w, h, geo, of.iconTopLeft.x), down, density, onLaunch, scope,
                                dragMove = { p -> iconDragMove(p) }, dragEnd = { iconDragEnd() })
                            return@awaitEachGesture
                        }
                        val t0 = System.nanoTime()
                        val hit = hitTest(down.position)
                        val wasAnimating = pager.position.isRunning
                        if (wasAnimating) scope.launch { pager.position.stop() }
                        val tracker = VelocityTracker().apply { addPosition(down.uptimeMillis, down.position) }
                        val slop = viewConfiguration.touchSlop
                        var mode = Mode.Pending
                        var acc = Offset.Zero
                        var longPressDone = false
                        var libScrollStart = 0f

                        var pressJob: Job? = null
                        fun releasePress(animated: Boolean) {
                            pressJob?.cancel()
                            if (model.pressScale.value != 1f) scope.launch {
                                model.pressScale.animateTo(1f, Motion.pressOut.spec()); model.pressScaleKey = null
                            }
                            if (model.pressedKey == null) return
                            if (animated) scope.launch {
                                model.pressAlpha.animateTo(0f, Motion.pressOut.spec()); model.pressedKey = null
                            } else scope.launch { model.pressAlpha.snapTo(0f); model.pressedKey = null }
                        }

                        /** Pull an app out of the App Library: edit mode, pager swipes to the last page (drop target). */
                        fun beginLibraryDrag(app: io.github.janhurn9400.liquidy.ui.library.LibraryHit.App, pointer: Offset) {
                            startLibraryDrag(app.app.key, pointer, app.screenRect.topLeft)
                            mode = Mode.Drag
                        }

                        fun beginIconDrag(icon: Hit.Icon, pointer: Offset) {
                            releasePress(animated = false)
                            model.enterEdit()
                            model.startDrag(icon.key, pointer, icon.iconTopLeft)
                            mode = Mode.Drag
                        }

                        if (hit is Hit.Today && !model.editing && !wasAnimating) {
                            // Today widget: capture the image for the return flight on touch down (like on the home screen)
                            todayWidgetAt(down.position)?.let { (k, r) ->
                                window?.let { io.github.janhurn9400.liquidy.data.AppLaunchTransition.snapshotWidget(it, k, android.graphics.RectF(r.left, r.top, r.right, r.bottom)) }
                            }
                        }
                        if (hit is Hit.Library && !model.editing && !wasAnimating) {
                            (hit.hit as? io.github.janhurn9400.liquidy.ui.library.LibraryHit.App)?.let { model.prefetchShortcuts(libraryMenuKey(it.app.key), density) }
                        }
                        // Android widget: forward the touch from the start (pressed state, scrolling); launcher can still take over
                        if (hit is Hit.Icon && !model.editing && !wasAnimating && model.widgets[hit.key]?.type == "android") {
                            model.widgets[hit.key]?.let { io.github.janhurn9400.liquidy.ui.widgets.AndroidWidgetTaps.begin(it.appWidgetId, arenaToWindow(down.position)) }
                        }
                        if (hit is Hit.Icon && !model.editing && !wasAnimating) {
                            // Widget: capture the image for the return flight NOW — the displayed image has no pressed dimming yet
                            // (on release it would be dark → surface flies back dark and the real widget flashes bright)
                            model.widgets[hit.key]?.let { spec ->
                                val r = geo.widgetRect(hit.iconTopLeft, spec.cols, spec.rows)
                                window?.let { io.github.janhurn9400.liquidy.data.AppLaunchTransition.snapshotWidget(it, hit.key, android.graphics.RectF(r.left, r.top, r.right, r.bottom)) }
                            }
                            model.pressedKey = hit.key
                            model.pressScaleKey = hit.key
                            model.prefetchShortcuts(hit.key, density)
                            // M-CTXMENU: grow slowly, then fade out the dark (before the menu)
                            pressJob = scope.launch {
                                // Widgets don't grow while holding (recording8 12.3–13.3 s: scale stays 1.0)
                                if (!isWidget(hit.key)) launch {
                                    delay(Motion.ctxGrowDelay.value.toLong())
                                    model.pressScale.animateTo(Motion.ctxPressScale.value,
                                        androidx.compose.animation.core.tween(Motion.ctxGrowDuration.value.roundToInt(), easing = androidx.compose.animation.core.LinearEasing))
                                }
                                delay(Motion.ctxPressFadeDelay.value.toLong())
                                if (model.pressedKey == hit.key) model.pressAlpha.animateTo(0f, androidx.compose.animation.core.tween(Motion.ctxPressFade.value.roundToInt()))
                            }
                            scope.launch {
                                val d = Motion.pressInDelay.value.roundToInt()
                                if (d > 0) delay(d.toLong())
                                // Android widgets show their own pressed effect (touch is forwarded) → don't dim additionally
                                if (model.pressedKey == hit.key && model.widgets[hit.key]?.type != "android") model.pressAlpha.snapTo(DesignTokens.pressDarkAlpha.value)
                            }
                        }

                        while (true) {
                            val waitForLongPress = mode == Mode.Pending && !longPressDone && !wasAnimating
                            val event = if (waitForLongPress) {
                                val hold = if (hit is Hit.Icon && !model.editing) Motion.ctxDelay.value else Motion.longPress.value
                                val remaining = hold.toLong() - (System.nanoTime() - t0) / 1_000_000
                                withTimeoutOrNull(remaining.coerceAtLeast(1)) { awaitPointerEvent() }
                            } else awaitPointerEvent()
                            if (event == null) {
                                longPressDone = true
                                io.github.janhurn9400.liquidy.ui.widgets.AndroidWidgetTaps.cancel() // hold → launcher menu, no click in the widget
                                when {
                                    hit is Hit.Icon && model.editing -> beginIconDrag(hit, down.position)
                                    hit is Hit.Icon -> {
                                        pressJob?.cancel()
                                        model.openContextMenu(hit.key, hit.iconTopLeft, density,
                                            model.widgets[hit.key]?.let { geo.widgetRect(hit.iconTopLeft, it.cols, it.rows) },
                                            iconScale = if (hit.key in model.display.dock) geo.dockIconScale else 1f)
                                        mode = Mode.Menu
                                    }
                                    hit is Hit.Empty && !model.editing -> { model.enterEdit(); mode = Mode.Consumed }
                                    hit is Hit.Today -> {
                                        val tw = todayWidgetAt(down.position)
                                        when {
                                            tw == null -> { model.enterEdit(); mode = Mode.Consumed } // empty spot → edit
                                            model.editing -> { model.beginTodayDrag(tw.first); mode = Mode.TodayDrag } // jiggling → drag
                                            else -> { // context menu with size bar (like home screen)
                                                model.openContextMenu(tw.first, tw.second.topLeft - geo.widgetOffset, density, tw.second)
                                                mode = Mode.Menu
                                            }
                                        }
                                    }
                                    // App Library: long-press an app → menu (delete, info, shortcuts); dragging brings it to the home screen
                                    hit is Hit.Library && hit.hit is io.github.janhurn9400.liquidy.ui.library.LibraryHit.App && !model.editing -> {
                                        val a = hit.hit as io.github.janhurn9400.liquidy.ui.library.LibraryHit.App
                                        // own menu key: otherwise the same icon on the home screen disappears while the menu is open
                                        model.openContextMenu(libraryMenuKey(a.app.key), a.screenRect.topLeft, density, fromLibrary = true)
                                        mode = Mode.Menu
                                    }
                                    hit is Hit.Library -> Unit
                                }
                                continue
                            }
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                when (mode) {
                                    Mode.Pending -> when {
                                        hit is Hit.Library -> when (val lh = hit.hit) {
                                            is io.github.janhurn9400.liquidy.ui.library.LibraryHit.App -> {
                                                model.launchedIconKey = null
                                                onLaunch(lh.app, lh.screenRect)
                                                model.libraryLaunch = lh.app.key to lh.screenRect // after onLaunch (it resets it otherwise)
                                            }
                                            is io.github.janhurn9400.liquidy.ui.library.LibraryHit.Category -> model.openCategory(lh.index)
                                            io.github.janhurn9400.liquidy.ui.library.LibraryHit.Search -> model.openLibList()
                                            io.github.janhurn9400.liquidy.ui.library.LibraryHit.Empty -> if (wasAnimating) scope.launch { pager.settle() }
                                        }
                                        hit is Hit.Pill -> if (geo.duo) model.openSearchFromDuoLupe() else model.openSearch() // iOS: tapping "Search" opens search
                                        hit is Hit.Today && model.editing -> todayWidgetAt(down.position).let { tw ->
                                            if (tw == null) model.exitEdit()
                                            else if (io.github.janhurn9400.liquidy.ui.widgets.hasWidgetConfig(model.widgets[tw.first])) model.openWidgetConfig(tw.first, tw.second)
                                        }
                                        hit is Hit.Today -> {
                                            // Tap a widget in the Today view → open app
                                            val local = down.position - Offset((-1f - pager.position.value) * geo.sideStride, -model.todayScroll.value)
                                            TodayLayout.place(model.today, model::sizeOf, geo, density).firstOrNull { it.second.contains(local) }
                                                ?.let { (k, rr) -> model.widgets[k]?.let { spec ->
                                                    if (spec.type == "android") {
                                                        io.github.janhurn9400.liquidy.ui.widgets.AndroidWidgetTaps.tap(spec.appWidgetId, arenaToWindow(down.position))
                                                        return@let
                                                    }
                                                    val off = Offset((-1f - pager.position.value) * geo.sideStride, -model.todayScroll.value)
                                                    io.github.janhurn9400.liquidy.ui.widgets.openWidgetApp(view.context, spec.type,
                                                        android.graphics.RectF(rr.left + off.x, rr.top + off.y, rr.right + off.x, rr.bottom + off.y), k)
                                                } }
                                        }
                                        hit is Hit.Badge && isWidget(hit.key) -> model.removeWidget(hit.key)
                                        hit is Hit.Badge -> model.pendingRemoval = hit.key
                                        hit is Hit.EditDone -> model.exitEdit()
                                        hit is Hit.PageDots -> openOverview()
                                        hit is Hit.EditCustomize -> model.openEditMenu(duo = geo.duo)
                                        hit is Hit.Icon && model.editing && io.github.janhurn9400.liquidy.ui.widgets.hasWidgetConfig(model.widgets[hit.key]) ->
                                            screenRectOf(hit.key)?.let { r -> model.openWidgetConfig(hit.key, r) }
                                        hit is Hit.Icon && isFolder(hit.key) -> {
                                            releasePress(animated = false)
                                            model.openFolder(hit.key, hit.iconTopLeft, if (hit.key in model.display.dock) geo.dockIconScale else 1f)
                                        }
                                        hit is Hit.Icon && !model.editing && model.pressedKey == hit.key -> {
                                            releasePress(animated = false)
                                            model.apps[hit.key]?.let {
                                                model.launchedIconKey = hit.key
                                                // Duo dock: smaller icon (48 pt) → window grows from its real size
                                                val r = Rect(hit.iconTopLeft, Size(geo.icon, geo.icon))
                                                val k = if (hit.key in model.display.dock) geo.dockIconScale else 1f
                                                onLaunch(it, if (k == 1f) r else Rect(r.center - Offset(r.width * k / 2, r.height * k / 2), Size(r.width * k, r.height * k)))
                                            }
                                            model.shownWidget(hit.key)?.let { spec ->
                                                if (spec.type == "android") { // gesture layer is on top → forward the touch into the widget
                                                    if (io.github.janhurn9400.liquidy.ui.widgets.AndroidWidgetTaps.isActive) io.github.janhurn9400.liquidy.ui.widgets.AndroidWidgetTaps.end(arenaToWindow(change.position))
                                                    else io.github.janhurn9400.liquidy.ui.widgets.AndroidWidgetTaps.tap(spec.appWidgetId, arenaToWindow(down.position))
                                                    return@let
                                                }
                                                val r = geo.widgetRect(hit.iconTopLeft, spec.cols, spec.rows)
                                                val rf = android.graphics.RectF(r.left, r.top, r.right, r.bottom)
                                                io.github.janhurn9400.liquidy.ui.widgets.openWidgetApp(view.context, spec.type, rf, hit.key)
                                            }
                                        }
                                        hit is Hit.Empty && model.editing -> model.exitEdit()
                                        wasAnimating -> scope.launch { pager.settle() }
                                    }
                                    Mode.WidgetTouch -> io.github.janhurn9400.liquidy.ui.widgets.AndroidWidgetTaps.end(arenaToWindow(change.position))
                                    Mode.Paging -> {
                                        val v = tracker.calculateVelocity().x
                                        scope.launch { pager.endDrag(-v / pw, pw, density) }
                                    }
                                    Mode.Drag -> iconDragEnd()
                                    Mode.Vertical -> model.searchRelease()
                                    Mode.LibPull -> model.libListRelease()
                                    Mode.Resize -> model.endResize(geo)
                                    Mode.StackScroll -> (hit as? Hit.Icon)?.let { hk -> model.widgets[hk.key]?.let { sp ->
                                        model.stackDragEnd(hk.key, tracker.calculateVelocity().y, geo.widgetSize(sp.cols, sp.rows).height) } }
                                    Mode.TodayDrag -> model.endTodayDrag()
                                    Mode.TodayScroll -> {
                                        model.todayTouching = false
                                        val v = -tracker.calculateVelocity().y
                                        scope.launch { flingScroll(model.todayScroll, todayMaxScroll(), v) }
                                    }
                                    Mode.LibScroll -> {
                                        val v = -tracker.calculateVelocity().y
                                        scope.launch { flingLibrary(model, libGeo.maxScroll(model.library.size), v) }
                                    }
                                    else -> Unit
                                }
                                releasePress(animated = true)
                                break
                            }
                            tracker.addPosition(change.uptimeMillis, change.position)
                            val delta = change.positionChange()
                            when (mode) {
                                // Menu open and finger keeps dragging → menu away, edit mode + drag icon (like iOS)
                                Mode.Menu -> if (hit is Hit.Icon && (change.position - down.position).getDistance() > slop) {
                                    model.closeContextMenu(animated = false)
                                    beginIconDrag(hit, change.position)
                                } else if (hit is Hit.Library && (change.position - down.position).getDistance() > slop) {
                                    (hit.hit as? io.github.janhurn9400.liquidy.ui.library.LibraryHit.App)?.let { beginLibraryDrag(it, change.position) }
                                }
                                Mode.WidgetTouch -> io.github.janhurn9400.liquidy.ui.widgets.AndroidWidgetTaps.move(arenaToWindow(change.position))
                                Mode.Pending -> {
                                    acc += delta
                                    if (acc.getDistance() > slop && io.github.janhurn9400.liquidy.ui.widgets.AndroidWidgetTaps.isActive && io.github.janhurn9400.liquidy.ui.widgets.AndroidWidgetTaps.canHandle(acc.x, acc.y)) {
                                        // Widget can scroll/swipe in this direction itself → gesture belongs to the widget
                                        releasePress(animated = true)
                                        mode = Mode.WidgetTouch
                                        io.github.janhurn9400.liquidy.ui.widgets.AndroidWidgetTaps.move(arenaToWindow(change.position))
                                    } else if (acc.getDistance() > slop) {
                                        io.github.janhurn9400.liquidy.ui.widgets.AndroidWidgetTaps.cancel()
                                        if (hit is Hit.WidgetHandle) {
                                            model.widgets[hit.key]?.let { model.beginResize(hit.key, geo.widgetSize(it.cols, it.rows)) }
                                            mode = Mode.Resize
                                        } else if (model.editing && hit is Hit.Icon) {
                                            beginIconDrag(hit, change.position)
                                        } else if (!model.editing && hit is Hit.Icon && model.widgets[hit.key]?.type == STACK && abs(acc.y) > abs(acc.x)) {
                                            // Widget stack: vertical swipe pages (instead of search)
                                            releasePress(animated = true)
                                            model.stackDragStart(hit.key)
                                            mode = Mode.StackScroll
                                        } else if (abs(acc.x) > abs(acc.y)) {
                                            releasePress(animated = true)
                                            mode = Mode.Paging
                                            pager.beginDrag()
                                            val over = acc.x - slop * sign(acc.x)
                                            val target = pager.dragBy(-over / pw, pw)
                                            scope.launch { pager.position.snapTo(target) }
                                        } else {
                                            releasePress(animated = true)
                                            // App Library: scroll vertically; otherwise pull down → search
                                            mode = when {
                                                // Pull down at the very top → A–Z list (recording2 10.1 s); otherwise scroll
                                                hit is Hit.Library && acc.y > 0 && model.libraryScroll.value <= LIB_PULL_TOP && pager.position.value.let { abs(it - (libIndexNow() - geo.pagesVisible + 1)) < 0.01f } -> {
                                                    scope.launch { model.libraryScroll.animateTo(0f, Motion.scrollBounce.spec()) }
                                                    model.libListPullBegin(); Mode.LibPull
                                                }
                                                hit is Hit.Library -> { libScrollStart = model.libraryScroll.value; scope.launch { model.libraryScroll.stop() }; Mode.LibScroll }
                                                // Today view: at the very top pull down → search (like iOS), otherwise scroll
                                                // Duo unfolded: pulling down on the Today column scrolls (rubber band) instead of opening search [recording 14:57]
                                                hit is Hit.Today && acc.y > 0 && model.todayScroll.value <= LIB_PULL_TOP && !(geo.duo && geo.unfolded) -> Mode.Vertical
                                                hit is Hit.Today -> { libScrollStart = model.todayScroll.value; model.todayTouching = true; scope.launch { model.todayScroll.stop() }; Mode.TodayScroll }
                                                acc.y > 0 && !model.editing -> Mode.Vertical
                                                else -> Mode.Consumed
                                            }
                                        }
                                    }
                                }
                                Mode.Paging -> {
                                    val target = pager.dragBy(-delta.x / pw, pw)
                                    scope.launch { pager.position.snapTo(target) }
                                }
                                Mode.LibScroll -> {
                                    val raw = libScrollStart - (change.position.y - down.position.y)
                                    val max = libGeo.maxScroll(model.library.size)
                                    val d = h * DesignTokens.libRubberBand.value
                                    val c = Motion.rubberBandC.value
                                    fun rb(x: Float) = (x * d * c) / (d + c * x) // M-RUBBER
                                    val shown = when { raw < 0f -> -rb(-raw); raw > max -> max + rb(raw - max); else -> raw }
                                    scope.launch { model.libraryScroll.snapTo(shown) }
                                }
                                Mode.Resize -> model.updateResize(change.position - down.position, geo)
                                Mode.StackScroll -> (hit as? Hit.Icon)?.let { hk -> model.widgets[hk.key]?.let { sp ->
                                    model.stackDrag(hk.key, change.position.y - down.position.y, geo.widgetSize(sp.cols, sp.rows).height) } }
                                Mode.TodayDrag -> model.todayDrag?.let { d ->
                                    val off = Offset((-1f - pager.position.value) * geo.sideStride, -model.todayScroll.value)
                                    val from = TodayLayout.place(model.today, model::sizeOf, geo, density).first { it.first == d.key }.second
                                    val delta = change.position - down.position
                                    // Beyond the right edge of the Today page (unfolded: onto the home page next to it,
                                    // otherwise to the screen edge) → widget moves to the home screen and is dragged on there normally
                                    val todayRight = off.x + (if (geo.unfolded) geo.sideStride else w)
                                    val toHome = if (geo.unfolded) change.position.x > todayRight else change.position.x > w - geo.edgeZone
                                    if (toHome) {
                                        val widgetTopLeft = from.topLeft + off + delta
                                        model.moveTodayToHome(d.key, change.position, widgetTopLeft - geo.widgetOffset)
                                        if (!geo.unfolded) scope.launch { pager.animateTo(0) }
                                        mode = Mode.Drag
                                    } else model.updateTodayDrag(delta, todayTargetIndex(model, d.key, from.center + delta, geo, density))
                                }
                                Mode.TodayScroll -> {
                                    val raw = libScrollStart - (change.position.y - down.position.y)
                                    val max = todayMaxScroll()
                                    val d = h * DesignTokens.libRubberBand.value
                                    val c = Motion.rubberBandC.value
                                    fun rb(x: Float) = (x * d * c) / (d + c * x) // M-RUBBER
                                    val shown = when { raw < 0f -> -rb(-raw); raw > max -> max + rb(raw - max); else -> raw }
                                    scope.launch { model.todayScroll.snapTo(shown) }
                                }
                                Mode.LibPull -> model.libListPullTo((change.position.y - down.position.y - slop).coerceAtLeast(0f) / density)
                                Mode.Vertical -> {
                                    // Finger → home offset with UIScrollView rubber band (M-RUBBER, d = screen height)
                                    val pull = (change.position.y - down.position.y - slop).coerceAtLeast(0f) / density
                                    val d = h / density
                                    val c = Motion.rubberBandC.value
                                    model.searchPull((pull * d * c) / (d + c * pull))
                                }
                                Mode.Drag -> iconDragMove(change.position)
                                else -> Unit
                            }
                            if (mode == Mode.Drag || mode == Mode.Paging || mode == Mode.Vertical || mode == Mode.LibScroll || mode == Mode.LibPull || mode == Mode.Resize || mode == Mode.StackScroll || mode == Mode.TodayScroll || mode == Mode.TodayDrag || mode == Mode.WidgetTouch) change.consume()
                        }
                    }
                }
        )

        // ── Own status bar (setting; the Android bar is then hidden in the launcher) ─────
        @Composable fun StatusLayer() { if (io.github.janhurn9400.liquidy.data.OwnStatusBar.enabled && !model.editing && !model.wallpaperEditorOpen && model.stackEditKey == null) {
            val info by io.github.janhurn9400.liquidy.data.rememberStatusInfo()
            val color = if (DesignTokens.wallpaperIsDark || geo.duo) Color.White else Color.Black
            Box(Modifier.fillMaxSize().graphicsLayer {
                // hide with folder/search like the Android bar
                val folder = ((model.openFolder?.progress?.value ?: 0f) / 0.3f).coerceIn(0f, 1f)
                alpha = (1f - folder) * (if (geo.duo) 1f else 1f - model.searchOpenAmount.value.coerceIn(0f, 1f))
            }) {
                if (geo.duo) DuoStatusSymbols(info, w - DUO_STATUS_FROM_RIGHT * density, density, color)
                else IosStatusBar(info, statusBarPx.takeIf { it > 0f } ?: (IOS_STATUS_H * density), density, color)
            }
        } }
        // Duo: draw only after the App Library list/search field (their fading edge lay over the symbols otherwise)
        if (!geo.duo) StatusLayer()

        // Order: App Library list/search field/category above the arena, above that the Duo symbols — but BELOW widget
        // settings, picker, launcher settings, edit and context menu (they cover everything).
        // ── App Library: A–Z list and expanded category (above the arena, own gestures) ──
        val libListBackdrop = if (geo.duo) com.kyant.backdrop.backdrops.rememberLayerBackdrop() else null
        if (model.libListOpen) {
            io.github.janhurn9400.liquidy.ui.library.LibraryList(model, libGeo, density,
                offsetX = { if (geo.duo) geo.libraryOffset(libIndexNow() - pager.position.value) else 0f },
                listBackdrop = libListBackdrop, onLaunch = onLaunch)
        }
        // Duo: search field ABOVE the list; its glass refracts background + rows (combined source) [video 2, 24.8 s]
        if (geo.duo && libEnabledNow() && libraryOnScreen) {
            val base = io.github.janhurn9400.liquidy.glass.LocalGlassBackdrop.current
            // Background + App Library tiles (+ A–Z rows when open)
            val combined = when {
                base == null || libPageBackdrop == null -> base
                model.libListOpen && libListBackdrop != null -> com.kyant.backdrop.backdrops.rememberCombinedBackdrop(base, libPageBackdrop, libListBackdrop)
                else -> com.kyant.backdrop.backdrops.rememberCombinedBackdrop(base, libPageBackdrop)
            }
            androidx.compose.runtime.CompositionLocalProvider(io.github.janhurn9400.liquidy.glass.LocalGlassBackdrop provides combined) {
                io.github.janhurn9400.liquidy.ui.library.LibraryChrome(model, libGeo, density) { geo.libraryOffset(libIndexNow() - pager.position.value) }
            }
        }
        if (geo.duo) StatusLayer()
        model.openCategory?.let { oc ->
            io.github.janhurn9400.liquidy.ui.library.CategoryView(model, oc, libGeo, density, { app, rect ->
                    model.launchedIconKey = null
                    onLaunch(app, rect)
                    model.libraryLaunch = app.key to rect
                },
                originX = (geo.libraryOffset(libIndexNow() - pager.position.value)).coerceAtLeast(0f),
                onLongPress = { app, tl ->
                    model.prefetchShortcuts(libraryMenuKey(app.key), density)
                    model.openContextMenu(libraryMenuKey(app.key), tl, density, fromLibrary = true)
                },
                onDragStart = { app, p, tl -> model.collapseCategoryForDrag(); startLibraryDrag(app.key, p, tl) },
                onDragMove = { p -> iconDragMove(p) },
                // Category stayed open (invisibly) so its gesture keeps running → now close without animation
                onDragEnd = { iconDragEnd(); model.closeCategoryNow() })
        }

        val remindersAnim = remember { androidx.compose.animation.core.Animatable(0f) }
        LaunchedEffect(io.github.janhurn9400.liquidy.ui.widgets.RemindersStore.sheetOpen) {
            remindersAnim.animateTo(if (io.github.janhurn9400.liquidy.ui.widgets.RemindersStore.sheetOpen) 1f else 0f,
                androidx.compose.animation.core.spring(0.85f, 500f))
        }
        // ── Widget settings ──────────────────────────────────────────────────────
        model.widgetConfigKey?.let { k -> model.widgets[k]?.let { spec ->
            androidx.activity.compose.BackHandler { model.closeWidgetConfig() }
            io.github.janhurn9400.liquidy.ui.widgets.WidgetConfigSheet(spec, model.widgetConfigFrom, { model.widgetConfig.value },
                onChange = { model.setWidgetConfig(k, it) }, onClose = { model.closeWidgetConfig() })
        } }

        // ── Reminders list (tapping the Reminders widget) ──────────────────────
        if (io.github.janhurn9400.liquidy.ui.widgets.RemindersStore.sheetOpen || remindersAnim.value > 0f) {
            val close = { io.github.janhurn9400.liquidy.ui.widgets.RemindersStore.sheetOpen = false }
            if (io.github.janhurn9400.liquidy.ui.widgets.RemindersStore.sheetOpen) androidx.activity.compose.BackHandler(onBack = close)
            io.github.janhurn9400.liquidy.ui.widgets.RemindersSheet({ remindersAnim.value }, close)
        }

        // ── Widget-Picker ─────────────────────────────────────────────────────────────
        if (model.widgetPickerOpen) {
            val calendarPermission = androidx.activity.compose.rememberLauncherForActivityResult(
                androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { }
            io.github.janhurn9400.liquidy.ui.widgets.WidgetPicker(
                onAdd = { spec ->
                    if (spec.type == io.github.janhurn9400.liquidy.ui.widgets.IosWidgetTypes.CALENDAR) calendarPermission.launch(android.Manifest.permission.READ_CALENDAR)
                    if (spec.type == io.github.janhurn9400.liquidy.ui.widgets.IosWidgetTypes.WEATHER) calendarPermission.launch(android.Manifest.permission.ACCESS_COARSE_LOCATION)
                    if (spec.type == io.github.janhurn9400.liquidy.ui.widgets.IosWidgetTypes.PHOTOS) calendarPermission.launch(android.Manifest.permission.READ_MEDIA_IMAGES)
                    if (model.addingToStack) { model.addingToStack = false; model.addToStack(spec) } else {
                    // Picker → always home screen (Today page has its own path). Open on Today → page to page 1 first
                    val onToday = pager.position.value < -0.5f
                    val page = if (onToday) 0 else pager.currentPage.coerceAtMost(model.display.pages.lastIndex)
                    if (onToday) scope.launch { pager.animateTo(0) }
                    val id = model.addWidget(spec, page)
                    // flies from the preview of the detail sheet (screen centre, ≈ 55 % height) to its slot
                    model.startWidgetLanding(id, io.github.janhurn9400.liquidy.ui.widgets.PickerLaunch.previewCenter ?: Offset(w / 2, h * LAND_FROM_Y)) { screenRectOf(id)?.center }
                    model.enterEdit() // iOS: new widget jiggles on the page (recording8 31 s)
                    }
                },
                onClose = { model.widgetPickerOpen = false; model.addingToStack = false },
            )
        }

        // ── Launcher settings ──────────────────────────────────────────────────────
        if (model.settingsOpen) LauncherSettingsSheet(model, onClose = { model.settingsClosed() })

        // ── Customise sheet (icon style) ─────────────────────────────────────────────────
        if (model.customiseOpen) {
            CustomiseSheet(model, sample = display.dock.firstOrNull()?.let { model.apps[it] } ?: model.apps.values.firstOrNull(),
                wallpaper = wallpaper, onClose = { model.customiseClosed() })
        }

        // Duo: brush button lights up before the menu flows out
        if (geo.duo && !model.editMenuOpen && model.editBrushFlash.value > 0.01f) Canvas(Modifier.fillMaxSize()) {
            val r = geo.editButtonRect(left = true)
            drawCircle(Color.White.copy(alpha = 0.85f * model.editBrushFlash.value), r.width / 2, r.center)
        }

        // ── Edit menu (grows from the button top left, recording6 4.9 s) ────────────────
        if (model.editMenuOpen && !model.wallpaperEditorOpen) { // not as a ghost image in the editor preview
            Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures(onPress = { model.closeEditMenu() }) })
            val r = geo.editButtonRect(left = true)
            // Duo (recording 21:28, 53.5–54.2 s): menu right-aligned with the column, top just below the brush; it
            // flows out of the button as a droplet towards the bottom left (origin = brush centre) and overshoots slightly.
            if (geo.duo) {
                // Duo: glass droplet from the brush (video 4) — right-aligned with the column, top just below the brush
                val left = (r.right + DUO_EDIT_MENU_RIGHT_OVER * density - DUO_EDIT_MENU_W * density).coerceAtLeast(8f * density)
                DuoDropletMenu(model.editMenuItems(), dim = DesignTokens.editMenuTint.value, t = { model.editMenu.value }, from = r,
                    toLeftTop = Offset(left, r.bottom + DUO_EDIT_MENU_GAP * density), widthPx = DUO_EDIT_MENU_W * density, density = density,
                    flash = { model.editBrushFlash.value }, closing = { model.editMenuClosing }) { item ->
                    model.closeEditMenu(); item.action()
                }
            } else Box(
                Modifier
                    .offset { IntOffset(r.left.roundToInt(), (r.bottom + EDIT_MENU_GAP * density).roundToInt()) }
                    .width(DesignTokens.ctxWidth.dp)
                    .graphicsLayer {
                        val v = model.editMenu.value
                        val s0 = Motion.ctxMenuFrom.value
                        val s = s0 + (1f - s0) * v
                        scaleX = s; scaleY = s
                        alpha = (v * 4f).coerceIn(0f, 1f)
                        transformOrigin = TransformOrigin(0f, 0f)
                    }
            ) {
                MenuPanel(model.editMenuItems(), dim = DesignTokens.editMenuTint.value) { item -> model.closeEditMenu(); item.action() }
            }
        }

        // ── Context menu (above the arena, own gestures) ──────────────────────────────
        model.contextMenu?.let { m ->
            ContextMenuOverlay(model, m, w, h, density, onSize = { c, r -> model.setWidgetSize(m.key, c, r, geo) }) {
                if (isFolder(m.key) || isWidget(m.key)) Item(m.key, showLabel = false, inMenu = true)
                else model.apps[m.key]?.let { app ->
                    AppIconView(
                        app = app, showLabel = false, wallpaper = wallpaper,
                        pressAlpha = { 0f }, jiggle = { 0f }, clockSeconds = { 0f },
                        badgeScale = { 0f }, showBadge = false,
                    )
                }
            }
        }

        // ── Search ─────────────────────────────────────────────────────────────────
        if (model.searchVisible) {
            io.github.janhurn9400.liquidy.ui.search.SearchOverlay(model, onLaunch,
                // Duo folded [video 5, 13.1 s]: suggestions on the left up to the dock column, dock + status symbols stay visible
                columnWidth = when {
                    geo.unfolded -> pxToDp(geo.duoSearchWidth + DUO_SEARCH_SUGGEST_EXTRA * density, density)
                    geo.duo -> androidx.compose.ui.unit.Dp(geo.duoDockLeftPt - DUO_FOLDED_SEARCH_GAP)
                    else -> null
                }, columnStart = geo.duo && !geo.unfolded)
        }

        // ── Flying widget: ONE arc above everything (recording 10:40, 47.56–48.20 s) ─────────────
        // Position follows progress, size 0.92 → ≈ 1.12 (just before landing) → 1.0, content blurred on the way.
        model.widgetLanding?.let { l ->
            val spec = model.widgets[l.key]
            val to = screenRectOf(l.key)
            if (spec != null && to != null) {
                val ws = geo.widgetSize(spec.cols, spec.rows)
                // lingering soft shadow at the preview position (reference 47.60–47.80)
                Box(Modifier.zIndex(19f).offset {
                    IntOffset((l.fromCenter.x - ws.width / 2).roundToInt(), (l.fromCenter.y - ws.height / 2 + 14f * density).roundToInt())
                }.size(pxToDp(ws.width, density), pxToDp(ws.height, density)).graphicsLayer {
                    alpha = WidgetFlight.ghost(l.t.value) * 0.22f
                    renderEffect = BlurEffect(22f * density, 22f * density, TileMode.Decal)
                }.background(Color.Black, io.github.janhurn9400.liquidy.glass.glassRect(DesignTokens.widgetCorner.dp)))
                Box(Modifier.zIndex(20f).offset {
                    val t = l.t.value
                    val hover = l.fromCenter + Offset(0f, -LAND_LIFT_DP * density) * WidgetFlight.lift(t)
                    val c = hover + (to.center - hover) * WidgetFlight.travel(t)
                    IntOffset((c.x - ws.width / 2).roundToInt(), (c.y - ws.height / 2).roundToInt())
                }.size(pxToDp(ws.width, density), pxToDp(ws.height, density))
                    .graphicsLayer {
                        val t = l.t.value
                        val sc = WidgetFlight.scale(t); scaleX = sc; scaleY = sc
                        shadowElevation = 18f * density * WidgetFlight.ghost(t); shape = io.github.janhurn9400.liquidy.glass.glassRect(DesignTokens.widgetCorner.dp); clip = true
                        val b = WidgetFlight.blur(t) * LAND_BLUR_DP * density
                        renderEffect = if (b > 0.5f) BlurEffect(b, b, TileMode.Decal) else null
                    }) {
                    if (spec.type == "android") io.github.janhurn9400.liquidy.ui.widgets.AndroidWidget(spec, Modifier.fillMaxSize())
                    else WithWallpaperGlass(wallpaper) { io.github.janhurn9400.liquidy.ui.widgets.IosWidget(spec.type, spec.cols, spec.rows, Modifier.fillMaxSize(), spec.config) }
                }
            }
        }

        // ── Delete dialog ──────────────────────────────────────────────────────────
        model.pendingRemoval?.let { key ->
            model.apps[key]?.let { app ->
                RemoveAppAlert(
                    appName = app.label,
                    canDelete = !model.isSystemApp(key),
                    wallpaper = wallpaper,
                    onDelete = { model.pendingRemoval = null; model.deleteApp(key) },
                    onRemoveFromHome = { model.pendingRemoval = null; model.removeFromHome(key) },
                    onCancel = { model.pendingRemoval = null },
                )
            }
        }
    }
    } // end CompositionLocalProvider
}

/** Places an icon at its animated position (M-REFLOW in edit mode, otherwise immediately). */
@Composable
private fun IconSlot(
    model: HomeModel,
    key: String,
    container: Container,
    target: Offset,
    pager: PagerState,
    pageShift: (Float) -> Float,
    density: Float,
    content: @Composable () -> Unit,
) {
    val anim = remember(key) { model.anims.getOrPut(key) { IconAnim(container, target) } }
    LaunchedEffect(anim, container, target) {
        if (anim.container != container) {
            // Container change: convert via screen coordinates so nothing jumps
            val screen = toScreen(anim.container, anim.pos.value, pager.position.value, pageShift)
            anim.pos.snapTo(fromScreen(container, screen, pager.position.value, pageShift))
            anim.container = container
        }
        val spring = anim.nextSpring ?: if (model.editing) Motion.reflow else null
        anim.nextSpring = null
        if (spring == null) anim.pos.snapTo(target) else anim.pos.animateTo(target, spring.spec())
    }
    Box(
        Modifier.offset {
            val p = toScreen(container, anim.pos.value, pager.position.value, pageShift)
            val sh = shockOffset(model, key, p + Offset(DesignTokens.iconSize.value * density / 2, DesignTokens.iconSize.value * density / 2), density)
            IntOffset((cellOffsetX(p.x, density) + sh.x).roundToInt(), (p.y + sh.y).roundToInt())
        }.graphicsLayer { val s = anim.scale.value; scaleX = s; scaleY = s } // just dropped: springs back from the lifted size
    ) { content() }
}

/**
 * Shock wave (widget lands): radial push outwards that travels at wave speed and springs back damped;
 * ≈ 6 pt at the direct neighbours (≈ 100 pt away), weaker further away [MEASURED roughly] 10:40, 47.9–48.2 s.
 */
private fun shockOffset(model: HomeModel, key: String, center: Offset, density: Float): Offset {
    val sw = model.shockwave ?: return Offset.Zero
    if (model.widgetLanding?.key == key) return Offset.Zero
    val d = center - sw.center
    val dist = d.getDistance() / density
    if (dist < 1f) return Offset.Zero
    val phase = sw.time.value - dist / SHOCK_SPEED
    if (phase <= 0f) return Offset.Zero
    val amp = SHOCK_AMP * kotlin.math.exp(-dist / SHOCK_RANGE) * kotlin.math.exp(-phase * SHOCK_DECAY) *
        kotlin.math.sin(phase * 2f * kotlin.math.PI.toFloat() * SHOCK_FREQ)
    return d / (dist * density) * amp * density
}

private fun toScreen(c: Container, p: Offset, pagerPos: Float, shift: (Float) -> Float) = when (c) {
    is Container.Page -> p + Offset(shift(c.index - pagerPos), 0f)
    Container.Dock -> p
}

private fun fromScreen(c: Container, p: Offset, pagerPos: Float, shift: (Float) -> Float) = when (c) {
    is Container.Page -> p - Offset(shift(c.index - pagerPos), 0f)
    Container.Dock -> p
}

private fun pxToDp(px: Float, density: Float) = androidx.compose.ui.unit.Dp(px / density)

/**
 * Gestures with an open folder: tap on icon → launch app, long press → drag out of the folder
 * (folder closes, dragging continues on the home screen), tap outside → close folder.
 */
private suspend fun androidx.compose.ui.input.pointer.AwaitPointerEventScope.handleFolderGesture(
    model: HomeModel,
    open: OpenFolder,
    geo: FolderGeometry,
    down: androidx.compose.ui.input.pointer.PointerInputChange,
    density: Float,
    onLaunch: (AppEntry, Rect) -> Unit,
    scope: kotlinx.coroutines.CoroutineScope,
    /** Keep dragging on the home screen after the app has left the folder (like normal icon dragging). */
    dragMove: (Offset) -> Unit,
    dragEnd: () -> Unit,
) {
    val folder = model.folderFor(open.id) ?: return
    val panel = geo.panel
    val icon = DesignTokens.iconSize.value * density
    val perPage = DesignTokens.FOLDER_COLUMNS * DesignTokens.FOLDER_ROWS
    // Folder pages (9 apps each; in edit mode one more empty one, iOS) — only the currently visible one is hit
    val pageCount = ((folder.apps.size + perPage - 1) / perPage + (if (model.editing) 1 else 0)).coerceAtLeast(1)
    val page = model.folderPage.value.roundToInt().coerceIn(0, pageCount - 1)
    val onPage = folder.apps.withIndex().filter { it.index / perPage == page }
    fun slotTopLeft(i: Int) = panel.topLeft + geo.slot(i % perPage)
    val hitKey = onPage.firstOrNull { (i, _) -> Rect(slotTopLeft(i), Size(icon, icon)).inflate(icon * 0.2f).contains(down.position) }
    val insidePanel = panel.contains(down.position)
    // Editing: tap on the rename field → text field gets focus + keyboard (the arena lies above it);
    // the round × button on the right clears the name. Any other touch in the folder ends renaming.
    if (model.editing && geo.titleRect.contains(down.position)) {
        while (true) { val e = awaitPointerEvent(); if (e.changes.none { it.pressed }) break }
        if (down.position.x >= geo.titleRect.right - geo.nameClearZone) model.renameFolder(open.id, "")
        else model.folderRenameTap = Offset(down.position.x, down.position.y)
        model.folderRenaming = true
        return
    }
    model.folderRenaming = false
    // Minus badge → "Remove App?" like on the home screen
    if (model.editing) {
        val badgeKey = onPage.firstOrNull { (i, _) ->
            val c = slotTopLeft(i) + Offset(DesignTokens.badgeOffsetX.value * density, DesignTokens.badgeOffsetY.value * density)
            (c - down.position).getDistance() <= (DesignTokens.badgeSize.value / 2 + DesignTokens.badgeHitSlop.value) * density
        }
        if (badgeKey != null) {
            while (true) { val e = awaitPointerEvent(); if (e.changes.none { it.pressed }) break }
            model.pendingRemoval = badgeKey.value
            return
        }
    }
    if (hitKey != null) {
        model.pressedKey = hitKey.value
        scope.launch { model.pressAlpha.snapTo(DesignTokens.pressDarkAlpha.value) }
    }
    val t0 = System.nanoTime()
    var moved = false
    // Paging in the folder: horizontal in the panel, follows the finger, snaps with M-PAGE [MEASURED user iPhone 10-14, 18.4 s]
    var paging = false
    val startPage = model.folderPage.value
    val tracker = androidx.compose.ui.input.pointer.util.VelocityTracker().apply { addPosition(down.uptimeMillis, down.position) }
    while (true) {
        val remaining = Motion.longPress.value.toLong() - (System.nanoTime() - t0) / 1_000_000
        val event = if (hitKey != null && !moved) withTimeoutOrNull(remaining.coerceAtLeast(1)) { awaitPointerEvent() } else awaitPointerEvent()
        if (event == null) {
            scope.launch { model.pressAlpha.snapTo(0f); model.pressedKey = null }
            dragInFolder(model, open.id, geo, page, hitKey!!.value, slotTopLeft(hitKey.index), down, icon, dragMove, dragEnd)
            return
        }
        val change = event.changes.firstOrNull { it.id == down.id } ?: return
        tracker.addPosition(change.uptimeMillis, change.position)
        if (!change.pressed) {
            scope.launch { model.pressAlpha.snapTo(0f); model.pressedKey = null }
            if (paging) {
                val v = -tracker.calculateVelocity().x / panel.width // pages/s
                val p = model.folderPage.value
                val from = startPage.roundToInt()
                val target = when {
                    kotlin.math.abs(v) * panel.width / density > Motion.pageFlingVelocity.value -> from + kotlin.math.sign(v).toInt()
                    p - from > Motion.pageDistanceFraction.value -> from + 1
                    from - p > Motion.pageDistanceFraction.value -> from - 1
                    else -> from
                }.coerceIn(0, pageCount - 1)
                scope.launch { model.folderPage.animateTo(target.toFloat(), Motion.page.spec(), v) }
                return
            }
            if (!moved) when {
                hitKey != null -> model.apps[hitKey.value]?.let {
                    model.launchedIconKey = hitKey.value
                    onLaunch(it, Rect(slotTopLeft(hitKey.index), Size(icon, icon)))
                }
                !insidePanel -> model.closeFolder()
            }
            return
        }
        val delta = change.position - down.position
        if (!moved && delta.getDistance() > viewConfiguration.touchSlop) {
            scope.launch { model.pressAlpha.animateTo(0f, Motion.pressOut.spec()); model.pressedKey = null }
            moved = true
            paging = insidePanel && kotlin.math.abs(delta.x) > kotlin.math.abs(delta.y)
        }
        if (paging) {
            change.consume()
            // beyond the first/last page with rubber band (M-RUBBER, d = panel width)
            val raw = startPage - delta.x / panel.width
            val c = Motion.rubberBandC.value
            fun rb(x: Float) = (x * c) / (1f + c * x)
            val shown = when {
                raw < 0f -> -rb(-raw)
                raw > pageCount - 1f -> pageCount - 1f + rb(raw - (pageCount - 1f))
                else -> raw
            }
            scope.launch { model.folderPage.snapTo(shown) }
        }
    }
}

/**
 * App lifted in the open folder (iOS): it hangs on the finger, the folder stays open. If its centre is outside the panel for
 * [Motion.folderOutDelay], the folder closes and dragging continues on the home screen [MEASURED user iPhone 10-14,
 * 23.27 → 23.42 s]. Release inside the panel → back to its slot.
 */
private suspend fun androidx.compose.ui.input.pointer.AwaitPointerEventScope.dragInFolder(
    model: HomeModel,
    folderId: String,
    geo: FolderGeometry,
    page: Int,
    key: String,
    iconTopLeft: Offset,
    down: androidx.compose.ui.input.pointer.PointerInputChange,
    icon: Float,
    dragMove: (Offset) -> Unit,
    dragEnd: () -> Unit,
) {
    val panel = geo.panel
    val fd = FolderDrag(key, down.position - iconTopLeft, down.position)
    model.folderDrag = fd
    model.haptic()
    var outsideSince: Long? = null
    // Reordering in the folder like on the home screen: slot under the icon, take over only after M-REFLOW.delay standstill
    val perPage = DesignTokens.FOLDER_COLUMNS * DesignTokens.FOLDER_ROWS
    val others = (model.folderFor(folderId)?.apps?.size ?: 1) - 1
    var candidate = -1; var candidateSince = 0L
    while (true) {
        // keep checking even without movement (finger holds still outside the panel)
        val e = withTimeoutOrNull(FOLDER_DRAG_POLL_MS) { awaitPointerEvent() }
        if (e != null) {
            val ch = e.changes.firstOrNull { it.id == down.id }
            if (ch == null || !ch.pressed) { // released in the folder → to the insertion slot (otherwise back to its slot)
                if (fd.hoverIndex >= 0) model.reorderInFolder(folderId, key, fd.hoverIndex)
                fd.hoverIndex = -1
                // copy flies into its slot (FolderDragCopy), afterwards the real icon is there
                val at = model.folderFor(folderId)?.apps?.indexOf(key) ?: -1
                val slotPage = if (at >= 0) at / perPage else page
                fd.landingAt = if (at >= 0 && slotPage == page) panel.topLeft + geo.slot(at % perPage) else null
                if (fd.landingAt == null) model.folderDrag = null
                return
            }
            fd.pointer = ch.position
            ch.consume()
        }
        val center = fd.pointer - fd.grab + Offset(icon / 2, icon / 2)
        val now = System.nanoTime()
        outsideSince = if (panel.contains(center)) null else outsideSince ?: now
        if (outsideSince == null) {
            // nearest slot on the visible folder page
            val j = (0 until perPage).minBy { (panel.topLeft + geo.slot(it) + Offset(icon / 2, icon / 2) - center).getDistance() }
            val idx = (page * perPage + j).coerceIn(0, others)
            if (idx != candidate) { candidate = idx; candidateSince = now }
            else if (idx != fd.hoverIndex && (now - candidateSince) / 1_000_000 >= Motion.reflowDelay.value) fd.hoverIndex = idx
        }
        if (outsideSince != null && (now - outsideSince) / 1_000_000 >= Motion.folderOutDelay.value) break
    }
    // out: folder closes, normal dragging takes over
    model.folderDrag = null
    model.dragOutOfFolder(key, fd.pointer, fd.pointer - fd.grab)
    while (true) {
        val e = awaitPointerEvent()
        val ch = e.changes.firstOrNull { it.id == down.id }
        if (ch == null || !ch.pressed) { dragEnd(); return }
        dragMove(ch.position)
        ch.consume()
    }
}

private const val FOLDER_DRAG_POLL_MS = 16L

private const val EDIT_MENU_GAP = 16f // pt below the button [MEASURED] recording6 4.9 s (menu top ≈ 62.7 pt)
private const val LIB_PULL_TOP = 1f
private const val LIB_COVERS_AT = 0.5f // from this App Library share on, the page next to it is no longer usable // px: list only when the App Library is at the very top

/** App Library fling: decelerate like UIScrollView (M-SCROLL), spring back at the bounds (M-SCROLL-BOUNCE). */
private suspend fun flingLibrary(model: HomeModel, max: Float, velocity: Float) = flingScroll(model.libraryScroll, max, velocity)

/** Decelerate like UIScrollView (M-SCROLL), spring back at the bounds (M-SCROLL-BOUNCE). */
private suspend fun flingScroll(a: androidx.compose.animation.core.Animatable<Float, androidx.compose.animation.core.AnimationVector1D>, max: Float, velocity: Float) {
    val bounce = Motion.scrollBounce.spec<Float>()
    if (a.value < 0f || a.value > max) {
        a.animateTo(a.value.coerceIn(0f, max), bounce, velocity)
        return
    }
    a.updateBounds(0f, max)
    val result = a.animateDecay(velocity, androidx.compose.animation.core.exponentialDecay(Motion.scrollDecayFriction.value))
    a.updateBounds(null, null)
    if (result.endReason == androidx.compose.animation.core.AnimationEndReason.BoundReached) {
        a.animateTo(a.value, bounce, result.endState.velocity)
    }
}

private const val OVERVIEW_HOME_HIDDEN = 0.35f // progress at which the home screen is fully gone (spring: ≈ 145 ms before the end, Duo ≈ 130 ms)
private const val OVERVIEW_HOME_FULL = 0.15f   // … and at which it is fully there (≈ 205 ms, Duo ≈ 230 ms)

private const val DUO_ICON = 60f // pt, iPhone Duo [MEASURED; also confirmed with real display edges in recording 19:18]
private const val LAND_FROM = 1f          // lifts off at preview size (preview = widget size)
private const val LAND_OVER = 1.12f       // lands slightly enlarged
private const val LAND_BLUR_DP = 10f
private const val LAND_LIFT_DP = 90f // reference: rises ≈ 95 pt above the preview
private const val LAND_FROM_Y = 0.55f

private const val SHOCK_AMP = 14f         // pt (≈ 11 pt at direct neighbours, ≈ 6 pt at 400 pt)
private const val SHOCK_RANGE = 450f      // pt decay range
private const val SHOCK_SPEED = 900f      // pt/s
private const val SHOCK_DECAY = 7f
private const val SHOCK_FREQ = 2.2f       // Hz
private const val DUO_EDIT_MENU_W = 249f        // [MEASURED] 681.5 → 930.4 pt
private const val DUO_EDIT_MENU_RIGHT_OVER = 4.4f // right edge 4 pt beyond the button edge
private const val DUO_EDIT_MENU_GAP = -19f      // top 120 pt; brush bottom 139 pt → menu slightly covers the button
private const val DUO_SEARCH_SUGGEST_EXTRA = 30f // suggestions 458 pt vs. field 428 pt [MEASURED]
private const val DUO_DOCK_CORNER = 22f
private const val FOLD_IN_MS = 1300         // total: brightening 0.85 s + sharpening 0.45 s
private const val FOLD_SHARPEN_SHARE = 0.35f // blur holds until 35 % remain (= last 0.45 s)
private const val FOLD_BLUR_DP = 18f
private const val FOLD_DIM = 0.26f
private const val DUO_WPE_FADE_MS = 250
private const val DUO_STATUS_FROM_RIGHT = 47f
private const val DUO_FOLDED_SEARCH_GAP = 8f
private const val DUO_SEARCH_DIM = 0.40f // dock column centre
private const val IOS_STATUS_H = 47f
private const val DUO_LIB_BLUR_FULL_AT = 0.3f
private const val DUO_DOCK_RIM = 0.62f
private const val DUO_SEARCH_DOCK_DELAY_MS = 1200L
private const val DUO_SEARCH_DOCK_FULL_AT = 0.5f
private const val DUO_SEARCH_DOCK_OUT_MS = 420
private const val DUO_SEARCH_DOCK_IN_MS = 320
private const val DUO_DOCK_OUT_MARGIN = 24f // pt beyond the edge (glass shadow)
private const val DUO_CAT_DOCK_SHIFT = 150f // pt with an open category → fully out of view
private const val DUO_LIB_PAGE_BLUR = 14f // pt blur of the home page next to the App Library [ESTIMATED]

/**
 * Widget flight from the picker, phases over t (0…1 linear over 0.94 s) [MEASURED] recording 10:40, 47.56–48.50 s:
 *  flight 0…0.40: blurred, position with soft acceleration/deceleration, size 1.0 → 1.16;
 *  landing 0.40…1: sharp, size 1.16 → 0.87 (at 0.68 = 48.25 s) → 1.0 (springy, smoothly interpolated).
 */
private object WidgetFlight {
    private const val T = LANDING_MS / 1000f          // 1.0 s total (reference 47.53 → 48.53 s)
    private const val LIFT_S = 0.25f                   // 47.56–47.78: rises above the sheet, grows to 1.18
    private const val ARRIVE_S = LANDING_ARRIVE * T    // 47.94: sharp again, impact
    private fun cd(w: Float, x: Float) = if (x <= 0f) 0f else 1f - (1f + w * x) * kotlin.math.exp(-w * x)
    /** Lift 0…1 (upwards, ease-out), then stands still – the hover pause of the reference. */
    fun lift(t: Float) = inOut((t * T - 0.04f) / (LIFT_S - 0.06f))
    // Reference: 47.56–47.65 barely, 47.65–47.70 the main part, then hovering → ease-in-out instead of ease-out
    private fun inOut(x: Float): Float { val k = x.coerceIn(0f, 1f); return if (k < 0.5f) 4f * k * k * k else 1f - (-2f * k + 2f).let { it * it * it } / 2f }
    /** Path from the hover point to the slot: fast jump (≈ 0.1 s) + soft glide (≈ 25 pt until 48.5 s). */
    private val Z_END = 0.86f * cd(34f, T - LIFT_S) + 0.14f * cd(8f, T - LIFT_S)
    fun travel(t: Float): Float { val x = t * T - LIFT_S; return (0.86f * cd(34f, x) + 0.14f * cd(8f, x)) / Z_END }
    /** Size: 1 → 1.18 while lifting, 1.16 on arrival, then damped oscillation (0.93 @ 0.11 s, 0.87 @ 0.26 s, 1.0 @ 0.55 s). */
    fun scale(t: Float): Float {
        val s = t * T
        if (s < LIFT_S) return 1f + 0.18f * lift(t)
        if (s < ARRIVE_S) return 1.18f - 0.02f * ((s - LIFT_S) / (ARRIVE_S - LIFT_S))
        val x = s - ARRIVE_S
        val osc = kotlin.math.exp(-3.7f * x) * (0.16f * kotlin.math.cos(7.6f * x) - 0.30f * kotlin.math.sin(7.6f * x))
        val fade = 1f - ((s - (T - 0.15f)) / 0.15f).coerceIn(0f, 1f) // settle the rest cleanly to 1.0
        return 1f + osc * fade
    }
    /** Blur: on immediately (content becomes a grey shape), gone in 0.09 s from shortly before arrival. */
    fun blur(t: Float): Float { val s = t * T; return ((s / 0.03f).coerceIn(0f, 1f)) * (1f - ((s - (ARRIVE_S - 0.03f)) / 0.09f).coerceIn(0f, 1f)) }
    /** Shadow shape that stays behind at the departure point and disappears with the sheet. */
    fun ghost(t: Float): Float { val s = t * T; return ((s / 0.06f).coerceIn(0f, 1f)) * (1f - ((s - LIFT_S) / 0.12f).coerceIn(0f, 1f)) }
}

/** Context menu key for an app from the App Library (resolves to the same app via [AppMap], but is not a home icon). */
internal fun libraryMenuKey(appKey: String) = appKey + INSTANCE_SEP + "library"
