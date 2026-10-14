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
    data object Pill : Hit
    data class Library(val hit: io.github.janhurn9400.liquidy.ui.library.LibraryHit) : Hit
}

private enum class Mode { Pending, Paging, Vertical, LibScroll, LibPull, TodayScroll, TodayDrag, Menu, Drag, Resize, StackScroll, WidgetTouch, Consumed }

/**
 * Homescreen: Wallpaper, Seiten (4×6), Dock, Pill, Bearbeitungsmodus.
 * Eine zentrale Gesten-Arena (ARCHITECTURE): Antippen, Long-Press, Paging, Ziehen, Badge, Edit-Buttons.
 */
@Composable
fun HomeScreen(
    model: HomeModel,
    pager: PagerState,
    wallpaper: ImageBitmap,
    onLaunch: (AppEntry, Rect) -> Unit,
    /** false: Android zeigt dasselbe Bild hinter dem Fenster → eigenes nur für Unschärfe-Effekte zeichnen. */
    drawWallpaper: Boolean = true,
    /** Wallpaper-Editor: weich animierter Blur der Vorschau (0…1). */
    wallpaperBlur: () -> Float = { 0f },
) {
    val hapticView = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.SideEffect { model.haptic = { io.github.janhurn9400.liquidy.data.Haptics.tick(hapticView) } }
    val scope = rememberCoroutineScope()
    val view = androidx.compose.ui.platform.LocalView.current
    val density = LocalDensity.current.density
    val display = model.display
    val dragging = model.drag != null
    // App-Mediathek = Seite hinter der letzten Homescreen-Seite (nicht im Bearbeitungsmodus)
    // Frisch lesen (Funktionen statt Werte): die Gesten-Arena (pointerInput) wird nur einmal angelegt und hätte sonst
    // Seitenzahl/Mediathek-Lage vom Anlegen behalten → Taps trafen alte Positionen, Halten in der Mediathek ging nicht
    fun libIndexNow() = model.display.pages.size
    fun libEnabledNow() = !model.editing || model.leavingLibrary
    // Heute-Ansicht links (nicht im Bearbeitungsmodus — dort wäre Seite −1 kein gültiges Ziehziel)
    // Heute-Ansicht auch im Bearbeitungsmodus erreichbar — nur nicht beim Ziehen von Icons (Seite −1 ist kein Ablageziel)
    val screenIsWide = androidx.compose.ui.platform.LocalConfiguration.current.let { it.screenWidthDp > it.screenHeightDp }
    pager.firstPage = if (model.drag != null) 0 else -1
    pager.pageCount = display.pages.size +
        (if (dragging && display.pages.lastOrNull()?.isNotEmpty() == true) 1 else 0) +
        (if (libEnabledNow()) 1 else 0) -
        // Duo aufgeklappt: zwei Seiten sichtbar → letzte Position zeigt [letzte Seite | Mediathek]
        (if (io.github.janhurn9400.liquidy.data.FoldableStyle.duoStyle && screenIsWide) 1 else 0)

    // Seiten weggefallen (leere Seite entfernt, App deinstalliert, Neuladen): Position nicht hinter der letzten Seite lassen
    LaunchedEffect(pager.pageCount) {
        val last = (pager.pageCount - 1).coerceAtLeast(0).toFloat()
        if (!pager.isDragging && pager.position.value > last) pager.position.animateTo(last, androidx.compose.animation.core.spring(1f, 380f))
    }

    // Wackel-Uhr: läuft nur, solange gewackelt wird; gelesen nur in graphicsLayer → keine Rekomposition
    val clock = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(model.editing) {
        if (!model.editing && model.jiggleAmount.value == 0f) return@LaunchedEffect
        val start = withFrameNanos { it }
        while (isActive && (model.editing || model.jiggleAmount.value > 0f)) {
            withFrameNanos { clock.floatValue = (it - start) / 1e9f }
        }
    }

    // Glas-Quelle: Wallpaper + Seiten-Icons werden aufgenommen; Dock, Pill, Ordner, Dialoge brechen diesen Inhalt
    // Seitliche Unschärfe: Heute-Ansicht (links) und Mediathek (rechts) verwischen den Wallpaper gleich stark
    /** Mediathek-Anteil der Seitenunschärfe (0 auf den Homescreen-Seiten, 1 auf der Mediathek). */
    fun libBlur(): Float {
        val pos = pager.position.value
        // frisch lesen (nicht die beim ersten Aufbau eingefangenen Werte — da waren die Apps noch nicht geladen)
        val libIndex = model.display.pages.size
        // Duo aufgeklappt: Mediathek kommt schon eine Seite früher ins Bild (zwei Seiten sichtbar) — bei nur einer
        // Seite also direkt von der Heute-Ansicht aus (Position −1 → 0); darum nicht an pos ≥ 0 koppeln
        val shift = if (io.github.janhurn9400.liquidy.data.FoldableStyle.duoStyle && screenIsWide) 1 else 0
        return if (!model.editing || model.leavingLibrary) (pos - (libIndex - 1 - shift)).coerceIn(0f, 1f) else 0f
    }
    fun sideBlur(): Float {
        val pos = pager.position.value
        // Duo: Wallpaper neben der Heute-Seite bleibt scharf (Aufnahme 10-08 21:28, 31 s — scharfe Wallpaper-Kante)
        // Duo aufgeklappt: Heute verwischt nur ihre eigene Seite (TodayPage); zugeklappt wie iPhone bildschirmweit
        val today = if (io.github.janhurn9400.liquidy.data.FoldableStyle.duoStyle && screenIsWide) 0f else (-pos).coerceIn(0f, 1f)
        return maxOf(today, libBlur())
    }
    /** Nur der Mediathek-Anteil von [sideBlur] (0 auf der Heute-Seite). */
    fun sideBlurLibOnly(): Float = libBlur()

    val blurredWallpaper by androidx.compose.runtime.produceState<ImageBitmap?>(null, wallpaper) {
        value = io.github.janhurn9400.liquidy.glass.WallpaperRenderer.softBlur(wallpaper, DesignTokens.todayBlur.value * density)
    }
    val wallpaperBlur = remember(blurredWallpaper) { io.github.janhurn9400.liquidy.glass.WallpaperBlur(blurredWallpaper, ::sideBlur) }
    // Glas-Quelle: Wallpaper (seitlich unscharf überblendet) + Seiten-Icons — Glas bricht denselben Hintergrund, den man sieht
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
        // Höhe der Android-Statusleiste (auch wenn ausgeblendet) — Maß für unsere eigene iOS-Leiste
        @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
        val statusBarPx = androidx.compose.foundation.layout.WindowInsets.statusBarsIgnoringVisibility
            .getTop(androidx.compose.ui.platform.LocalDensity.current).toFloat()
        // iPhone-Duo-Stil (Foldable, im Setup gewählt): eigene Geometrie; Icons 60 pt wie auf dem Duo [GEMESSEN]
        val duo = io.github.janhurn9400.liquidy.data.FoldableStyle.duoStyle
        LaunchedEffect(duo) { DesignTokens.iconSize.tunable.value = if (duo) DUO_ICON else DesignTokens.iconSize.tunable.default }
        val geo = remember(w, h, density, navBarPx, duo) { HomeGeometry(density, w, h, navBarPx, duo) }
        geo.dockCount = model.display.dock.size
        // Plätze im Dock: Duo mit „Mehr Apps im Dock“ so viele, wie in die rechte Spalte passen, sonst 4 wie iOS
        model.dockMax = if (geo.duo && io.github.janhurn9400.liquidy.data.FoldableStyle.moreDockApps) geo.duoDockCapacity else DesignTokens.DOCK_SLOTS
        // Duo: Dock-Glas wächst/schrumpft mit derselben Feder wie die ausweichenden Icons (M-REFLOW) statt zu springen
        val dockSlots = remember { androidx.compose.animation.core.Animatable(model.display.dock.size.toFloat()) }
        val dockSize = model.display.dock.size
        LaunchedEffect(dockSize) { dockSlots.animateTo(dockSize.toFloat(), Motion.reflow.spec()) }
        /** Blätter-Schritt: eine Seite (aufgeklappt 448 pt, sonst Bildschirmbreite). */
        val pw = geo.pageWidth
        // Duo: Glas-Quelle der Mediathek-Kacheln (sie liegen außerhalb der Haupt-Quelle, über dem Dock)
        val libPageBackdrop = if (geo.duo) com.kyant.backdrop.backdrops.rememberLayerBackdrop() else null
        // Zuklappen (Duo, Aufnahme 10-08 21:29, 62,2–63,6 s): Außendisplay erscheint gedimmt + unscharf, hellt in ≈ 0,85 s
        // auf und wird danach in ≈ 0,45 s scharf. Aufklappen: Innen sofort scharf (kein eigener Übergang).
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
        /** 1 auf den Homescreen-Seiten, 0 auf der Mediathek: Dock + Pill blenden aus. */
        /** Seitenübersicht: Homescreen-Inhalt verschwindet sofort (Video: ≈ 3 Bilder), die Karte übernimmt. */
        // Seitenübersicht ↔ Homescreen [GEMESSEN Duo 19:05:45, 21,9 s @ 30 fps]: beim Schließen blendet der echte Homescreen
        // (Dock, ✓/Pinsel, Minus, Beschriftungen) ≈ 130–230 ms weich ein, die gezoomte Karte ist ≈ 260 ms weg; kein harter Schnitt.
        // Homescreen ist voll da, BEVOR die Karte darüber ausblendet → kein dunkles Zwischenbild (PageOverview: FOCUS_FADE_*)
        fun overviewHide(): Float = if (!model.pagesOverviewOpen) 1f
            else ((OVERVIEW_HOME_HIDDEN - model.pagesOverview.value) / (OVERVIEW_HOME_HIDDEN - OVERVIEW_HOME_FULL)).coerceIn(0f, 1f)
        fun homeChromeAlpha(): Float {
            // Duo: Dock + Lupe stehen in der rechten Spalte und bleiben neben der Mediathek sichtbar (Aufnahme 35,5 s)
            val lib = if (!libEnabledNow() || geo.duo) 1f else (libIndexNow() - pager.position.value).coerceIn(0f, 1f)
            // Heute-Ansicht: Dock + Pill blenden aus — Duo nicht (rechte Spalte bleibt, Aufnahme 31 s)
            val today = if (geo.duo) 1f else (1f + pager.position.value).coerceIn(0f, 1f)
            return minOf(lib, today)
        }

        /** Duo (auf-/zugeklappt): 0…1, wie weit die Mediathek im Bild ist (Dock/Lupe/Punkte reagieren darauf). */
        // Rampe: Seite + Dock sind schon voll unscharf, wenn die Mediathek ≈ 30 % hereingekommen ist
        // [GEMESSEN 10:40: 15,42 s ≈ 10 % leicht, 15,44 s ≈ 29 % voll unscharf]
        fun duoLibAmount(): Float = if (geo.duo && libEnabledNow()) {
            val k = (sideBlurLibOnly() / DUO_LIB_BLUR_FULL_AT).coerceIn(0f, 1f); k * (2f - k)
        } else 0f
        // Duo: im Wallpaper-Editor zeigt die Vorschau kein Dock und keine Lupe [GEMESSEN 10:40, 84,5 s]
        val duoWpeHide by androidx.compose.animation.core.animateFloatAsState(
            if (geo.duo && model.wallpaperEditorOpen) 1f else 0f, androidx.compose.animation.core.tween(DUO_WPE_FADE_MS), label = "duoWpeHide")
        // Duo zugeklappt, Suche offen: Dock bleibt erst stehen, gleitet nach kurzer Zeit rechts aus dem Bild, beim Schließen zurück
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
        /** Seitenübersicht öffnen; Duo aufgeklappt mit Heute links: aktuelle Seite liegt rechts → Karte wächst aus der rechten Hälfte. */
        fun openOverview() {
            model.overviewLeftPage = pager.position.value.roundToInt()
            model.overviewZoom.clear()
            model.openPagesOverview(pager.currentPage)
        }

        // ── Gezogenes Icon: Größe ──────────────────────────────────────────────────
        // Duo: über dem Dock hat es Dock-Größe (48 pt), sonst Raster-Größe — weich umblenden, damit es beim Ablegen
        // bzw. Herausziehen nicht springt; beim Aufnehmen aus dem Dock startet es in Dock-Größe
        val dragKey = model.drag?.key
        val dragOverDock by remember { derivedStateOf { geo.duo && model.drag?.hover is DropTarget.Dock } }
        val dragDockScale = remember { androidx.compose.animation.core.Animatable(1f) }
        val dragScaleFor = remember { arrayOfNulls<String>(1) }
        LaunchedEffect(dragKey, dragOverDock) {
            val t = if (dragOverDock) geo.dockIconScale else 1f
            if (dragKey != dragScaleFor[0]) { dragScaleFor[0] = dragKey; dragDockScale.snapTo(t) }
            else dragDockScale.animateTo(t, Motion.reflow.spec())
        }

        // ── Icon ziehen (gemeinsam für Homescreen-Gesten und Mediathek-Kategorie) ──────────
        val dragEdgeJob = remember { arrayOfNulls<Job>(1) }
        /** Finger bewegt beim Ziehen: Ziel aktualisieren, am Rand nach M-EDGE weiterblättern bzw. neue Seite anlegen. */
        fun iconDragMove(pos: Offset) {
            // aufgeklappt (Duo): Seite unter dem Finger — zwei Seiten sind gleichzeitig sichtbar
            val page = if (geo.unfolded) (pager.position.value.roundToInt() + if (pos.x > w / 2) 1 else 0).coerceAtLeast(0)
                else pager.position.value.roundToInt().coerceAtLeast(0)
            model.updateDrag(pos, geo, page, pageOffsetPx(page))
            // Seitenwechsel am Rand (M-EDGE), nicht über dem Dock
            val x = pos.x
            val right = geo.pagesRightEdge
            val overDock = geo.inDockZone(pos)
            // Duo: Dock-Spalte liegt am rechten Rand → Streifen davor (und links gleich) breiter; ein volles Dock (bzw. Widget)
            // nimmt nichts auf und wirkt wie der Rand — sonst passierte beim Ziehen nach rechts gar nichts
            // Duo: rechts gilt alles ab dem Seitenrand bis zum Bildschirmrand als Rand (auch über/unter dem Dock) —
            // nur die Dock-Glasfläche selbst nimmt Icons auf (Nutzer 10-14: schmaler Streifen war schwer zu treffen)
            val rightZone = if (geo.duo) geo.edgeZone * 2f else geo.edgeZone
            val dockIsEdge = geo.duo && overDock && model.drag?.key?.let { !model.dockAccepts(it) } == true
            val atEdge = (!overDock && (x < rightZone || x > right - rightZone)) || dockIsEdge
            if (atEdge && dragEdgeJob[0] == null) {
                val dir = if (x < rightZone && !dockIsEdge) -1 else 1
                dragEdgeJob[0] = scope.launch {
                    while (isActive) {
                        // echte Position (kann −1 sein: Heute-Ansicht links, Seite 0 rechts)
                        val cur = pager.position.value.roundToInt()
                        // Hinüberschauen (M-EDGE, gemessen): sofort kleiner Ruck, dann gleichmäßig weiter, bis nach der
                        // Haltezeit Haptik + Seitenwechsel kommen. Vor der ersten Seite (neue Seite davor) kein Hinüberschauen.
                        val peeks = !(dir < 0 && cur <= 0) && (cur + dir) in 0 until pager.pageCount
                        val start = System.nanoTime()
                        val peekJob = if (peeks) launch {
                            val perPt = density / geo.pageWidth
                            pager.position.animateTo(cur + dir * Motion.edgePeekNudge.value * perPt, Motion.edgeNudge.spec())
                            // Rest der Haltezeit gleichmäßig weiterkriechen
                            val rest = (Motion.edgeFlipDelay.value - (System.nanoTime() - start) / 1_000_000f).coerceAtLeast(1f)
                            pager.position.animateTo(cur + dir * Motion.edgePeekMax.value * perPt,
                                androidx.compose.animation.core.tween(rest.roundToInt(), easing = androidx.compose.animation.core.LinearEasing))
                        } else null
                        delay(Motion.edgeFlipDelay.value.toLong())
                        peekJob?.cancel()
                        model.haptic() // iOS: spürbarer Tick genau beim Seitenwechsel
                        // links vor der ersten Seite: neue Seite davor anlegen (Ansicht bleibt stehen, dann hinüber)
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
                // Rand vor dem Wechsel verlassen: Seite federt aus dem Hinüberschauen zurück
                val p = pager.position.value
                if (p != p.roundToInt().toFloat()) scope.launch { pager.animateTo(p.roundToInt()) }
            }
        }
        /** Bildschirm-Oberkante-links eines Icons auf den sichtbaren Seiten bzw. im Dock. */
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
            // Ablegen auf einem Ordner/einer App: Icon fliegt in seinen Mini-Platz (M-MERGE-DROP) statt zu verschwinden
            val d = model.drag
            val target = d?.mergeTarget
            val fly = if (d != null && target != null && model.apps[d.key] != null) {
                val slot = if (isFolder(target)) model.folders[target]?.apps?.size ?: 0 else 1
                val folderTl = iconScreenTopLeft(target)
                if (folderTl == null || slot >= DesignTokens.FOLDER_COLUMNS * DesignTokens.FOLDER_ROWS) null else {
                    val s = d.lift.value * dragDockScale.value
                    val c = d.pointer - d.grabOffset + Offset(geo.icon / 2, geo.icon / 2)
                    val from = Rect(c - Offset(geo.icon * s / 2, geo.icon * s / 2), Size(geo.icon * s, geo.icon * s))
                    // Ordner im Dock: sichtbares Icon kleiner (geo.iconRect / dockIconScale) → Mini-Plätze mitskalieren
                    val box = geo.iconRect(folderTl, target in model.display.dock)
                    val k = box.width / geo.icon
                    val mini = DesignTokens.folderMiniSize.value * density * k
                    val tl = box.topLeft + Offset(
                        (DesignTokens.folderMiniInset.value + DesignTokens.folderMiniPitch.value * (slot % DesignTokens.FOLDER_COLUMNS)) * density * k,
                        (DesignTokens.folderMiniInset.value + DesignTokens.folderMiniPitch.value * (slot / DesignTokens.FOLDER_COLUMNS)) * density * k)
                    FolderFly(d.key, target, from, Rect(tl, Size(mini, mini)))
                }
            } else null
            model.drop(geo, pager.position.value)
            // neuer Ordner aus zwei Apps: Ziel ist jetzt der Ordner, der an der Stelle der Ziel-App entstanden ist
            fly?.let { f -> model.flyIntoFolder(FolderFly(f.key, model.folderContaining(f.key) ?: f.folder, f.from, f.to)) }
        }
        /** App aus der Mediathek (Übersicht oder Kategorie) herausziehen: Bearbeiten-Modus, Pager wischt zur letzten Seite. */
        fun startLibraryDrag(appKey: String, pointer: Offset, iconTopLeft: Offset) {
            model.closeContextMenu(animated = false)
            model.leavingLibrary = true
            model.enterEdit()
            // liegt die App schon auf dem Homescreen → weiteres Icon (iOS erlaubt mehrere), das alte bleibt
            model.startDrag(model.newIconKey(appKey), pointer, iconTopLeft)
            val target = (model.display.pages.size - 1).coerceAtLeast(0)
            scope.launch {
                runCatching { pager.animateTo(target, 0f) }
                model.leavingLibrary = false
            }
        }
        // Ziel für die Gesten-Navigation: Icon der App (Seite/Dock) bzw. ihr Ordner auf der aktuellen Seite
        model.iconLocator = locator@{ cn, user ->
            val d = model.display
            fun matches(k: String) = model.apps[k]?.let { it.component == cn && it.user == user } == true
            fun holds(k: String) = matches(k) || (isFolder(k) && model.folders[k]?.apps?.any(::matches) == true)
            val iconSize = Size(geo.icon, geo.icon)
            // Offener Ordner (App wurde daraus geöffnet, iOS lässt ihn offen) → Platz der App im Ordner-Panel
            model.openFolder?.let { of ->
                val apps = model.folderFor(of.id)?.apps.orEmpty().take(DesignTokens.FOLDER_COLUMNS * DesignTokens.FOLDER_ROWS)
                val i = apps.indexOfFirst(::matches)
                if (i >= 0) {
                    val fg = FolderGeometry(density, w, h, geo, of.iconTopLeft.x)
                    return@locator Rect(fg.panel.topLeft + fg.slot(i), iconSize)
                }
            }
            // Duo: Dock-Icons sind kleiner (48 pt statt 60) → Ziel-Rahmen um die Icon-Mitte verkleinern, sonst fliegt das große
            // Icon zurück und springt am Ende auf die Dock-Größe
            fun dockRect(i: Int): Rect {
                val tl = geo.dockPosition(i, d.dock.size)
                if (!geo.duo) return Rect(tl, iconSize)
                val s = geo.icon * geo.dockIconScale
                val c = tl + Offset(geo.icon / 2, geo.icon / 2)
                return Rect(c - Offset(s / 2, s / 2), Size(s, s))
            }
            // mehrere Icons derselben App: das angetippte zuerst (Dock oder sichtbare Seiten), sonst das erste passende
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
            // Aus einem Heute-Widget geöffnet und die Heute-Ansicht ist offen → Fenster fliegt in dieses Widget
            io.github.janhurn9400.liquidy.data.AppLaunchTransition.widgetOrigins[cn.packageName]?.takeIf { it in model.today && pager.position.value < -0.5f }?.let { wk ->
                val off = Offset((-1f - pager.position.value) * geo.sideStride, -model.todayScroll.value)
                TodayLayout.place(model.today, model::sizeOf, geo, density).firstOrNull { it.first == wk }?.let { return@locator it.second.translate(off) }
            }
            // Duo aufgeklappt: zwei Seiten gleichzeitig sichtbar → auch die rechte Seite durchsuchen (sonst flog das Fenster
            // für Apps der rechten Seite nur zur Mitte)
            for (pg in page until page + geo.pagesVisible) {
                val cells = model.displayCells.getOrNull(pg) ?: continue
                // Aus einem Widget geöffnet und das Widget liegt auf dieser Seite → Fenster fliegt ins Widget
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
        /** Heute-Widget unter [p] (Bildschirm) mit Bildschirmrahmen, oder null. */
        fun todayWidgetAt(p: Offset): Pair<String, Rect>? {
            val off = Offset((-1f - pager.position.value) * geo.sideStride, -model.todayScroll.value)
            return TodayLayout.place(model.today, model::sizeOf, geo, density)
                .firstOrNull { it.second.translate(off).contains(p) }?.let { it.first to it.second.translate(off) }
        }
        fun todayMaxScroll(): Float {
            val rects = TodayLayout.place(model.today, model::sizeOf, geo, density)
            return (TodayLayout.contentHeight(rects, density, duoUnfolded = geo.duo && geo.unfolded) - h).coerceAtLeast(0f)
        }
        // sichtbar, sobald die Mediathek in den Bildschirm ragt (aufgeklappt sind zwei Seiten zu sehen)
        // gleiches State-Objekt über Seitenzahl-Änderungen hinweg (kein Schlüssel darauf) → auch in der Gesten-Arena aktuell
        val libraryOnScreen by remember(pager, geo) { derivedStateOf { pager.position.value > model.display.pages.size - geo.pagesVisible } }
        // Rückkehr in die Mediathek nur, wenn sie ganz im Bild ist (nicht halb hinausgewischt)
        model.libraryVisible = { libEnabledNow() && abs(pager.position.value - (model.display.pages.size - geo.pagesVisible + 1)) < 0.05f }

        // Liest model.display frisch (nicht den beim Erzeugen des pointerInput eingefangenen Stand)
        /** Bildschirmrechteck eines Widgets auf einer Homescreen-Seite (für Lande-Animation/Schockwelle). */
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
            // Duo: rechte Spalte (Knöpfe, Lupe) liegt über allem → zuerst prüfen
            if (geo.duo) {
                if (model.editing && geo.editButtonRect(left = false).inflate(6f * density).contains(p)) return Hit.EditDone
                if (model.editing && geo.editButtonRect(left = true).inflate(6f * density).contains(p)) return Hit.EditCustomize
                // Bearbeiten: Punkte liegen in einer Glas-Kapsel → Seitenübersicht (Lupe ist dann weg) [Duo, Video 6, 11 s]
                if (model.editing && duoDotsCapsule(geo, pager.position.value, density).inflate(6f * density).contains(p)) return Hit.PageDots
                if (duoLibAmount() < 0.5f && (geo.unfolded || pager.position.value > -0.5f) && !model.editing && geo.pillRect.inflate(8f * density).contains(p)) return Hit.Pill
            }
            if (pager.position.value < 0f && p.x < -pager.position.value * geo.sideStride) {
                if (model.editing) {
                    val off = Offset((-1f - pager.position.value) * geo.sideStride, -model.todayScroll.value)
                    // Größengriff unten rechts (vor dem Badge), wie auf dem Homescreen
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
                // nur wenn die Mediathek wirklich im Bild ist — Duo aufgeklappt beginnt sie sonst bei 2 × 448 pt, also
                // genau unter dem Dock (Tipps aufs Dock landeten in der unsichtbaren Mediathek)
                if (libraryOnScreen && libOffset < w && p.x >= libOffset) {
                    return Hit.Library(io.github.janhurn9400.liquidy.ui.library.libraryHitTest(model, libGeo, p, libOffset))
                }
            }
            if (model.editing) {
                if (geo.editButtonRect(left = false).contains(p)) return Hit.EditDone
                if (geo.editButtonRect(left = true).contains(p)) return Hit.EditCustomize
            }
            // Duo: Lupe liegt in der rechten Spalte → vor Icons/Mediathek prüfen (sonst überdeckt eine Zelle sie)
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
                // Größengriff unten rechts an Widgets (vor dem Badge/Icon-Treffer)
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
            if (model.editing && geo.pillRect.contains(p)) return Hit.PageDots // iOS: Punkte im Bearbeiten-Modus → Seitenübersicht
            return Hit.Empty
        }

        /** App- oder Ordner-Icon; der offene Ordner wird durch sein Panel ersetzt. */
        @Composable
        fun Item(key: String, showLabel: Boolean, inMenu: Boolean = false) {
            // Im Kontextmenü schwebt eine Kopie über der Abdunkelung → Original ausblenden
            if (!inMenu && model.contextMenu?.key == key) return
            // App-Fenster fliegt gerade in dieses Icon → Icon-Fläche des Systems steht an seiner Stelle.
            // Widget: nur die Fläche ausblenden, der Name bleibt stehen (sonst erscheint er am Ende mit hartem Schnitt)
            // auch: Widget dreht gerade ins Einstellungsblatt (die Kopie im Blatt steht an seiner Stelle)
            val flyingHere = !inMenu && (model.flyingKey == key || model.widgetConfigKey == key)
            if (flyingHere && !isWidget(key)) return
            if (!inMenu && model.stackEditKey == key) return
            // App wächst gerade aus diesem Icon/Widget (Öffnen-Animation) → Platz ist leer wie bei iOS
            if (!inMenu && io.github.janhurn9400.liquidy.data.AppLaunchTransition.launchingKey.value == key) return
            val press = { if (model.pressedKey == key) model.pressAlpha.value else 0f }
            val merge = model.drag?.mergeTarget == key
            Box(Modifier.graphicsLayer {
                // M-CTXMENU: Icon wächst beim Halten (um die Icon-Mitte, nicht die Zellmitte mit Label)
                val s = if (model.pressedKey == key || model.pressScaleKey == key) model.pressScale.value else 1f
                scaleX = s; scaleY = s
                transformOrigin = TransformOrigin(0.5f, DesignTokens.iconSize.value / 2 / (size.height / density))
            }) {
            if (isWidget(key)) {
                val spec = model.widgets[key] ?: return@Box
                val landing = model.widgetLanding?.takeIf { it.key == key }
                Box(Modifier.graphicsLayer {
                    // Flug aus dem Picker: von der Vorschau-Mitte zur Platz-Mitte, landet ≈ 1,12× und schwingt auf 1,0 ein
                    landing ?: return@graphicsLayer
                    alpha = 0f // die fliegende Kopie (über allem) macht die ganze Bewegung; danach steht das echte Widget da
                }) {
                WidgetCell(key, spec, geo, density, wallpaper, jiggle = { model.jiggleAmount.value }, clockSeconds = { clock.floatValue },
                    badgeScale = { model.badgeOf(key) }, showBadge = model.editing || model.badgeScale.value > 0f,
                    pressAlpha = press, resize = model.resize?.takeIf { it.key == key }, showLabel = showLabel, hideBody = flyingHere, stackModel = model)
                }
            } else if (isFolder(key)) {
                val folder = model.folders[key] ?: return
                // Offener Ordner: Icon blendet in der kleinsten Phase ins Panel über (statt hart zu verschwinden)
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

        // Hintergrund-Blur (px) für Ordner (T-FOLDER-BG) und Suche (M-SEARCH.blurRamp, T-SEARCH.blur/blurOpen)
        /** Nur der Such-Anteil der Hintergrund-Unschärfe (px). */
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
            // Heute-Ansicht/Mediathek verwischen nur den Wallpaper, nicht die hinausgleitenden Icons (recording8 4,2 s)
            val today = if (includeToday) sideBlur() * DesignTokens.todayBlur.value else 0f
            // App öffnen/schließen: Scharf/Unscharf vorerst AUS (Nutzer: später genauer prüfen) — siehe M-HOME-RETURN/M-APP-OPEN
            // Stapel bearbeiten: Homescreen in ≈ 2 Bildern unscharf (Bild 287→288), unabhängig vom langsameren Panel
            val stackEdit = (model.stackEdit.value * 4f).coerceIn(0f, 1f) * DesignTokens.folderBgBlur.value
            val config = model.widgetConfig.value * DesignTokens.folderBgBlur.value // Widget-Einstellungen: Homescreen verwischt
            return maxOf(pf * DesignTokens.folderBgBlur.value, if (withSearch) searchBlur else 0f, cat, today, stackEdit, config) * density
        }

        // Hintergrund = immer das Android-Wallpaper hinter dem durchsichtigen Fenster (kein eigenes Launcher-Bild).
        // Unschärfe (Ordner, Suche, Heute, Mediathek) macht Android selbst: Fenster-Hintergrund-Blur verwischt sein Wallpaper.
        val window = remember(view) {
            var c = view.context
            while (c is android.content.ContextWrapper && c !is android.app.Activity) c = c.baseContext
            (c as? android.app.Activity)?.window
        }
        LaunchedEffect(window) { window?.setBackgroundBlurRadius(0) }
        // In Ruhe zeigt Android sein Wallpaper hinter dem durchsichtigen Fenster. Sobald der Hintergrund verschwimmen soll
        // (Ordner, Suche, Heute, Mediathek …) legen wir unsere deckungsgleiche Kopie (= Androids Ausschnitt) verwischt
        // darüber — das sieht auf jedem Gerät gleich aus (Androids Fenster-Unschärfe ist nicht überall verfügbar).
        // Selbst gezeichnet wird immer, wenn wir Androids Bild nicht kennen ([drawWallpaper]) und im Editor.
        // Schlüssel: sonst bliebe die Rechnung vom ersten Aufbau (z. B. auf-/zugeklappt) eingefroren
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

        // Homescreen-Inhalt (Icons, Dock, Pill): unscharf + bei der Suche verschoben/verkleinert. Decal = außen transparent.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // Zuklappen: erst nur gedimmt+unscharf, ab 62 % des Ablaufs wird es scharf
                    val f = foldIn.value
                    val foldBlur = (f / FOLD_SHARPEN_SHARE).coerceIn(0f, 1f) * FOLD_BLUR_DP * density
                    // Duo: bei der Suche bleibt die Dock-Spalte scharf und still [Video 5, 13,1 s] → Such-Unschärfe/Versatz
                    // dann nur auf Seiten + Heute (unten), nicht auf diesem gemeinsamen Ebene
                    val r = maxOf(backgroundBlurPx(withSearch = !geo.duo), foldBlur)
                    renderEffect = if (r > 0.5f) BlurEffect(r, r, TileMode.Decal) else null
                    alpha = overviewHide()
                    if (!geo.duo) {
                        val hs = model.searchProgress.value // Homescreen-Versatz h (pt)
                        translationY = hs * density
                        val sc = model.searchContentScale()
                        scaleX = sc; scaleY = sc
                    }
                }
        ) {

        // ── Seiten-Icons (+ Mediathek): Quelle des Glases ──────────────────────────
        Box(Modifier.fillMaxSize().layerBackdrop(glassBackdrop)) {
        val visiblePages by remember(pager, geo) {
            derivedStateOf { floor(pager.position.value).toInt()..(ceil(pager.position.value).toInt() + geo.pagesVisible - 1) }
        }
        // Seitenübersicht: Icons schon IN der Glasquelle ausblenden — sonst brechen die Glaskarten die echte Seite
        Box(Modifier.fillMaxSize().graphicsLayer {
            alpha = overviewHide()
            // Duo aufgeklappt: Seite links neben der Mediathek wird unscharf (Aufnahme 35,5 s)
            val lb = maxOf(if (geo.unfolded) duoLibAmount() * DUO_LIB_PAGE_BLUR * density else 0f, if (geo.duo) searchBlurPx() else 0f)
            renderEffect = if (lb > 0.5f) BlurEffect(lb, lb, TileMode.Decal) else null
            if (geo.duo) {
                translationY = model.searchProgress.value * density
                val sc = model.searchContentScale(); scaleX = sc; scaleY = sc
            }
        }) {
        display.pages.forEachIndexed { page, keys ->
            if (page in visiblePages) Box(Modifier.fillMaxSize().graphicsLayer {
                // Seitenübersicht: jede sichtbare Seite zoomt mit IHRER Karte (Duo: links und rechts gleichzeitig)
                // Fortschritt immer lesen → Block läuft jedes Bild mit, auch wenn die Karte erst ein Bild später eingetragen wird
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
                    // key(): Komposition folgt dem Icon, nicht dem Listenplatz → Umsortieren animiert korrekt
                    if (key != model.drag?.key) key(key) {
                        IconSlot(model, key, Container.Page(page), geo.slotPosition(cell), pager, geo::pageShift, density) {
                            Item(key, showLabel = true)
                        }
                    }
                }
            }
        }

        } // Ende Übersicht-Ausblendung

        // ── App-Mediathek ─────────────────────────────────────────────────────────
        // Duo: Mediathek erst NACH dem Dock zeichnen (unten) — das Dock liegt dahinter und verschwimmt mit
        if (libEnabledNow() && libIndexNow() in visiblePages && !geo.duo) {
            io.github.janhurn9400.liquidy.ui.library.LibraryPage(model, libGeo, wallpaper, density) { geo.libraryOffset(libIndexNow() - pager.position.value) }
        }
        } // Ende Glas-Quelle

        // ── Mediathek: Scroll-Kante + Glas-Suchfeld (außerhalb der Quelle, damit es die Kacheln bricht) ──
        if (libEnabledNow() && libraryOnScreen && !geo.duo) {
            io.github.janhurn9400.liquidy.ui.library.LibraryChrome(model, libGeo, density) { geo.libraryOffset(libIndexNow() - pager.position.value) }
        }

        // Duo-Suche [GEMESSEN Video 5, 12,5 → 13,1 s]: Hintergrund (auch hinter dem Dock) ≈ 40 % dunkler, Dock-Icons
        // unverändert hell → Abdunkeln hier, unter dem Dock
        if (geo.duo && model.searchVisible) Canvas(Modifier.fillMaxSize()) {
            drawRect(Color.Black.copy(alpha = model.searchDim() * (DUO_SEARCH_DIM / DesignTokens.searchDimMax.value)))
        }

        // ── Dock ────────────────────────────────────────────────────────────────
        // Duo aufgeklappt, Mediathek (Aufnahme 10:40, 15,3–16,2 s): Dock samt Glas bleibt stehen und verschwimmt wie die
        // Seite daneben – es liegt UNTER der hereinkommenden Mediathek-Ebene; Kategorie auf (16,79–16,92 s): Dock gleitet rechts aus dem Bild
        Box(Modifier.fillMaxSize().graphicsLayer {
            // Duo: bei offener A–Z-Liste steht der Index in der Dock-Spalte → Dock weg [Video 2, 18 s]
            alpha = homeChromeAlpha() * (1f - duoWpeHide) * (if (geo.duo && model.libListOpen) (1f - model.libList.value).coerceIn(0f, 1f) else 1f)
            val lib = duoLibAmount(); val cat = duoCatAmount()
            // Kategorie bzw. (zugeklappt) offene Suche schieben das Dock rechts weg
            // aufgeklappt: Dock gleitet sofort mit dem Herunterziehen zur Suche rechts hinaus (folgt dem Finger, voll bei
            // halbem Abdunkeln) und beim Schließen zurück [Duo, Nutzer 10-11]; zugeklappt erst verzögert (duoSearchDockOut)
            val unfoldedSearch = if (geo.duo && geo.unfolded && model.searchVisible)
                (model.searchDim() / (DesignTokens.searchDimMax.value * DUO_SEARCH_DOCK_FULL_AT)).coerceIn(0f, 1f).let { it * it * (3f - 2f * it) } else 0f
            translationX = maxOf(cat, duoSearchDockOut.value, unfoldedSearch) * (size.width - geo.dockRect.left + DUO_DOCK_OUT_MARGIN * density).coerceAtLeast(DUO_CAT_DOCK_SHIFT * density)
            val b = lib * DUO_LIB_PAGE_BLUR * density
            renderEffect = if (b > 0.5f) BlurEffect(b, b, TileMode.Decal) else null
        }) {
        LiquidGlass(
            // Duo: schmale senkrechte Glasleiste mit eigenem Radius (Kachel-Ecken ≈ 22 pt)
            shape = glassRect(if (geo.duo) androidx.compose.ui.unit.Dp(DUO_DOCK_CORNER) else DesignTokens.dockCorner.dp),
            // Duo: deutlicher heller Lichtsaum rundherum (Referenz 10:40, 13 s)
            rimAlpha = if (geo.duo) DUO_DOCK_RIM else null,
            // Rahmen erst beim Layout lesen: die Feder läuft dann ohne Neuaufbau des Homescreens
            modifier = Modifier.layout { m, _ ->
                val r = geo.dockRectFor(dockSlots.value)
                val p = m.measure(androidx.compose.ui.unit.Constraints.fixed(r.width.roundToInt(), r.height.roundToInt()))
                layout(p.width, p.height) { p.place(r.left.roundToInt(), r.top.roundToInt()) }
            },
        )
        display.dock.forEachIndexed { i, key ->
            if (key != model.drag?.key) key(key) {
                IconSlot(model, key, Container.Dock, geo.dockPosition(i, display.dock.size), pager, geo::pageShift, density) {
                    // Duo: Dock-Icons kleiner als das Raster (≈ 48 statt 60 pt, viel Glas rundherum) [GEMESSEN] 21:29, 44 s
                    if (geo.duo) Box(Modifier.graphicsLayer {
                        val sc = geo.dockIconScale; scaleX = sc; scaleY = sc
                        transformOrigin = TransformOrigin(0.5f, DesignTokens.iconSize.value / 2 / (size.height / density))
                    }) { Item(key, showLabel = false) } else Item(key, showLabel = false)
                }
            }
        }

        } // Ende Dock

        // Duo (auf- und zugeklappt): Mediathek als Ebene ÜBER dem (unscharfen) Dock
        // eigene Glas-Quelle, damit das Suchfeld die Kacheln weiterhin bricht (liegt außerhalb der Haupt-Quelle)
        if (libEnabledNow() && geo.duo) {
            if (libraryOnScreen) Box(Modifier.fillMaxSize().then(if (libPageBackdrop != null) Modifier.layerBackdrop(libPageBackdrop) else Modifier)) {
                io.github.janhurn9400.liquidy.ui.library.LibraryPage(model, libGeo, wallpaper, density) { geo.libraryOffset(libIndexNow() - pager.position.value) }
            }
        }

        // ── Edit-Buttons oben ─────────────────────────────────────────────────────
        if (model.editing || model.badgeScale.value > 0f) {
            // Duo: der Pinsel wird selbst zum Tropfen → solange das Menü offen ist, steht dort kein Knopf (Video 4)
            EditButtons(geo, wallpaper, density, showLeft = !(geo.duo && model.editMenuOpen),
                popIn = if (geo.duo) ({ model.editButtons.value }) else null) { model.badgeScale.value }
        }
        // ── Heute-Ansicht (links von Seite 0) — im Homescreen-Inhalt: beim Herunterziehen für die Suche rutscht sie
        // genauso nach unten, schrumpft und wird unscharf wie die Icons (recording8 5–7 s)
        val todayVisible by remember(pager) { derivedStateOf { pager.position.value < -0.001f } }
        if (todayVisible) Box(Modifier.fillMaxSize().zIndex(-1f).graphicsLayer {
            if (geo.duo) {
                val b = searchBlurPx(); renderEffect = if (b > 0.5f) BlurEffect(b, b, TileMode.Decal) else null
                translationY = model.searchProgress.value * density
                val sc = model.searchContentScale(); scaleX = sc; scaleY = sc
            }
        }) { TodayPage(model, geo, density, wallpaper, clockSeconds = { clock.floatValue }) { (-1f - pager.position.value) * geo.sideStride } }
        } // Ende Homescreen-Inhalt

        // Zuklappen: Abdunkeln (gemessen Helligkeit 105 → 142, also ≈ 26 % dunkler am Anfang), linear aufhellend
        if (foldIn.value > 0f) Canvas(Modifier.fillMaxSize()) { drawRect(Color.Black.copy(alpha = FOLD_DIM * foldIn.value)) }

        // Suche: Abdunkeln proportional zum Fortschritt (T-SEARCH.dim)
        if (model.searchVisible) {
            // Duo: Abdunkeln liegt UNTER dem Dock (siehe vor „Dock“) — hier nur iPhone
            if (!geo.duo) Canvas(Modifier.fillMaxSize()) { drawRect(Color.Black.copy(alpha = model.searchDim())) }
        }

        // ── Pill / Dots / Suchfeld-Morph — außerhalb der verwischten Ebene (bleibt beim Ziehen scharf, recording5) ──
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // In der Heute-Ansicht ist die Pill weg, das Suchfeld erscheint aber beim Herunterziehen (Morph)
                    alpha = maxOf(homeChromeAlpha(), model.searchMorph.value.coerceIn(0f, 1f)) * overviewHide() *
                        (1f - model.stackEdit.value.coerceIn(0f, 1f)) // Stapel bearbeiten: Pill weg
                    // bei offenem Ordner mit dem Homescreen verwischt (IMG_3130)
                    val r = (model.openFolder?.progress?.value ?: 0f) * DesignTokens.folderBgBlur.value * density
                    renderEffect = if (r > 0.5f) BlurEffect(r, r, TileMode.Decal) else null
                }
        ) {
            // Duo: in der Mediathek verschwinden Lupe + Punkte (Aufnahme 10:40, ab 15,6 s – oben ist das Mediathek-Suchfeld)
            // zugeklappt: auf der Heute-Seite sind Punkte + Lupe weg [GEMESSEN 19:07, 5 s]
            if (geo.duo) Box(Modifier.fillMaxSize().graphicsLayer {
                val today = if (geo.unfolded) 0f else (-pager.position.value).coerceIn(0f, 1f)
                alpha = (1f - duoLibAmount()) * (1f - duoWpeHide) * (1f - today)
            }) {
                // Punkte gehören zum Homescreen-Inhalt: beim Herunterziehen zur Suche rutschen, schrumpfen und verschwimmen sie mit
                // den Seiten (gleiche Ebene-Werte wie die Seiten) [Duo, Nutzer 10-11]
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

        // ── Stapel bearbeiten ─────────────────────────────────────────────────────
        model.stackEditKey?.let { sk ->
            Canvas(Modifier.fillMaxSize()) { drawRect(Color.Black.copy(alpha = 0.18f * (model.stackEdit.value * 4f).coerceIn(0f, 1f))) }
            StackEditor(model, sk, geo, density, wallpaper, w, h)
        }

        // ── Seitenübersicht („Seiten bearbeiten“) ────────────────────────────────
        LaunchedEffect(model.pagesOverviewRequested) {
            if (model.pagesOverviewRequested) { model.pagesOverviewRequested = false; openOverview() }
        }
        if (model.pagesOverviewOpen) {
            PageOverview(model, geo, density, wallpaper, w, h) { page ->
                // Duo aufgeklappt: war die Seite rechts sichtbar, bleibt sie rechts (kein Sprung nach links) — Karte zoomt dorthin
                // (echte Position: −1 = Heute links, Seite 0 rechts)
                val right = geo.unfolded && page == pager.position.value.roundToInt() + 1
                model.overviewLeftPage = if (right) page - 1 else page
                scope.launch { pager.position.snapTo((if (right) page - 1 else page).toFloat()) }
            }
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = model.pagesOverview.value }) {
                EditButtons(geo, wallpaper, density, showLeft = false) { 1f }
            }
        }

        // ── Offener Ordner ────────────────────────────────────────────────────────
        if (openFolder != null) {
            Canvas(Modifier.fillMaxSize()) {
                drawRect(Color.Black.copy(alpha = DesignTokens.folderBgDim.value * openFolder.progress.value.coerceIn(0f, 1f)))
            }
            FolderOverlay(model, openFolder, folderGeo, density, wallpaper) { clock.floatValue }
            FolderDragCopy(model, density, wallpaper)
        }

        // ── Gezogenes Icon (über allem) ──────────────────────────────────────────
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
            // App bzw. Ordner (Ordner fehlten hier → beim Ziehen war nichts zu sehen)
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

        // ── Aufnehmen: Original blendet an der Ausgangsstelle aus, sein Badge schrumpft weg (M-BADGE-DRAG) ──
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

        // ── Icon fliegt in den Ordner (M-MERGE-DROP): von der Lage beim Loslassen auf Mini-Größe in den freien Platz ──
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
        // (bei offener Seitenübersicht abgeschaltet — die Übersicht darunter bekommt die Berührungen)
        if (!model.pagesOverviewOpen && model.stackEditKey == null) Box(
            Modifier
                .fillMaxSize()
                .onGloballyPositioned { arenaCoords[0] = it }
                .pointerInput(geo) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (model.pendingRemoval != null) return@awaitEachGesture
                        model.openFolder?.let { of ->
                            // frisch berechnen: die Gesten-Arena wird nur einmal angelegt (sonst Lage des ERSTEN geöffneten Ordners)
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

                        /** App aus der Mediathek herausziehen: Bearbeiten-Modus, Pager wischt zur letzten Seite (Ablageziel). */
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
                            // Heute-Widget: Bild für den Rückflug beim Aufsetzen aufnehmen (wie auf dem Homescreen)
                            todayWidgetAt(down.position)?.let { (k, r) ->
                                window?.let { io.github.janhurn9400.liquidy.data.AppLaunchTransition.snapshotWidget(it, k, android.graphics.RectF(r.left, r.top, r.right, r.bottom)) }
                            }
                        }
                        if (hit is Hit.Library && !model.editing && !wasAnimating) {
                            (hit.hit as? io.github.janhurn9400.liquidy.ui.library.LibraryHit.App)?.let { model.prefetchShortcuts(libraryMenuKey(it.app.key), density) }
                        }
                        // Android-Widget: Berührung von Anfang an mitschicken (Drück-Zustand, Scrollen); Launcher kann noch übernehmen
                        if (hit is Hit.Icon && !model.editing && !wasAnimating && model.widgets[hit.key]?.type == "android") {
                            model.widgets[hit.key]?.let { io.github.janhurn9400.liquidy.ui.widgets.AndroidWidgetTaps.begin(it.appWidgetId, arenaToWindow(down.position)) }
                        }
                        if (hit is Hit.Icon && !model.editing && !wasAnimating) {
                            // Widget: Bild für den Rückflug JETZT aufnehmen — das angezeigte Bild hat noch keine Drück-Abdunklung
                            // (beim Loslassen wäre es dunkel → Fläche fliegt dunkel zurück und das echte Widget blitzt hell auf)
                            model.widgets[hit.key]?.let { spec ->
                                val r = geo.widgetRect(hit.iconTopLeft, spec.cols, spec.rows)
                                window?.let { io.github.janhurn9400.liquidy.data.AppLaunchTransition.snapshotWidget(it, hit.key, android.graphics.RectF(r.left, r.top, r.right, r.bottom)) }
                            }
                            model.pressedKey = hit.key
                            model.pressScaleKey = hit.key
                            model.prefetchShortcuts(hit.key, density)
                            // M-CTXMENU: langsam wachsen, dann das Dunkel ausblenden (vor dem Menü)
                            pressJob = scope.launch {
                                // Widgets wachsen beim Halten nicht (recording8 12,3–13,3 s: Skala bleibt 1,0)
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
                                // Android-Widgets zeigen ihren eigenen Drück-Effekt (Berührung wird durchgereicht) → nicht zusätzlich abdunkeln
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
                                io.github.janhurn9400.liquidy.ui.widgets.AndroidWidgetTaps.cancel() // Halten → Launcher-Menü, kein Klick im Widget
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
                                            tw == null -> { model.enterEdit(); mode = Mode.Consumed } // leere Stelle → Bearbeiten
                                            model.editing -> { model.beginTodayDrag(tw.first); mode = Mode.TodayDrag } // wackelnd → ziehen
                                            else -> { // Kontextmenü mit Größenleiste (wie Homescreen)
                                                model.openContextMenu(tw.first, tw.second.topLeft - geo.widgetOffset, density, tw.second)
                                                mode = Mode.Menu
                                            }
                                        }
                                    }
                                    // Mediathek: App lange drücken → Menü (Löschen, Info, Shortcuts); Ziehen holt sie auf den Homescreen
                                    hit is Hit.Library && hit.hit is io.github.janhurn9400.liquidy.ui.library.LibraryHit.App && !model.editing -> {
                                        val a = hit.hit as io.github.janhurn9400.liquidy.ui.library.LibraryHit.App
                                        // eigener Menü-Key: sonst verschwindet das gleiche Icon auf dem Homescreen, solange das Menü offen ist
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
                                                model.libraryLaunch = lh.app.key to lh.screenRect // nach onLaunch (setzt es sonst zurück)
                                            }
                                            is io.github.janhurn9400.liquidy.ui.library.LibraryHit.Category -> model.openCategory(lh.index)
                                            io.github.janhurn9400.liquidy.ui.library.LibraryHit.Search -> model.openLibList()
                                            io.github.janhurn9400.liquidy.ui.library.LibraryHit.Empty -> if (wasAnimating) scope.launch { pager.settle() }
                                        }
                                        hit is Hit.Pill -> if (geo.duo) model.openSearchFromDuoLupe() else model.openSearch() // iOS: Tippen auf „Search“ öffnet die Suche
                                        hit is Hit.Today && model.editing -> todayWidgetAt(down.position).let { tw ->
                                            if (tw == null) model.exitEdit()
                                            else if (io.github.janhurn9400.liquidy.ui.widgets.hasWidgetConfig(model.widgets[tw.first])) model.openWidgetConfig(tw.first, tw.second)
                                        }
                                        hit is Hit.Today -> {
                                            // Widget in der Heute-Ansicht antippen → App öffnen
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
                                                // Duo-Dock: kleineres Icon (48 pt) → Fenster wächst aus dessen echter Größe
                                                val r = Rect(hit.iconTopLeft, Size(geo.icon, geo.icon))
                                                val k = if (hit.key in model.display.dock) geo.dockIconScale else 1f
                                                onLaunch(it, if (k == 1f) r else Rect(r.center - Offset(r.width * k / 2, r.height * k / 2), Size(r.width * k, r.height * k)))
                                            }
                                            model.shownWidget(hit.key)?.let { spec ->
                                                if (spec.type == "android") { // Gesten-Ebene liegt darüber → Berührung ins Widget weiterreichen
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
                                // Menü offen und Finger zieht weiter → Menü weg, Bearbeitungsmodus + Icon ziehen (wie iOS)
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
                                        // Widget kann in diese Richtung selbst scrollen/wischen → Geste gehört dem Widget
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
                                            // Widget-Stapel: senkrecht wischen blättert (statt Suche)
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
                                            // Mediathek: vertikal scrollen; sonst nach unten ziehen → Suche
                                            mode = when {
                                                // Ganz oben nach unten ziehen → A–Z-Liste (recording2 10,1 s); sonst scrollen
                                                hit is Hit.Library && acc.y > 0 && model.libraryScroll.value <= LIB_PULL_TOP && pager.position.value.let { abs(it - (libIndexNow() - geo.pagesVisible + 1)) < 0.01f } -> {
                                                    scope.launch { model.libraryScroll.animateTo(0f, Motion.scrollBounce.spec()) }
                                                    model.libListPullBegin(); Mode.LibPull
                                                }
                                                hit is Hit.Library -> { libScrollStart = model.libraryScroll.value; scope.launch { model.libraryScroll.stop() }; Mode.LibScroll }
                                                // Heute-Ansicht: ganz oben nach unten → Suche (wie iOS), sonst scrollen
                                                // Duo aufgeklappt: Ziehen nach unten auf der Heute-Spalte scrollt (Gummiband) statt die Suche zu öffnen [Aufnahme 14:57]
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
                                    // Über den rechten Rand der Heute-Seite hinaus (aufgeklappt: auf die Homescreen-Seite daneben,
                                    // sonst an den Bildschirmrand) → Widget wandert auf den Homescreen und wird dort normal weitergezogen
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
                                    // Finger → Homescreen-Versatz mit UIScrollView-Gummiband (M-RUBBER, d = Bildschirmhöhe)
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

        // ── Eigene Statusleiste (Einstellung; Android-Leiste ist dann im Launcher ausgeblendet) ─────
        @Composable fun StatusLayer() { if (io.github.janhurn9400.liquidy.data.OwnStatusBar.enabled && !model.editing && !model.wallpaperEditorOpen && model.stackEditKey == null) {
            val info by io.github.janhurn9400.liquidy.data.rememberStatusInfo()
            val color = if (DesignTokens.wallpaperIsDark || geo.duo) Color.White else Color.Black
            Box(Modifier.fillMaxSize().graphicsLayer {
                // mit Ordner/Suche ausblenden wie die Android-Leiste
                val folder = ((model.openFolder?.progress?.value ?: 0f) / 0.3f).coerceIn(0f, 1f)
                alpha = (1f - folder) * (if (geo.duo) 1f else 1f - model.searchOpenAmount.value.coerceIn(0f, 1f))
            }) {
                if (geo.duo) DuoStatusSymbols(info, w - DUO_STATUS_FROM_RIGHT * density, density, color)
                else IosStatusBar(info, statusBarPx.takeIf { it > 0f } ?: (IOS_STATUS_H * density), density, color)
            }
        } }
        // Duo: erst nach Mediathek-Liste/-Suchfeld zeichnen (deren verlaufende Kante lag sonst über den Symbolen)
        if (!geo.duo) StatusLayer()

        // Reihenfolge: Mediathek-Liste/-Suchfeld/Kategorie über der Arena, darüber die Duo-Symbole — aber UNTER Widget-
        // Einstellungen, Picker, Launcher-Einstellungen, Edit- und Kontextmenü (die decken alles ab).
        // ── Mediathek: A–Z-Liste und aufgeklappte Kategorie (über der Arena, eigene Gesten) ──
        val libListBackdrop = if (geo.duo) com.kyant.backdrop.backdrops.rememberLayerBackdrop() else null
        if (model.libListOpen) {
            io.github.janhurn9400.liquidy.ui.library.LibraryList(model, libGeo, density,
                offsetX = { if (geo.duo) geo.libraryOffset(libIndexNow() - pager.position.value) else 0f },
                listBackdrop = libListBackdrop, onLaunch = onLaunch)
        }
        // Duo: Suchfeld ÜBER der Liste; sein Glas bricht Hintergrund + Zeilen (kombinierte Quelle) [Video 2, 24,8 s]
        if (geo.duo && libEnabledNow() && libraryOnScreen) {
            val base = io.github.janhurn9400.liquidy.glass.LocalGlassBackdrop.current
            // Hintergrund + Mediathek-Kacheln (+ A–Z-Zeilen, wenn offen)
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
                // Kategorie blieb (unsichtbar) offen, damit ihre Geste weiterläuft → jetzt ohne Animation schließen
                onDragEnd = { iconDragEnd(); model.closeCategoryNow() })
        }

        val remindersAnim = remember { androidx.compose.animation.core.Animatable(0f) }
        LaunchedEffect(io.github.janhurn9400.liquidy.ui.widgets.RemindersStore.sheetOpen) {
            remindersAnim.animateTo(if (io.github.janhurn9400.liquidy.ui.widgets.RemindersStore.sheetOpen) 1f else 0f,
                androidx.compose.animation.core.spring(0.85f, 500f))
        }
        // ── Widget-Einstellungen ──────────────────────────────────────────────────────
        model.widgetConfigKey?.let { k -> model.widgets[k]?.let { spec ->
            androidx.activity.compose.BackHandler { model.closeWidgetConfig() }
            io.github.janhurn9400.liquidy.ui.widgets.WidgetConfigSheet(spec, model.widgetConfigFrom, { model.widgetConfig.value },
                onChange = { model.setWidgetConfig(k, it) }, onClose = { model.closeWidgetConfig() })
        } }

        // ── Erinnerungen-Liste (Antippen des Erinnerungen-Widgets) ──────────────────────
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
                    // Picker → immer Homescreen (Heute-Seite hat ihren eigenen Weg). Offen auf Heute → erst zu Seite 1 blättern
                    val onToday = pager.position.value < -0.5f
                    val page = if (onToday) 0 else pager.currentPage.coerceAtMost(model.display.pages.lastIndex)
                    if (onToday) scope.launch { pager.animateTo(0) }
                    val id = model.addWidget(spec, page)
                    // fliegt aus der Vorschau des Detail-Blatts (Bildschirmmitte, ≈ 55 % Höhe) an seinen Platz
                    model.startWidgetLanding(id, io.github.janhurn9400.liquidy.ui.widgets.PickerLaunch.previewCenter ?: Offset(w / 2, h * LAND_FROM_Y)) { screenRectOf(id)?.center }
                    model.enterEdit() // iOS: neues Widget wackelt auf der Seite (recording8 31 s)
                    }
                },
                onClose = { model.widgetPickerOpen = false; model.addingToStack = false },
            )
        }

        // ── Launcher-Einstellungen ──────────────────────────────────────────────────────
        if (model.settingsOpen) LauncherSettingsSheet(model, onClose = { model.settingsClosed() })

        // ── Customise-Blatt (Icon-Stil) ─────────────────────────────────────────────────
        if (model.customiseOpen) {
            CustomiseSheet(model, sample = display.dock.firstOrNull()?.let { model.apps[it] } ?: model.apps.values.firstOrNull(),
                wallpaper = wallpaper, onClose = { model.customiseClosed() })
        }

        // Duo: Pinsel-Knopf leuchtet auf, bevor das Menü herausfließt
        if (geo.duo && !model.editMenuOpen && model.editBrushFlash.value > 0.01f) Canvas(Modifier.fillMaxSize()) {
            val r = geo.editButtonRect(left = true)
            drawCircle(Color.White.copy(alpha = 0.85f * model.editBrushFlash.value), r.width / 2, r.center)
        }

        // ── Edit-Menü (wächst aus dem Knopf oben links, recording6 4,9 s) ────────────────
        if (model.editMenuOpen && !model.wallpaperEditorOpen) { // im Editor nicht als Geisterbild in der Vorschau
            Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures(onPress = { model.closeEditMenu() }) })
            val r = geo.editButtonRect(left = true)
            // Duo (Aufnahme 21:28, 53,5–54,2 s): Menü rechtsbündig mit der Spalte, Oberkante knapp unter dem Pinsel; es
            // fließt als Tropfen aus dem Knopf nach links unten (Ursprung = Pinsel-Mitte) und schwingt leicht über.
            if (geo.duo) {
                // Duo: Glastropfen aus dem Pinsel (Video 4) — rechtsbündig mit der Spalte, Oberkante knapp unter dem Pinsel
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

        // ── Kontextmenü (über der Arena, eigene Gesten) ──────────────────────────────
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

        // ── Suche ─────────────────────────────────────────────────────────────────
        if (model.searchVisible) {
            io.github.janhurn9400.liquidy.ui.search.SearchOverlay(model, onLaunch,
                // Duo zugeklappt [Video 5, 13,1 s]: Vorschläge links bis vor die Dock-Spalte, Dock + Statussymbole bleiben sichtbar
                columnWidth = when {
                    geo.unfolded -> pxToDp(geo.duoSearchWidth + DUO_SEARCH_SUGGEST_EXTRA * density, density)
                    geo.duo -> androidx.compose.ui.unit.Dp(geo.duoDockLeftPt - DUO_FOLDED_SEARCH_GAP)
                    else -> null
                }, columnStart = geo.duo && !geo.unfolded)
        }

        // ── Fliegendes Widget: EIN Bogen über allem (Aufnahme 10:40, 47,56–48,20 s) ─────────────
        // Position folgt dem Fortschritt, Größe 0,92 → ≈ 1,12 (kurz vor der Landung) → 1,0, Inhalt unterwegs unscharf.
        model.widgetLanding?.let { l ->
            val spec = model.widgets[l.key]
            val to = screenRectOf(l.key)
            if (spec != null && to != null) {
                val ws = geo.widgetSize(spec.cols, spec.rows)
                // zurückbleibender, weicher Schatten an der Vorschau-Position (Referenz 47,60–47,80)
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

        // ── Lösch-Dialog ──────────────────────────────────────────────────────────
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
    } // Ende CompositionLocalProvider
}

/** Platziert ein Icon an seiner animierten Position (M-REFLOW im Bearbeitungsmodus, sonst sofort). */
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
            // Containerwechsel: über Bildschirmkoordinaten umrechnen, damit nichts springt
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
        }
    ) { content() }
}

/**
 * Schockwelle (Widget landet): radialer Stoß nach außen, der mit Wellengeschwindigkeit wandert und gedämpft zurückfedert;
 * ≈ 6 pt bei den direkten Nachbarn (≈ 100 pt Abstand), weiter weg schwächer [GEMESSEN grob] 10:40, 47,9–48,2 s.
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
 * Gesten bei offenem Ordner: Tippen auf Icon → App starten, Long-Press → aus dem Ordner ziehen
 * (Ordner schließt, Ziehen geht auf dem Homescreen weiter), Tippen außerhalb → Ordner schließen.
 */
private suspend fun androidx.compose.ui.input.pointer.AwaitPointerEventScope.handleFolderGesture(
    model: HomeModel,
    open: OpenFolder,
    geo: FolderGeometry,
    down: androidx.compose.ui.input.pointer.PointerInputChange,
    density: Float,
    onLaunch: (AppEntry, Rect) -> Unit,
    scope: kotlinx.coroutines.CoroutineScope,
    /** Weiterziehen auf dem Homescreen, nachdem die App den Ordner verlassen hat (wie das normale Icon-Ziehen). */
    dragMove: (Offset) -> Unit,
    dragEnd: () -> Unit,
) {
    val folder = model.folderFor(open.id) ?: return
    val panel = geo.panel
    val icon = DesignTokens.iconSize.value * density
    val perPage = DesignTokens.FOLDER_COLUMNS * DesignTokens.FOLDER_ROWS
    // Ordner-Seiten (je 9 Apps; im Bearbeiten-Modus eine leere mehr, iOS) — getroffen wird nur die gerade sichtbare
    val pageCount = ((folder.apps.size + perPage - 1) / perPage + (if (model.editing) 1 else 0)).coerceAtLeast(1)
    val page = model.folderPage.value.roundToInt().coerceIn(0, pageCount - 1)
    val onPage = folder.apps.withIndex().filter { it.index / perPage == page }
    fun slotTopLeft(i: Int) = panel.topLeft + geo.slot(i % perPage)
    val hitKey = onPage.firstOrNull { (i, _) -> Rect(slotTopLeft(i), Size(icon, icon)).inflate(icon * 0.2f).contains(down.position) }
    val insidePanel = panel.contains(down.position)
    // Bearbeiten: Tippen aufs Umbenennen-Feld → Textfeld bekommt Fokus + Tastatur (die Arena liegt darüber);
    // rechts der runde ×-Knopf leert den Namen. Jede andere Berührung im Ordner beendet das Umbenennen.
    if (model.editing && geo.titleRect.contains(down.position)) {
        while (true) { val e = awaitPointerEvent(); if (e.changes.none { it.pressed }) break }
        if (down.position.x >= geo.titleRect.right - geo.nameClearZone) model.renameFolder(open.id, "")
        else model.folderRenameTap = Offset(down.position.x, down.position.y)
        model.folderRenaming = true
        return
    }
    model.folderRenaming = false
    // Minus-Badge → „App entfernen?“ wie auf dem Homescreen
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
    // Blättern im Ordner: waagerecht im Panel, folgt dem Finger, rastet mit M-PAGE ein [GEMESSEN Nutzer-iPhone 10-14, 18,4 s]
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
                val v = -tracker.calculateVelocity().x / panel.width // Seiten/s
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
            // über die erste/letzte Seite hinaus mit Gummiband (M-RUBBER, d = Panelbreite)
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
 * App im offenen Ordner angehoben (iOS): sie hängt am Finger, der Ordner bleibt offen. Ist ihre Mitte [Motion.folderOutDelay]
 * lang außerhalb des Panels, schließt der Ordner und das Ziehen geht auf dem Homescreen weiter [GEMESSEN Nutzer-iPhone 10-14,
 * 23,27 → 23,42 s]. Loslassen im Panel → zurück an ihren Platz.
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
    // Umsortieren im Ordner wie auf dem Homescreen: Platz unter dem Icon, erst nach M-REFLOW.delay Stillstand übernehmen
    val perPage = DesignTokens.FOLDER_COLUMNS * DesignTokens.FOLDER_ROWS
    val others = (model.folderFor(folderId)?.apps?.size ?: 1) - 1
    var candidate = -1; var candidateSince = 0L
    while (true) {
        // auch ohne Bewegung weiterprüfen (Finger hält still außerhalb des Panels)
        val e = withTimeoutOrNull(FOLDER_DRAG_POLL_MS) { awaitPointerEvent() }
        if (e != null) {
            val ch = e.changes.firstOrNull { it.id == down.id }
            if (ch == null || !ch.pressed) { // im Ordner losgelassen → an den Einfügeplatz (sonst zurück an den Platz)
                if (fd.hoverIndex >= 0) model.reorderInFolder(folderId, key, fd.hoverIndex)
                fd.hoverIndex = -1
                // Kopie fliegt in ihren Platz (FolderDragCopy), danach steht dort das echte Icon
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
            // nächster Platz auf der sichtbaren Ordnerseite
            val j = (0 until perPage).minBy { (panel.topLeft + geo.slot(it) + Offset(icon / 2, icon / 2) - center).getDistance() }
            val idx = (page * perPage + j).coerceIn(0, others)
            if (idx != candidate) { candidate = idx; candidateSince = now }
            else if (idx != fd.hoverIndex && (now - candidateSince) / 1_000_000 >= Motion.reflowDelay.value) fd.hoverIndex = idx
        }
        if (outsideSince != null && (now - outsideSince) / 1_000_000 >= Motion.folderOutDelay.value) break
    }
    // raus: Ordner schließt, normales Ziehen übernimmt
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

private const val EDIT_MENU_GAP = 16f // pt unter dem Knopf [GEMESSEN] recording6 4,9 s (Menü oben ≈ 62,7 pt)
private const val LIB_PULL_TOP = 1f // px: Liste nur, wenn die Mediathek ganz oben steht

/** Mediathek-Fling: Abbremsen wie UIScrollView (M-SCROLL), an den Grenzen Zurückfedern (M-SCROLL-BOUNCE). */
private suspend fun flingLibrary(model: HomeModel, max: Float, velocity: Float) = flingScroll(model.libraryScroll, max, velocity)

/** Abbremsen wie UIScrollView (M-SCROLL), an den Grenzen Zurückfedern (M-SCROLL-BOUNCE). */
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

private const val OVERVIEW_HOME_HIDDEN = 0.35f // Fortschritt, ab dem der Homescreen ganz weg ist (Feder: ≈ 145 ms vor Ende, Duo ≈ 130 ms)
private const val OVERVIEW_HOME_FULL = 0.15f   // … und ab dem er voll da ist (≈ 205 ms, Duo ≈ 230 ms)

private const val DUO_ICON = 60f // pt, iPhone Duo [GEMESSEN; mit echten Displaykanten auch in Aufnahme 19:18 bestätigt]
private const val LAND_FROM = 1f          // hebt in Vorschau-Größe ab (Vorschau = Widget-Größe)
private const val LAND_OVER = 1.12f       // landet leicht vergrößert
private const val LAND_BLUR_DP = 10f
private const val LAND_LIFT_DP = 90f // Referenz: hebt sich ≈ 95 pt über die Vorschau
private const val LAND_FROM_Y = 0.55f

private const val SHOCK_AMP = 14f         // pt (≈ 11 pt bei direkten Nachbarn, ≈ 6 pt in 400 pt)
private const val SHOCK_RANGE = 450f      // pt Abklingweite
private const val SHOCK_SPEED = 900f      // pt/s
private const val SHOCK_DECAY = 7f
private const val SHOCK_FREQ = 2.2f       // Hz
private const val DUO_EDIT_MENU_W = 249f        // [GEMESSEN] 681,5 → 930,4 pt
private const val DUO_EDIT_MENU_RIGHT_OVER = 4.4f // rechte Kante 4 pt über die Knopfkante hinaus
private const val DUO_EDIT_MENU_GAP = -19f      // Oberkante 120 pt; Pinsel-Unterkante 139 pt → Menü überdeckt den Knopf leicht
private const val DUO_SEARCH_SUGGEST_EXTRA = 30f // Vorschläge 458 pt vs. Feld 428 pt [GEMESSEN]
private const val DUO_DOCK_CORNER = 22f
private const val FOLD_IN_MS = 1300         // gesamt: Aufhellen 0,85 s + Scharfwerden 0,45 s
private const val FOLD_SHARPEN_SHARE = 0.35f // Unschärfe hält, bis 35 % Rest übrig sind (= letzte 0,45 s)
private const val FOLD_BLUR_DP = 18f
private const val FOLD_DIM = 0.26f
private const val DUO_WPE_FADE_MS = 250
private const val DUO_STATUS_FROM_RIGHT = 47f
private const val DUO_FOLDED_SEARCH_GAP = 8f
private const val DUO_SEARCH_DIM = 0.40f // Dock-Spaltenmitte
private const val IOS_STATUS_H = 47f
private const val DUO_LIB_BLUR_FULL_AT = 0.3f
private const val DUO_DOCK_RIM = 0.62f
private const val DUO_SEARCH_DOCK_DELAY_MS = 1200L
private const val DUO_SEARCH_DOCK_FULL_AT = 0.5f
private const val DUO_SEARCH_DOCK_OUT_MS = 420
private const val DUO_SEARCH_DOCK_IN_MS = 320
private const val DUO_DOCK_OUT_MARGIN = 24f // pt über den Rand hinaus (Glas-Schatten)
private const val DUO_CAT_DOCK_SHIFT = 150f // pt bei offener Kategorie → ganz aus dem Bild
private const val DUO_LIB_PAGE_BLUR = 14f // pt Unschärfe der Homescreen-Seite neben der Mediathek [GESCHÄTZT]

/**
 * Widget-Flug aus dem Picker, Phasen über t (0…1 linear über 0,94 s) [GEMESSEN] Aufnahme 10:40, 47,56–48,50 s:
 *  Flug 0…0,40: unscharf, Position mit weichem An-/Abbremsen, Größe 1,0 → 1,16;
 *  Landung 0,40…1: scharf, Größe 1,16 → 0,87 (bei 0,68 = 48,25 s) → 1,0 (federnd, glatt interpoliert).
 */
private object WidgetFlight {
    private const val T = LANDING_MS / 1000f          // 1,0 s gesamt (Referenz 47,53 → 48,53 s)
    private const val LIFT_S = 0.25f                   // 47,56–47,78: hebt sich über dem Blatt, wächst auf 1,18
    private const val ARRIVE_S = LANDING_ARRIVE * T    // 47,94: wieder scharf, Aufprall
    private fun cd(w: Float, x: Float) = if (x <= 0f) 0f else 1f - (1f + w * x) * kotlin.math.exp(-w * x)
    /** Anheben 0…1 (nach oben, ease-out), steht danach still – die Schwebe-Pause der Referenz. */
    fun lift(t: Float) = inOut((t * T - 0.04f) / (LIFT_S - 0.06f))
    // Referenz: 47,56–47,65 kaum, 47,65–47,70 der Hauptteil, dann Schweben → ease-in-out statt ease-out
    private fun inOut(x: Float): Float { val k = x.coerceIn(0f, 1f); return if (k < 0.5f) 4f * k * k * k else 1f - (-2f * k + 2f).let { it * it * it } / 2f }
    /** Weg vom Schwebepunkt zum Platz: schneller Sprung (≈ 0,1 s) + weiches Nachgleiten (≈ 25 pt bis 48,5 s). */
    private val Z_END = 0.86f * cd(34f, T - LIFT_S) + 0.14f * cd(8f, T - LIFT_S)
    fun travel(t: Float): Float { val x = t * T - LIFT_S; return (0.86f * cd(34f, x) + 0.14f * cd(8f, x)) / Z_END }
    /** Größe: 1 → 1,18 beim Anheben, 1,16 bei Ankunft, dann gedämpfte Schwingung (0,93 @ 0,11 s, 0,87 @ 0,26 s, 1,0 @ 0,55 s). */
    fun scale(t: Float): Float {
        val s = t * T
        if (s < LIFT_S) return 1f + 0.18f * lift(t)
        if (s < ARRIVE_S) return 1.18f - 0.02f * ((s - LIFT_S) / (ARRIVE_S - LIFT_S))
        val x = s - ARRIVE_S
        val osc = kotlin.math.exp(-3.7f * x) * (0.16f * kotlin.math.cos(7.6f * x) - 0.30f * kotlin.math.sin(7.6f * x))
        val fade = 1f - ((s - (T - 0.15f)) / 0.15f).coerceIn(0f, 1f) // Rest sauber auf 1,0
        return 1f + osc * fade
    }
    /** Unschärfe: sofort an (Inhalt wird zum grauen Schemen), ab kurz vor Ankunft in 0,09 s weg. */
    fun blur(t: Float): Float { val s = t * T; return ((s / 0.03f).coerceIn(0f, 1f)) * (1f - ((s - (ARRIVE_S - 0.03f)) / 0.09f).coerceIn(0f, 1f)) }
    /** Schatten-Schemen, der am Abflugort zurückbleibt und mit dem Blatt verschwindet. */
    fun ghost(t: Float): Float { val s = t * T; return ((s / 0.06f).coerceIn(0f, 1f)) * (1f - ((s - LIFT_S) / 0.12f).coerceIn(0f, 1f)) }
}

/** Kontextmenü-Key für eine App aus der Mediathek (löst über [AppMap] auf dieselbe App auf, ist aber kein Homescreen-Icon). */
internal fun libraryMenuKey(appKey: String) = appKey + INSTANCE_SEP + "library"
