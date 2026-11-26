package io.github.janhurn9400.liquidy.ui.control

import androidx.compose.animation.core.animateFloatAsState
import android.content.Context
import android.content.Intent
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.layout.mandatorySystemGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import io.github.janhurn9400.liquidy.glass.stretch
import io.github.janhurn9400.liquidy.glass.drawGlassGlow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.janhurn9400.liquidy.R
import io.github.janhurn9400.liquidy.data.RootShell
import io.github.janhurn9400.liquidy.data.SystemControls
import io.github.janhurn9400.liquidy.design.CcLayout
import io.github.janhurn9400.liquidy.design.DesignTokens
import io.github.janhurn9400.liquidy.glass.drawObjectRim
import com.kyant.backdrop.backdrops.layerBackdrop
import io.github.janhurn9400.liquidy.glass.glassPull
import androidx.compose.foundation.verticalScroll
import io.github.janhurn9400.liquidy.motion.Motion
import io.github.janhurn9400.liquidy.ui.Inter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** Modules that open big on a long press. */
enum class CcExpand { Connectivity, Media, Volume, Brightness }

/**
 * Control Center state (M-CC). [progress] 0…1 follows the finger while pulling down from the top right and settles with
 * a spring; [over] is the rubber-banded pull beyond full (pt). [expand] 0…1 grows the [expanded] module into its big
 * panel (M-CC-EXPAND). Everything interruptible, release velocity passed on.
 */
class ControlCenterModel(private val scope: CoroutineScope) {
    val progress = Animatable(0f)
    val over = Animatable(0f)
    var open by mutableStateOf(false)
        private set
    val visible: Boolean get() = open || progress.value > 0.001f

    var expanded by mutableStateOf<CcExpand?>(null)
        private set
    val expand = Animatable(0f)
    /** Modules 0…1 (M-CC-REVEAL): not tied to the finger — they appear on their own once the pull passes a threshold. */
    val reveal = Animatable(0f)
    /** Duo: how far ring + modules hang below their place while the finger pulls (pt, M-CC.followDuo); 0 when let go. */
    val follow = Animatable(0f)
    /** Duo: status column 0…1 unfolding out of the ring (M-CC-UNFOLD), only past a short pull; overshoots. */
    val unfold = Animatable(0f)
    /** Duo: tile stretch (0 = own shape, > 0 taller and thinner, < 0 squashed) — M-CC.stretchDuo / M-CC-STRETCH. */
    val stretch = Animatable(0f)

    private fun stretchTo(pt: Float) { if (duo) scope.launch { stretch.snapTo(pt * Motion.ccStretchDuo.value) } }

    /** Let go: the tiles stretch further while flying back, overshoot into a short squash and settle. */
    private fun releaseStretch() {
        if (stretch.value == 0f && stretch.targetValue == 0f) return
        val v = stretch.value * Motion.ccStretchKick.value
        scope.launch { stretch.animateTo(0f, Motion.ccStretchBack.spec(), initialVelocity = v) }
    }

    private fun unfoldTo(on: Boolean) {
        val t = if (on) 1f else 0f
        if (unfold.targetValue == t) return
        scope.launch {
            if (on && unfold.value < Motion.ccUnfoldFromDuo.value) unfold.snapTo(Motion.ccUnfoldFromDuo.value)
            unfold.animateTo(t, if (on) Motion.ccUnfold.spec() else Motion.ccClose.spec())
        }
    }

    /** Ring offset (pt) for a pull of [pt]: rubber-banded (M-CC.followDuo / followRangeDuo). */
    private fun followFor(pt: Float): Float {
        val c = Motion.ccFollowDuo.value; val d = Motion.ccFollowRangeDuo.value
        return pt * d * c / (d + c * pt)
    }

    /** iPhone Duo style: the modules appear right away instead of past half the pull (M-CC Duo). */
    var duo = false
    private val revealAt get() = if (duo) Motion.ccRevealAtDuo.value else Motion.ccRevealAt.value

    private fun revealTo(on: Boolean, closing: Boolean = false) {
        val t = if (on) 1f else 0f
        if (reveal.targetValue != t) scope.launch { reveal.animateTo(t, if (closing) Motion.ccClose.spec() else Motion.ccReveal.spec()) }
    }

    fun pull(pt: Float, screenPt: Float) {
        val full = Motion.ccPull.value
        val p = (pt / full).coerceIn(0f, 1f)
        val extra = if (pt > full) Motion.rubberBand(pt - full, screenPt) else 0f
        scope.launch { progress.snapTo(p) }
        scope.launch { over.snapTo(extra) }
        if (duo) {
            val f = followFor(pt)
            scope.launch { follow.snapTo(f) }
            stretchTo(f * Motion.ccFollowModulesDuo.value)
            unfoldTo(pt >= Motion.ccUnfoldAtDuo.value)
        }
        revealTo(p >= revealAt)
    }

    /** Let go: ring and modules spring back to their place, carrying the finger's speed. */
    private fun releaseFollow(velocity: Float) {
        releaseStretch()
        if (follow.value == 0f && follow.targetValue == 0f) return
        scope.launch { follow.animateTo(0f, Motion.ccFollowBack.spec(), initialVelocity = velocity * Motion.ccFollowDuo.value) }
    }

    /** [velocity] in pt/s, positive = down. */
    fun release(velocity: Float) {
        val opens = velocity > Motion.ccFlick.value || (velocity > -Motion.ccFlick.value && progress.value >= Motion.ccCommit.value)
        if (opens) show(velocity) else hide(velocity)
    }

    fun show(velocity: Float = 0f) {
        open = true
        revealTo(true)
        if (duo) unfoldTo(true)
        io.github.janhurn9400.liquidy.data.PerfBoost.kick()
        scope.launch { progress.animateTo(1f, Motion.cc.spec(), initialVelocity = velocity / Motion.ccPull.value) }
        scope.launch { over.animateTo(0f, Motion.cc.spec()) }
        releaseFollow(velocity)
    }

    fun hide(velocity: Float = 0f) {
        open = false
        revealTo(false, closing = true)
        unfoldTo(false)
        if (editing || gallery) { editing = false; gallery = false; scope.launch { edit.snapTo(0f) }; scope.launch { galleryAmount.snapTo(0f) } }
        scope.launch {
            // closing is much quicker than opening (M-CC close)
            progress.animateTo(0f, Motion.ccClose.spec(), initialVelocity = velocity / Motion.ccPull.value)
            if (!open) { expanded = null; expand.snapTo(0f); page.snapTo(0f) } // closed → next time compact, first page
        }
        scope.launch { over.animateTo(0f, Motion.cc.spec()) }
        releaseFollow(velocity)
    }

    /** Back/home: the gallery closes first, then edit mode, a big module, then the Control Center. */
    fun back() = when {
        gallery -> closeGallery()
        editing -> exitEdit()
        expanded != null && expand.targetValue == 1f -> collapse()
        else -> hide()
    }

    // ── edit mode ("+", M-CC-EDIT) and the "Add a Control" gallery (M-CC-GALLERY)
    private var layoutStore: io.github.janhurn9400.liquidy.data.CcLayoutStore? = null
    fun store(context: Context) = layoutStore ?: io.github.janhurn9400.liquidy.data.CcLayoutStore(context).also { layoutStore = it }

    var editing by mutableStateOf(false)
        private set
    val edit = Animatable(0f)
    var gallery by mutableStateOf(false)
        private set

    /** Live resize while a module's handle is dragged: its size in pt, following the finger (M-CC-RESIZE). */
    class LiveResize(val id: String, val w: Float, val h: Float)
    var resizing by mutableStateOf<LiveResize?>(null)

    /** Pages: what doesn't fit flows onto the next page; [page] 0… scrolls vertically (M-CC-PAGE). */
    val page = Animatable(0f)
    var pageCount by mutableStateOf(1)
    /** Grid rows per page (set by the compact page) — to find the page an item lands on. */
    var rowsPerPage = 6

    fun pageTo(i: Int, velocity: Float = 0f) {
        scope.launch { page.animateTo(i.coerceIn(0, pageCount - 1).toFloat(), Motion.ccPage.spec(), velocity) }
    }
    val galleryAmount = Animatable(0f)

    fun enterEdit() {
        if (editing) return
        if (expanded != null) collapse()
        editing = true
        scope.launch { edit.animateTo(1f, Motion.ccEdit.spec()) }
    }

    fun exitEdit() {
        if (!editing) return
        editing = false
        closeGallery()
        scope.launch { edit.animateTo(0f, Motion.ccEdit.spec()) }
    }

    fun openGallery() { gallery = true; scope.launch { galleryAmount.animateTo(1f, Motion.ccGallery.spec()) } }

    fun closeGallery() {
        if (!gallery) return
        gallery = false
        scope.launch { galleryAmount.animateTo(0f, Motion.ccGallery.spec()) }
    }

    fun expandModule(m: CcExpand) {
        if (expanded == m && expand.targetValue == 1f) return
        expanded = m
        io.github.janhurn9400.liquidy.data.PerfBoost.kick()
        scope.launch { expand.animateTo(1f, Motion.ccExpand.spec()) }
    }

    fun collapse() {
        if (expanded == null) return
        scope.launch {
            expand.animateTo(0f, Motion.ccExpand.spec())
            if (expand.targetValue == 0f) expanded = null
        }
    }

    /** Dragging the open Control Center up (negative [dyPt]) closes it along with the finger. */
    fun dragOpen(dyPt: Float, screenPt: Float) {
        if (dyPt >= 0f) {
            val o = Motion.rubberBand(dyPt, screenPt)
            scope.launch { progress.snapTo(1f) }; scope.launch { over.snapTo(o) }
            stretchTo(o) // Duo: pulling the open Control Center stretches the tiles again
        }
        else {
            val p = (1f + dyPt / Motion.ccPull.value).coerceIn(0f, 1f)
            scope.launch { progress.snapTo(p) }; scope.launch { over.snapTo(0f) }
            stretchTo(0f)
            if (duo) unfoldTo(p * Motion.ccPull.value >= Motion.ccUnfoldAtDuo.value)
            revealTo(p >= revealAt)
        }
    }
}

/** Layers of the Duo progressive blur (more = smoother ramp, each one costs a full-screen blur). */
private const val DUO_BLUR_STEPS = 4

/** Rectangle in pt. */
private data class PtRect(val x: Float, val y: Float, val w: Float, val h: Float) {
    fun lerp(o: PtRect, t: Float) = PtRect(x + (o.x - x) * t, y + (o.y - y) * t, w + (o.w - w) * t, h + (o.h - h) * t)
}

/**
 * Grid geometry of the compact page (T-CC): phone = centred below the status row; iPhone Duo unfolded = the right page
 * only, folded = left-aligned next to the status column (CcLayout DUO_*). Expanded panels centre on the grid and use
 * tighter Duo metrics so they fit its 669 pt height.
 */
private class CcGrid(screenWpt: Float, val duo: Boolean, unfolded: Boolean) {
    val cell = DesignTokens.ccCell.value
    // Duo: tighter grid than the phone (84 pt pitch both ways) — leaves the original's room below for "Add a Control"
    val gx = if (duo) CcLayout.DUO_GAP else DesignTokens.ccGapX.value
    val gy = if (duo) CcLayout.DUO_GAP else DesignTokens.ccGapY.value
    val width = 4 * cell + 3 * gx
    val left = when { !duo -> (screenWpt - width) / 2; unfolded -> screenWpt - CcLayout.DUO_OPEN_GRID_FROM_RIGHT; else -> CcLayout.DUO_FOLDED_GRID_LEFT }
    val top = when { !duo -> DesignTokens.ccTop.value; unfolded -> CcLayout.DUO_OPEN_TOP; else -> CcLayout.DUO_FOLDED_TOP }
    val centerX get() = left + width / 2
    /** Rows per page and the height of one page (pt). */
    val rows get() = CcLayout.EDIT_ROWS
    val pageH get() = rows * (cell + gy)
    val buttonY get() = if (duo) CcLayout.DUO_BUTTON_Y else DesignTokens.ccButtonY.value
    val expTop get() = if (duo) CcLayout.DUO_EXP_TOP else CcLayout.EXP_TOP
    val expGap get() = if (duo) CcLayout.DUO_EXP_GAP else CcLayout.EXP_GAP
    val expTile get() = if (duo) CcLayout.DUO_EXP_TILE else CcLayout.EXP_TILE
    val bigGlyphY get() = if (duo) CcLayout.DUO_BIG_GLYPH_Y else CcLayout.BIG_GLYPH_Y
    val bigSliderY get() = if (duo) CcLayout.DUO_BIG_SLIDER_Y else CcLayout.BIG_SLIDER_Y
    val bigButtonY get() = if (duo) CcLayout.DUO_BIG_BUTTON_Y else CcLayout.BIG_BUTTON_Y
    fun slot(col: Int, row: Int, cols: Int = 1, rows: Int = 1) =
        PtRect(left + col * (cell + gx), top + row * (cell + gy), cols * cell + (cols - 1) * gx, rows * cell + (rows - 1) * gy)
    /** Where each module sits right now (filled by [Modules]; the big panels grow out of these). */
    val placed = HashMap<String, PtRect>()
    val connectivity get() = placed["connectivity"] ?: slot(0, 0, 2, 2)
    val media get() = placed["media"] ?: slot(2, 0, 2, 2)
    val volume get() = placed["volume"] ?: slot(2, 2, 1, 2)
    val brightness get() = placed["brightness"] ?: slot(3, 2, 1, 2)
    fun from(m: CcExpand) = when (m) { CcExpand.Connectivity -> connectivity; CcExpand.Media -> media; CcExpand.Volume -> volume; CcExpand.Brightness -> brightness }
    fun to(m: CcExpand): PtRect = when (m) {
        CcExpand.Connectivity -> PtRect(left, expTop, width, 4 * CcLayout.EXP_ROW + 2 * expTile + 5 * expGap)
        CcExpand.Media -> PtRect(centerX - CcLayout.MEDIA_W / 2, if (duo) (CcLayout.DUO_EXP_TOP + 15f) else CcLayout.MEDIA_Y, CcLayout.MEDIA_W, CcLayout.MEDIA_H)
        CcExpand.Volume, CcExpand.Brightness -> PtRect(centerX - CcLayout.BIG_SLIDER_W / 2, bigSliderY, CcLayout.BIG_SLIDER_W, CcLayout.BIG_SLIDER_H)
    }
}

/** Places a composable at a rect in pt (relayout per frame only for the morphing module). */
private fun Modifier.at(density: Float, r: () -> PtRect) = this
    .offset { val q = r(); IntOffset((q.x * density).roundToInt(), (q.y * density).roundToInt()) }
    .layout { m, _ ->
        val q = r(); val w = (q.w * density).roundToInt().coerceAtLeast(1); val h = (q.h * density).roundToInt().coerceAtLeast(1)
        val p = m.measure(Constraints.fixed(w, h)); layout(w, h) { p.place(0, 0) }
    }

private fun Modifier.at(r: PtRect) = this
    .offset(r.x.dp, r.y.dp)
    .requiredSize(r.w.dp, r.h.dp)

/** Full-screen Control Center over the (blurred) home screen. */
@Composable
fun ControlCenter(model: ControlCenterModel, density: Float, screenW: Float, screenH: Float,
                  /** iPhone Duo style (foldable), [unfolded] = two pages; [statusColumnX] = centre of the right column (px). */
                  duo: Boolean = false, unfolded: Boolean = false, statusColumnX: Float = 0f,
                  /** Duo unfolded: draws wallpaper + home content — only the right page gets blurred behind the panel. */
                  drawBackdrop: (DrawScope.() -> Unit)? = null,
                  /** Colour of the status column where the pull starts (black over the light settings); turns white as it opens. */
                  statusBase: Color = Color.White) {
    model.duo = duo
    if (!model.visible) return
    val context = LocalContext.current
    // keep the shown state fresh while open (cheap reads); root is asked for once, the first time it opens
    // first values already while pulling (the status row shows during the pull, before it opens)
    LaunchedEffect(Unit) { SystemControls.refresh(context) }
    LaunchedEffect(model.open) {
        if (!model.open) return@LaunchedEffect
        // rights for the switches: root (Magisk asks once), else Shizuku if it's running and allowed
        io.github.janhurn9400.liquidy.data.ShizukuShell.refresh()
        RootShell.probe { SystemControls.grantPrivileged(context) }
        while (true) { SystemControls.refresh(context); delay(REFRESH_MS) }
    }
    val p = { model.progress.value }
    val e = { model.expand.value }
    val screenPt = screenH / density
    val wPt = screenW / density
    val grid = CcGrid(wPt, duo, unfolded)
    // Duo unfolded: only the right page (right of the hinge) is blurred; the left page just dims [MEASURED simulator]
    // Progressive blur from the hinge rightwards (T-CC.duoBlurFeather): a crossfade from sharp to blurred showed both images
    // half on top of each other — a visible seam. Instead several layers with growing blur radius, each faded in over
    // its own slice of the ramp; every layer blurs the whole backdrop, so pixels near the hinge blur with real neighbours.
    if (duo && unfolded && drawBackdrop != null) Box(Modifier.fillMaxSize().graphicsLayer {
        alpha = p().coerceIn(0f, 1f); compositingStrategy = CompositingStrategy.Offscreen
    }) {
        for (i in 1..DUO_BLUR_STEPS) Box(Modifier.fillMaxSize()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                val w = DesignTokens.ccDuoBlurFeather.value * density
                val start = size.width / 2 // ramp begins at the hinge
                val x0 = start + w * (i - 1) / DUO_BLUR_STEPS; val x1 = start + w * i / DUO_BLUR_STEPS
                drawRect(Brush.horizontalGradient(0f to Color.Transparent, 1f to Color.Black, startX = x0, endX = x1),
                    blendMode = BlendMode.DstIn)
            }) {
            Box(Modifier.fillMaxSize().graphicsLayer {
                val b = DesignTokens.ccBlur.value * density * i / DUO_BLUR_STEPS
                renderEffect = BlurEffect(b, b, TileMode.Clamp)
            }) { Canvas(Modifier.fillMaxSize()) { drawBackdrop() } }
        }
    }
    val gridNow by rememberUpdatedState(grid)
    val pageScope = rememberCoroutineScope()
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = p() }
            .background(Color.Black.copy(alpha = DesignTokens.ccDim.value))
            // empty area: tap closes (or shrinks a big module), a drag follows the finger (up closes)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    var moved = false
                    val tracker = androidx.compose.ui.input.pointer.util.VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    // more than one page: a vertical swipe inside the grid turns pages (rubber band at the ends)
                    val g = gridNow
                    val inGrid = model.pageCount > 1 && model.expanded == null &&
                        down.position.x / density in g.left..(g.left + g.width) && down.position.y / density in g.top..(g.top + g.pageH)
                    if (inGrid) {
                        val start = model.page.value
                        var paging = false
                        while (true) {
                            val ev = awaitPointerEvent()
                            val c = ev.changes.firstOrNull { it.id == down.id } ?: break
                            tracker.addPosition(c.uptimeMillis, c.position)
                            val dy = c.position.y - down.position.y
                            if (!paging && abs(dy) > viewConfiguration.touchSlop) paging = true
                            if (paging) {
                                c.consume()
                                val raw = start - dy / (g.pageH * density)
                                val last = (model.pageCount - 1).toFloat()
                                val v = when {
                                    raw < 0f -> -Motion.rubberBand(-raw * g.pageH, g.pageH) / g.pageH
                                    raw > last -> last + Motion.rubberBand((raw - last) * g.pageH, g.pageH) / g.pageH
                                    else -> raw
                                }
                                pageScope.launch { model.page.snapTo(v) }
                            }
                            if (!c.pressed) break
                        }
                        if (paging) {
                            val vy = tracker.calculateVelocity().y / density // pt/s, positive = down = previous page
                            val pv = -vy / g.pageH
                            val target = when {
                                pv > Motion.ccPageFlick.value -> kotlin.math.floor(model.page.value).toInt() + 1
                                pv < -Motion.ccPageFlick.value -> kotlin.math.ceil(model.page.value).toInt() - 1
                                else -> model.page.value.roundToInt()
                            }
                            model.pageTo(target, pv)
                            return@awaitEachGesture
                        }
                        when { model.editing -> model.exitEdit(); else -> model.hide() }
                        return@awaitEachGesture
                    }
                    while (true) {
                        val ev = awaitPointerEvent()
                        val c = ev.changes.firstOrNull { it.id == down.id } ?: break
                        tracker.addPosition(c.uptimeMillis, c.position)
                        val dy = c.position.y - down.position.y
                        if (!moved && model.expanded == null && !model.editing && abs(dy) > viewConfiguration.touchSlop) moved = true
                        if (moved) { model.dragOpen(dy / density, screenPt); c.consume() }
                        if (!c.pressed) break
                    }
                    // edit mode: tapping an empty spot ends it (like the original)
                    if (!moved) { when { model.editing -> model.exitEdit(); model.expanded != null -> model.collapse(); else -> model.hide() } }
                    else {
                        val v = tracker.calculateVelocity().y / density
                        if (v < -Motion.ccFlick.value || model.progress.value < 1f - Motion.ccCommit.value) model.hide(v) else model.show(v)
                    }
                }
            }
    )
    // the finger drives the background and the chrome (status row, + / power) …
    Box(Modifier.fillMaxSize().graphicsLayer {
        alpha = p() * (1f - e().coerceIn(0f, 1f)) * (1f - model.edit.value.coerceIn(0f, 1f))
        translationY = model.over.value * density
    }) { Chrome(grid, model) }
    // … the modules appear on their own clock (M-CC-REVEAL): fade in, sharpen, grow slightly and slide down
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                val r = model.reveal.value
                alpha = r.coerceIn(0f, 1f)
                val sc = Motion.ccEnterScale.value + (1f - Motion.ccEnterScale.value) * r
                // Duo: grows about the grid's own centre, so the tiles don't drift sideways [MEASURED simulator]
                val ox = if (duo) (grid.left + grid.width / 2) * density / size.width else 0.5f
                transformOrigin = TransformOrigin(ox, grid.top * density / size.height)
                // Duo: the tiles hang below with the finger and stretch, taller and thinner (M-CC Duo)
                val fol = model.follow.value * Motion.ccFollowModulesDuo.value
                val st = (1f + model.stretch.value).coerceAtLeast(0.5f)
                scaleX = sc / kotlin.math.sqrt(st); scaleY = sc * st
                translationY = (-(1f - r) * (if (duo) Motion.ccEnterYDuo.value else Motion.ccEnterY.value) + model.over.value + fol -
                    model.edit.value * Motion.ccEditLift.value - model.page.value * grid.pageH) * density
                val b = (1f - r).coerceIn(0f, 1f) * Motion.ccEnterBlur.value * density
                renderEffect = if (b > 0.5f) BlurEffect(b, b, TileMode.Decal) else null
            }
    ) {
        // compact modules: fade and blur away while one is big (M-CC-EXPAND)
        Box(Modifier.fillMaxSize().graphicsLayer {
            val x = e().coerceIn(0f, 1f)
            alpha = 1f - x
            val b = x * Motion.ccExpandBlur.value * density
            renderEffect = if (b > 0.5f) BlurEffect(b, b, TileMode.Decal) else null
        }) {
            EditSlots(context, model, grid)
            Modules(context, model, grid, density)
        }
        model.expanded?.let { m -> Expanded(context, model, m, grid, density) }
    }
    if (model.pageCount > 1) PageDots(model, grid, density) { model.reveal.value * (1f - e().coerceIn(0f, 1f)) }
    // Duo: the status column below the camera turns into the Control Center's (battery ring, Wi-Fi, carrier) and back
    if (duo) {
        val info by io.github.janhurn9400.liquidy.data.rememberStatusInfo()
        Box(Modifier.fillMaxSize().graphicsLayer {
            alpha = (1f - e().coerceIn(0f, 1f)) * (1f - model.edit.value.coerceIn(0f, 1f))
            translationY = (model.over.value + model.follow.value) * density
        }) {
            // the same symbols that stood there before (black over light settings) turn white as the panel comes down
            val statusColor = androidx.compose.ui.graphics.lerp(statusBase, Color.White, model.progress.value.coerceIn(0f, 1f))
            io.github.janhurn9400.liquidy.ui.home.DuoStatusSymbols(info, statusColumnX, density, statusColor,
                cc = { model.unfold.value }, ccOpening = { model.unfold.targetValue == 1f }, carrier = SystemControls.carrier.value.ifEmpty { "—" })
        }
    }
    if (model.editing || model.edit.value > 0.001f) AddControlButton(model, grid, screenPt)
    if (model.gallery || model.galleryAmount.value > 0.001f) Gallery(context, model, wPt, screenPt, density)
}

// ── compact page ─────────────────────────────────────────────────────────────────────

/** + and power at the top, status row — they follow the finger with the background. */
@Composable
private fun Chrome(g: CcGrid, model: ControlCenterModel) {
    val context = LocalContext.current
    val bs = DesignTokens.ccButton.value
    val by = g.buttonY - bs / 2
    val fill = Color.White.copy(alpha = DesignTokens.ccFill.value)
    Glyph(Modifier.at(PtRect(g.left - CcLayout.BUTTON_OUT, by, bs, bs)).ccGlassPress(CircleShape, fill, onTap = { model.enterEdit() }), Icons.plus, Color.White, 0.55f)
    // power: Android's power menu (global actions)
    Glyph(Modifier.at(PtRect(g.left + g.width - bs + CcLayout.BUTTON_OUT, by, bs, bs)).ccGlassPress(CircleShape, fill, onTap = { io.github.janhurn9400.liquidy.data.PowerMenu.show(context) }), Icons.power, Color.White, 0.6f)
    if (!g.duo) StatusRow(Modifier.at(PtRect(g.left + 4f, DesignTokens.ccStatusY.value - 10f, g.width - 8f, 20f)))
}

@Composable
private fun Modules(context: Context, model: ControlCenterModel, g: CcGrid, density: Float) {
    val store = model.store(context)
    val items = store.items.toList()
    val pos = io.github.janhurn9400.liquidy.data.CcLayoutStore.pack(items, g.rows)
    val pages = io.github.janhurn9400.liquidy.data.CcLayoutStore.pages(items, g.rows)
    androidx.compose.runtime.SideEffect { model.pageCount = pages; model.rowsPerPage = g.rows }
    for (it in items) {
        val (col, row) = pos[it.id] ?: continue
        val target = g.slot(col, row, it.w, it.h)
        g.placed[it.id] = target
        val onPage = (row / g.rows).toFloat()
        key(it.id) {
            // reflow: modules glide to their new spot / size (M-CC-REFLOW); while its handle is dragged the size
            // follows the finger instead and springs to the chosen size on release (M-CC-RESIZE)
            val anim = remember { RectAnim(target) }
            val live = model.resizing?.takeIf { r -> r.id == it.id }
            LaunchedEffect(target, live == null) { anim.to(target, size = live == null) }
            if (live != null) LaunchedEffect(live) { anim.w.snapTo(live.w); anim.h.snapTo(live.h) }
            Box(Modifier.at(density) { anim.value() }.graphicsLayer {
                // modules of other pages fade with the distance to the shown page
                alpha = (1f - abs(onPage - model.page.value)).coerceIn(0f, 1f)
            }) {
                // glass and glyph stay one element while resizing; only the text changes with a blur (ControlModule)
                Module(context, model, it.id, it.w, it.h, g)
                EditOverlay(model, store, it, g, density)
            }
        }
    }
}

/** Animated rect (pt) for a module moving or resizing. */
private class RectAnim(r: PtRect) {
    val x = Animatable(r.x); val y = Animatable(r.y); val w = Animatable(r.w); val h = Animatable(r.h)
    fun value() = PtRect(x.value, y.value, w.value, h.value)
    suspend fun to(r: PtRect, size: Boolean = true) = kotlinx.coroutines.coroutineScope {
        launch { x.animateTo(r.x, Motion.ccReflow.spec()) }; launch { y.animateTo(r.y, Motion.ccReflow.spec()) }
        if (size) { launch { w.animateTo(r.w, Motion.ccReflow.spec()) }; launch { h.animateTo(r.h, Motion.ccReflow.spec()) } }
    }
}

/** One module's content, filling its box. */
@Composable
private fun Module(context: Context, model: ControlCenterModel, id: String, w: Int, h: Int, g: CcGrid) {
    val c = SystemControls
    val view = LocalView.current
    fun big(m: CcExpand) { view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS); model.expandModule(m) }
    fun vis(m: CcExpand) = if (model.expanded == m) Modifier.graphicsLayer { alpha = 0f } else Modifier
    val full = Modifier.fillMaxSize()
    when (id) {
        "connectivity" -> Box(full.then(vis(CcExpand.Connectivity))) { ConnectivityCompact(context) { big(CcExpand.Connectivity) } }
        "media" -> Box(full.then(vis(CcExpand.Media)).ccGlassPress(RoundedCornerShape(DesignTokens.ccCorner.dp), onTap = {}, onLong = { big(CcExpand.Media) })) { MediaCompact() }
        "volume" -> Slider(full.then(vis(CcExpand.Volume)), c.volume.floatValue, Icons.speaker, BLUE,
            onLong = { big(CcExpand.Volume) }) { v -> c.setVolume(context, v) }
        "brightness" -> Slider(full.then(vis(CcExpand.Brightness)), c.brightness.floatValue, Icons.sun, YELLOW,
            onLong = { big(CcExpand.Brightness) }) { v -> c.setBrightness(context, v) }
        "focus" -> if (w == 2 && h == 1) FocusModule(full, c.dnd.value) { c.toggleDnd(context) }
            else ControlModule(full, g, CcIcon.Vector(Icons.moon), stringResource(R.string.cc_focus), c.dnd.value, PURPLE) { c.toggleDnd(context) }
        else -> {
            val b = builtIn(id)
            if (b != null) ControlModule(full, g, CcIcon.Vector(b.icon()), stringResource(b.label), b.on(), b.active) { b.tap(context) }
            else {
                val t = remember(id) { io.github.janhurn9400.liquidy.data.QsTiles.find(context, id) }
                if (t != null) ControlModule(full, g, CcIcon.Bitmap(t.icon), t.label, false, Color.Black) {
                    if (!io.github.janhurn9400.liquidy.data.QsTiles.click(t))
                        android.widget.Toast.makeText(context, R.string.cc_no_root, android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

/** A built-in on/off or launcher control. */
private class BuiltIn(val label: Int, val icon: () -> Path, val on: () -> Boolean, val active: Color, val tap: (Context) -> Unit)

private fun builtIn(id: String): BuiltIn? {
    val c = SystemControls
    return when (id) {
        "rotation" -> BuiltIn(R.string.cc_rotation_lock, { Icons.rotationLock }, { c.rotationLock.value }, RED) { c.toggleRotationLock(it) }
        "silent" -> BuiltIn(R.string.cc_silent, { if (c.silent.value) Icons.bellOff else Icons.bell }, { c.silent.value }, RED) { c.toggleSilent(it) }
        "torch" -> BuiltIn(R.string.cc_torch, { Icons.torch }, { c.torch.value }, BLUE) { c.toggleTorch(it) }
        "mirror" -> BuiltIn(R.string.cc_mirror, { Icons.mirror }, { false }, Color.Black) { launch(it, Intent(android.provider.Settings.ACTION_CAST_SETTINGS)) }
        "darkmode" -> BuiltIn(R.string.cc_dark_mode, { Icons.contrast }, { c.darkMode.value }, Color.Black) { c.toggleDarkMode(it) }
        "camera" -> BuiltIn(R.string.cc_camera, { Icons.camera }, { false }, Color.Black) { launch(it, Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)) }
        "saver" -> BuiltIn(R.string.cc_battery_saver, { Icons.battery }, { c.batterySaver.value }, ORANGE) { c.toggleBatterySaver(it) }
        "timer" -> BuiltIn(R.string.cc_timer, { Icons.timer }, { false }, Color.Black) { launch(it, Intent(android.provider.AlarmClock.ACTION_SHOW_TIMERS)) }
        "calculator" -> BuiltIn(R.string.cc_calculator, { Icons.calculator }, { false }, Color.Black) {
            launch(it, Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALCULATOR)) }
        "alarm" -> BuiltIn(R.string.cc_alarm, { Icons.alarm }, { false }, Color.Black) { launch(it, Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS)) }
        else -> null
    }
}

/** Label and glyph of any control for the gallery (built-ins only; Android tiles carry their own). */
private fun builtInGlyph(id: String): Pair<Int, Path>? = when (id) {
    "connectivity" -> R.string.cc_connectivity to Icons.wifi
    "media" -> R.string.cc_media to Icons.play
    "volume" -> R.string.cc_volume to Icons.speaker
    "brightness" -> R.string.cc_brightness to Icons.sun
    "focus" -> R.string.cc_focus to Icons.moon
    else -> builtIn(id)?.let { it.label to it.icon() }
}

private sealed interface CcIcon {
    data class Vector(val path: Path) : CcIcon
    data class Bitmap(val image: androidx.compose.ui.graphics.ImageBitmap?) : CcIcon
}

@Composable
private fun CcGlyph(m: Modifier, icon: CcIcon, color: Color, share: Float) = when (icon) {
    is CcIcon.Vector -> Glyph(m, icon.path, color, share)
    is CcIcon.Bitmap -> Box(m, contentAlignment = Alignment.Center) {
        icon.image?.let {
            Image(it, null, Modifier.fillMaxSize(share), contentScale = ContentScale.Fit,
                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(color))
        }
    }
}

private enum class CcKind(val cols: Int, val rows: Int) { Round(1, 1), Wide(2, 1), Tall(1, 2), Large(2, 2) }

/**
 * A control in any of its sizes: round = glass circle with the glyph; wide = capsule, glyph + name; tall =
 * capsule, glyph on top, name below; large = rounded square, glyph top left, name and state bottom left.
 * Everything follows the module's actual (live) size, like the original while its handle is dragged (M-CC-RESIZE,
 * user's iPhone recording 4.65–5.05 s): the glyph never moves or scales — it stays centred in the top-left cell, where
 * the round 1×1 has it, and the tile grows around it; the layout is the nearest size's, the corners morph continuously
 * from circle/capsule to the rounded square, and the text is blurred the further the module is from a valid size —
 * sharp on a size, most blurred halfway, where the text layout swaps unseen.
 */
@Composable
private fun ControlModule(m: Modifier, g: CcGrid, icon: CcIcon, label: String, on: Boolean, active: Color, tap: () -> Unit) {
    val fill = if (on) Color.White else Color.White.copy(alpha = DesignTokens.ccFill.value)
    val glyphColor = if (on) active else Color.White
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    var boxW by remember { mutableStateOf(g.cell) }
    var boxH by remember { mutableStateOf(g.cell) }
    fun span(n: Int, gap: Float) = n * g.cell + (n - 1) * gap
    // nearest valid size and how far off it the live size is (0 = on it, 1 = halfway to the next)
    val pitch = g.cell + g.gx
    // past the smallest / largest size (the small glass-like overshoot) counts as on that size: text stays sharp
    val cw = boxW.coerceIn(g.cell, span(2, g.gx)); val chh = boxH.coerceIn(g.cell, span(2, g.gy))
    val kind = CcKind.entries.minBy { abs(span(it.cols, g.gx) - cw) + abs(span(it.rows, g.gy) - chh) }
    val off = ((abs(span(kind.cols, g.gx) - cw) + abs(span(kind.rows, g.gy) - chh)) / (pitch / 2)).coerceIn(0f, 1f)
    // corners: radius = half the short side (circle / capsule) blending into the large tile's corner
    val large = ((minOf(boxW, boxH) - g.cell) / (span(2, g.gx) - g.cell)).coerceIn(0f, 1f)
    val radius = minOf(boxW, boxH) / 2 * (1f - large) + DesignTokens.ccCorner.value * large
    // on/off is the whole tile, at every size (no separate circle around the glyph — like the original)
    Box(m.layout { meas, c ->
        boxW = c.maxWidth / density; boxH = c.maxHeight / density
        val p = meas.measure(c); layout(p.width, p.height) { p.place(0, 0) }
    }.ccGlassPress(RoundedCornerShape(radius.dp), fill, onTap = tap)) {
        // the glyph: same size and place as on the 1×1 tile, whatever the size
        // requiredSize: never squeezed with the tile when it's pushed below 1×1
        CcGlyph(Modifier.requiredSize(g.cell.dp), icon, glyphColor, CcLayout.MODULE_GLYPH_SHARE)
        // the text: blurred (and a little faded) by the distance to the nearest size
        Box(Modifier.fillMaxSize().graphicsLayer {
            val b = off * Motion.ccResizeBlur.value * density
            renderEffect = if (b > 0.5f) BlurEffect(b, b, TileMode.Decal) else null
            alpha = 1f - off * Motion.ccResizeFadeOut.value
        }) {
            // dark text on the white "on" tile
            val name = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, color = if (on) Color.Black else Color.White)
            // the text is set for the nearest size's width, not the live one — it never re-wraps while the tile grows
            // (it may run past the edge for a moment, blurred and clipped, like the original)
            val wideW = span(2, g.gx) - g.cell - 2f - 12f
            val tallW = g.cell - 12f
            val largeW = span(2, g.gx) - 16f - 10f
            when (kind) {
                CcKind.Round -> Unit
                CcKind.Wide -> BasicText(label, Modifier.align(Alignment.CenterStart).padding(start = (g.cell + 2f).dp)
                    .wrapContentWidth(Alignment.Start, unbounded = true).width(wideW.dp),
                    maxLines = 2, style = name.copy(fontSize = 14.sp, lineHeight = 16.sp))
                CcKind.Tall -> BasicText(label, Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)
                    .wrapContentWidth(Alignment.CenterHorizontally, unbounded = true).width(tallW.dp), maxLines = 2,
                    style = name.copy(fontSize = 12.sp, lineHeight = 14.sp, textAlign = TextAlign.Center))
                CcKind.Large -> Column(Modifier.align(Alignment.BottomStart).padding(start = 16.dp, bottom = 14.dp)
                    .wrapContentWidth(Alignment.Start, unbounded = true).width(largeW.dp)) {
                    BasicText(label, maxLines = 2, style = name.copy(fontSize = 15.sp))
                    BasicText(stringResource(if (on) R.string.cc_on else R.string.cc_off),
                        style = TextStyle(fontFamily = Inter, fontSize = 13.sp, color = (if (on) Color.Black else Color.White).copy(alpha = 0.6f)))
                }
            }
        }
    }
}

// ── edit mode ─────────────────────────────────────────────────────────────────────

/** Faint circles on every grid cell while editing (where controls can go) [MEASURED simulator]. */
@Composable
private fun EditSlots(context: Context, model: ControlCenterModel, g: CcGrid) {
    val e = model.edit.value.coerceIn(0f, 1f)
    if (e <= 0.001f) return
    // only free cells (the glass modules would show the circles through)
    val items = model.store(context).items.toList()
    val pos = io.github.janhurn9400.liquidy.data.CcLayoutStore.pack(items, g.rows)
    val taken = HashSet<Int>()
    for (it in items) pos[it.id]?.let { (c0, r0) -> for (dx in 0 until it.w) for (dy in 0 until it.h) taken.add((r0 + dy) * 16 + c0 + dx) }
    Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = e }) {
        val rad = g.cell / 2 * density
        for (r in 0 until g.rows * model.pageCount) for (c in 0 until io.github.janhurn9400.liquidy.data.CcLayoutStore.COLS) {
            if (r * 16 + c in taken) continue
            val q = g.slot(c, r)
            val a = (1f - abs((r / g.rows) - model.page.value)).coerceIn(0f, 1f)
            if (a > 0.01f) drawCircle(Color.White.copy(alpha = CcLayout.EDIT_SLOT_ALPHA * a), rad, Offset((q.x + q.w / 2) * density, (q.y + q.h / 2) * density))
        }
    }
}

/**
 * Per module while editing: "−" badge top left (removes it) and the resize handle bottom right (drag to switch
 * between the sizes the control allows); the module itself takes no taps then.
 */
@Composable
private fun EditOverlay(model: ControlCenterModel, store: io.github.janhurn9400.liquidy.data.CcLayoutStore,
                        item: io.github.janhurn9400.liquidy.data.CcLayoutStore.Item, g: CcGrid, density: Float) {
    val e = model.edit.value.coerceIn(0f, 1f)
    if (e <= 0.001f) return
    val sizes = io.github.janhurn9400.liquidy.data.CcLayoutStore.sizesFor(item.id)
    val current by rememberUpdatedState(item)
    // while another module is resized its badge and handle fade out (only the resized one keeps them) [user's iPhone 11-26]
    val others by animateFloatAsState(if (model.resizing != null && model.resizing?.id != item.id) 0f else 1f, label = "editFade")
    Box(Modifier.fillMaxSize().graphicsLayer { alpha = e * others }.pointerInput(Unit) {
        // swallow taps on the module itself while editing
        awaitEachGesture { awaitFirstDown().consume(); while (true) { val ev = awaitPointerEvent(); ev.changes.forEach { it.consume() }; if (ev.changes.none { it.pressed }) break } }
    }) {
        if (sizes.size > 1) {
            // handle: a glass arc hugging the module's own bottom-right rounding (circle, capsule or rounded square)
            Canvas(Modifier.fillMaxSize()) {
                val cr = when { current.w == 1 && current.h == 1 -> size.minDimension / 2; current.h == 1 -> size.height / 2
                    else -> DesignTokens.ccCorner.value * density }
                val r = cr + CcLayout.EDIT_HANDLE_GAP * density
                val c = Offset(size.width - cr, size.height - cr)
                drawArc(Color.White.copy(alpha = 0.75f), 15f, 60f, false, Offset(c.x - r, c.y - r), Size(2 * r, 2 * r),
                    style = Stroke(CcLayout.EDIT_HANDLE_STROKE * density, cap = StrokeCap.Round))
            }
            val hs = CcLayout.EDIT_HANDLE_TOUCH
            // the box moves with the growing module, so finger positions are taken in window coordinates
            var origin by remember { mutableStateOf(Offset.Zero) }
            Box(Modifier.align(Alignment.BottomEnd).size(hs.dp)
                .onGloballyPositioned { origin = it.positionInWindow() }
                .pointerInput(item.id) {
                    awaitEachGesture {
                        val down = awaitFirstDown(); down.consume()
                        val startWin = down.position + origin
                        val base = current
                        fun span(n: Int, gap: Float) = n * g.cell + (n - 1) * gap
                        val baseW = span(base.w, g.gx); val baseH = span(base.h, g.gy)
                        val maxW = span(sizes.maxOf { it.first }, g.gx); val maxH = span(sizes.maxOf { it.second }, g.gy)
                        // past the smallest / largest size it only gives a little, like Liquid Glass pulled by the
                        // finger: a few pt, saturating (M-CC-RESIZE.over)
                        fun over(x: Float) = Motion.ccResizeOver.value * x / (x + Motion.ccResizeOverRange.value)
                        fun band(v: Float, lo: Float, hi: Float) = when {
                            v > hi -> hi + over(v - hi)
                            v < lo -> lo - over(lo - v)
                            else -> v
                        }
                        while (true) {
                            val ev = awaitPointerEvent(); val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                            ch.consume()
                            val d = (ch.position + origin) - startWin
                            val w = band(baseW + d.x / density, g.cell, maxW)
                            val h = band(baseH + d.y / density, g.cell, maxH)
                            model.resizing = ControlCenterModel.LiveResize(base.id, w, h)
                            // the others make room for the size the module would snap to — only once that size is clearly
                            // nearer than the current one (hysteresis), so they don't jump back and forth with the finger
                            fun dist(sz: Pair<Int, Int>) = abs(span(sz.first, g.gx) - w) + abs(span(sz.second, g.gy) - h)
                            val best = sizes.minBy { dist(it) }
                            val now = current.w to current.h
                            if (best != now && dist(best) + CcLayout.RESIZE_HYSTERESIS * g.cell < dist(now))
                                store.resize(base.id, best.first, best.second)
                            if (!ch.pressed) break
                        }
                        model.resizing = null // springs to the chosen size (M-CC-REFLOW)
                    }
                })
        }
        // remove badge
        val bd = CcLayout.EDIT_BADGE
        Box(Modifier.offset((-CcLayout.EDIT_BADGE_OUT).dp, (-CcLayout.EDIT_BADGE_OUT).dp).size(bd.dp)
            .ccGlass(CircleShape, Color(0x99303030))
            .pointerInput(item.id) { detectTapGestures { store.remove(current.id) } }) {
            Canvas(Modifier.fillMaxSize()) {
                val l = size.width * 0.42f
                drawLine(Color.White, Offset(size.width / 2 - l / 2, size.height / 2), Offset(size.width / 2 + l / 2, size.height / 2),
                    2.2f * density, StrokeCap.Round)
            }
        }
    }
}

/** Page dots right of the grid (current one white); tap one to go there. */
@Composable
private fun PageDots(model: ControlCenterModel, g: CcGrid, density: Float, alpha: () -> Float) {
    val n = model.pageCount
    val d = CcLayout.PAGE_DOT; val pitch = CcLayout.PAGE_DOT_PITCH
    val h = (n - 1) * pitch + d
    val x = g.left + g.width + CcLayout.PAGE_DOTS_OUT
    val y = g.top + g.pageH / 2 - h / 2
    Box(Modifier.at(PtRect(x - CcLayout.PAGE_DOT_TOUCH / 2 + d / 2, y, CcLayout.PAGE_DOT_TOUCH, h)).graphicsLayer { this.alpha = alpha().coerceIn(0f, 1f) }) {
        for (i in 0 until n) {
            Box(Modifier.align(Alignment.TopCenter).offset(y = (i * pitch).dp).size(d.dp).clip(CircleShape)
                .drawBehind {
                    val on = (1f - abs(i - model.page.value)).coerceIn(0f, 1f)
                    drawCircle(Color.White.copy(alpha = CcLayout.PAGE_DOT_OFF + (1f - CcLayout.PAGE_DOT_OFF) * on))
                }
                .pointerInput(i) { detectTapGestures { model.pageTo(i) } })
        }
    }
}

/** "⊕ Add a Control" centred below the grid while editing. */
@Composable
private fun AddControlButton(model: ControlCenterModel, g: CcGrid, screenPt: Float) {
    val e = model.edit.value.coerceIn(0f, 1f)
    // above Android's gesture bar (a tap there goes to the system, not to us)
    val d = androidx.compose.ui.platform.LocalDensity.current
    val gesturePt = androidx.compose.foundation.layout.WindowInsets.mandatorySystemGestures.getBottom(d) / d.density
    val y = screenPt - maxOf(CcLayout.EDIT_ADD_FROM_BOTTOM, gesturePt + CcLayout.EDIT_ADD_ABOVE_GESTURES)
    Row(Modifier.at(PtRect(g.centerX - 90f, y - 18f, 180f, 36f)).graphicsLayer { alpha = e }
        .pointerInput(Unit) { detectTapGestures { model.openGallery() } },
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(17.dp).clip(CircleShape).background(Color.White)) {
            Canvas(Modifier.fillMaxSize()) {
                val l = size.width * 0.5f; val sw = 2f * density; val c = center
                drawLine(Color.Black, Offset(c.x - l / 2, c.y), Offset(c.x + l / 2, c.y), sw, StrokeCap.Round)
                drawLine(Color.Black, Offset(c.x, c.y - l / 2), Offset(c.x, c.y + l / 2), sw, StrokeCap.Round)
            }
        }
        Spacer(Modifier.size(7.dp))
        BasicText(stringResource(R.string.cc_add_control), style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = Color.White))
    }
}

/**
 * "Add a Control" gallery [MEASURED simulator 2026-11-26]: dark panel centred on the screen (418 pt wide, top 8 pt,
 * corners 40 pt), search field on top, then sections — built-in controls, then every app with Android Quick Settings
 * tiles (app icon + name) — each a row of round controls (Ø 62 pt, pitch 100 pt) with their name below. Tapping one
 * adds it to the Control Center.
 */
@Composable
private fun Gallery(context: Context, model: ControlCenterModel, wPt: Float, screenPt: Float, density: Float) {
    val a = { model.galleryAmount.value }
    val store = model.store(context)
    val present = store.items.map { it.id }.toSet()
    val tiles = remember { io.github.janhurn9400.liquidy.data.QsTiles.list(context) }
    var query by remember { mutableStateOf("") }
    val q = query.trim().lowercase()
    // dim + tap outside closes
    Box(Modifier.fillMaxSize().graphicsLayer { alpha = a().coerceIn(0f, 1f) }.background(Color.Black.copy(alpha = CcLayout.GALLERY_DIM))
        .pointerInput(Unit) { detectTapGestures { model.closeGallery() } })
    val pw = minOf(CcLayout.GALLERY_W, wPt - 24f)
    val ph = screenPt - CcLayout.GALLERY_TOP + 40f
    // slides up from below with a little bounce (user: "comes up from the bottom")
    Box(Modifier.at(PtRect((wPt - pw) / 2, CcLayout.GALLERY_TOP, pw, ph))
        .graphicsLayer { translationY = (1f - a()) * (ph + CcLayout.GALLERY_TOP) * density }
        .clip(RoundedCornerShape(CcLayout.GALLERY_CORNER.dp))
        .pointerInput(Unit) { detectTapGestures { } }) {
        // the panel and its rows are the glass source for the floating search field (it refracts what scrolls under it)
        val surface = com.kyant.backdrop.backdrops.rememberLayerBackdrop()
        Box(Modifier.matchParentSize().layerBackdrop(surface).background(Color(CcLayout.GALLERY_BG))) {
            Column(Modifier.fillMaxSize().verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                Spacer(Modifier.size((CcLayout.GALLERY_FIELD_Y + CcLayout.GALLERY_FIELD_H + 20f).dp))
                val builtIns = io.github.janhurn9400.liquidy.data.CcLayoutStore.BUILT_IN
                    .mapNotNull { id -> builtInGlyph(id)?.let { (l, ic) -> Triple(id, context.getString(l), CcIcon.Vector(ic) as CcIcon) } }
                    .filter { q.isEmpty() || it.second.lowercase().contains(q) }
                val add = { id: String ->
                    if (id !in present) store.add(id)
                    model.closeGallery()
                    // the original shows the page the new control landed on (a full page → the next one) [user's iPhone 11-26]
                    val row = io.github.janhurn9400.liquidy.data.CcLayoutStore.pack(store.items.toList(), model.rowsPerPage)[id]?.second
                    if (row != null) model.pageTo(row / model.rowsPerPage)
                }
                if (builtIns.isNotEmpty()) GallerySection(null, null, builtIns, present, add)
                tiles.groupBy { it.appLabel }.forEach { (app, list) ->
                    val entries = list.filter { q.isEmpty() || it.label.lowercase().contains(q) || app.lowercase().contains(q) }
                        .map { Triple(it.id, it.label, CcIcon.Bitmap(it.icon) as CcIcon) }
                    if (entries.isNotEmpty()) GallerySection(app, list.first().icon, entries, present, add)
                }
                Spacer(Modifier.size(60.dp))
            }
        }
        androidx.compose.runtime.CompositionLocalProvider(io.github.janhurn9400.liquidy.glass.LocalGlassBackdrop provides surface) {
            SearchField(query) { query = it }
        }
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit) {
    // floats over the list as Liquid Glass and stays put while the controls scroll under it (like the original)
    Box(Modifier.padding(top = CcLayout.GALLERY_FIELD_Y.dp, start = 27.dp, end = 27.dp).fillMaxWidth().height(CcLayout.GALLERY_FIELD_H.dp),
        contentAlignment = Alignment.Center) {
        io.github.janhurn9400.liquidy.glass.LiquidGlass(shape = io.github.janhurn9400.liquidy.glass.glassCapsule(), modifier = Modifier.matchParentSize(),
            whiteTint = DesignTokens.libListFieldTint.value)
        androidx.compose.foundation.text.BasicTextField(value, onChange, singleLine = true,
            textStyle = TextStyle(fontFamily = Inter, fontSize = 19.sp, color = Color.White, textAlign = TextAlign.Center),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.White),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.Center) {
                    if (value.isEmpty()) Row(verticalAlignment = Alignment.CenterVertically) {
                        Canvas(Modifier.size(18.dp)) {
                            val sw = 2f * density; val r = size.width * 0.36f; val c = Offset(size.width * 0.42f, size.height * 0.42f)
                            drawCircle(Color.White.copy(alpha = 0.6f), r, c, style = Stroke(sw))
                            drawLine(Color.White.copy(alpha = 0.6f), c + Offset(r * 0.72f, r * 0.72f), Offset(size.width * 0.95f, size.height * 0.95f), sw, StrokeCap.Round)
                        }
                        Spacer(Modifier.size(8.dp))
                        BasicText(stringResource(R.string.cc_search_controls), style = TextStyle(fontFamily = Inter, fontSize = 19.sp, color = Color.White.copy(alpha = 0.6f)))
                    }
                    inner()
                }
            })
    }
}

@Composable
private fun GallerySection(title: String?, icon: androidx.compose.ui.graphics.ImageBitmap?, entries: List<Triple<String, String, CcIcon>>,
                           present: Set<String>, add: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 27.dp)) {
        if (title != null) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp, bottom = 14.dp)) {
                if (icon != null) Image(icon, null, Modifier.size(28.dp).clip(RoundedCornerShape(7.dp)))
                Spacer(Modifier.size(10.dp))
                BasicText(title, style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = Color.White))
            }
        }
        entries.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
                row.forEach { (id, label, ic) ->
                    Column(Modifier.width(CcLayout.GALLERY_PITCH.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        CcGlyph(Modifier.size(CcLayout.GALLERY_ITEM.dp).ccGlassPress(CircleShape, Color.White.copy(alpha = 0.14f), onTap = { add(id) }), ic, Color.White, 0.45f)
                        Spacer(Modifier.size(6.dp))
                        BasicText(label, maxLines = 2, style = TextStyle(fontFamily = Inter, fontSize = 12.sp, color = Color.White.copy(alpha = 0.85f), textAlign = TextAlign.Center))
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 18.dp).height(1.dp).background(Color.White.copy(alpha = 0.12f)))
    }
}

/** Carrier with signal bars on the left, battery on the right (like the original's own status row). */
@Composable
private fun StatusRow(m: Modifier) {
    val c = SystemControls
    val info by io.github.janhurn9400.liquidy.data.rememberStatusInfo()
    val style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color.White)
    Box(m) {
        Row(Modifier.align(Alignment.CenterStart), verticalAlignment = Alignment.CenterVertically) {
            if (info.cell >= 0) {
                Canvas(Modifier.size(18.dp, 12.dp)) {
                    val bw = size.width / 4 * 0.7f
                    for (i in 0 until 4) {
                        val h = size.height * (0.4f + 0.2f * i)
                        drawRoundRect(Color.White.copy(alpha = if (i < info.cell.coerceAtMost(4)) 1f else 0.35f),
                            Offset(i * size.width / 4, size.height - h), Size(bw, h), androidx.compose.ui.geometry.CornerRadius(bw / 3))
                    }
                }
                Spacer(Modifier.width(5.dp))
            }
            BasicText(c.carrier.value, style = style)
            if (c.wifiConnected.value) { Spacer(Modifier.width(6.dp)); Glyph(Modifier.size(16.dp), Icons.wifi, Color.White, 1f) }
        }
        Row(Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically) {
            BasicText("${c.batteryPercent.value} %", style = style)
            Spacer(Modifier.width(6.dp))
            Canvas(Modifier.size(25.dp, 12.dp)) {
                val body = Size(size.width - 2.5f * density, size.height)
                val r = androidx.compose.ui.geometry.CornerRadius(3.5f * density)
                drawRoundRect(Color.White.copy(alpha = 0.4f), size = body, cornerRadius = r, style = Stroke(1f * density))
                val inset = 2f * density
                val fillW = (body.width - 2 * inset) * (c.batteryPercent.value / 100f)
                drawRoundRect(if (c.charging.value) GREEN else Color.White, Offset(inset, inset), Size(fillW, body.height - 2 * inset),
                    androidx.compose.ui.geometry.CornerRadius(2f * density))
                drawRoundRect(Color.White.copy(alpha = 0.4f), Offset(body.width + 0.8f * density, size.height * 0.33f), Size(1.6f * density, size.height * 0.34f),
                    androidx.compose.ui.geometry.CornerRadius(1f * density))
            }
        }
    }
}

/**
 * Compact connectivity platter like the original: big circles airplane (top left), location (top right, where the
 * original has its blue AirDrop) and Wi-Fi (bottom left); bottom right four small ones: mobile data, Bluetooth, hotspot, NFC.
 */
@Composable
private fun ConnectivityCompact(context: Context, onLong: () -> Unit) {
    val c = SystemControls
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize().ccGlassPress(RoundedCornerShape(DesignTokens.ccCorner.dp), onTap = {}, onLong = onLong)) {
        val d = DesignTokens.ccInner.value
        val inset = DesignTokens.ccInnerInset.value
        val span = maxWidth.value // the platter's own size (phone and Duo grids differ)
        val far = span - inset - d
        @Composable
        fun Circle(x: Float, y: Float, size: Float, icon: Path, on: Boolean, color: Color, share: Float, tap: () -> Unit) = Glyph(
            Modifier.offset(x.dp, y.dp).size(size.dp)
                .ccGlassPress(CircleShape, if (on) color else Color.White.copy(alpha = DesignTokens.ccFill.value), onTap = tap, onLong = onLong, plain = true),
            icon, Color.White, share)
        Circle(inset, inset, d, Icons.airplane, c.airplane.value, ORANGE, 0.5f) { c.toggleAirplane(context) }
        Circle(far, inset, d, Icons.location, c.location.value, BLUE, 0.46f) { c.toggleLocation(context) }
        Circle(inset, far, d, Icons.wifi, c.wifi.value, BLUE, 0.5f) { c.toggleWifi(context) }
        val cx = far + d / 2; val o = CcLayout.QUAD_OFFSET; val q = CcLayout.QUAD_D
        Circle(cx - o - q / 2, cx - o - q / 2, q, Icons.cellular, c.mobileData.value, GREEN, 0.55f) { c.toggleMobileData(context) }
        Circle(cx + o - q / 2, cx - o - q / 2, q, Icons.bluetooth, c.bluetooth.value, BLUE, 0.6f) { c.toggleBluetooth(context) }
        Circle(cx - o - q / 2, cx + o - q / 2, q, Icons.link, c.hotspot.value, GREEN, 0.6f) { launch(context, Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS)) }
        Circle(cx + o - q / 2, cx + o - q / 2, q, Icons.nfc, c.nfc.value, BLUE, 0.55f) { c.toggleNfc(context) }
    }
}

@Composable
private fun MediaCompact() {
    val media = SystemControls.media.value
    val inset = DesignTokens.ccInnerInset.value
    Box(Modifier.fillMaxSize()) {
        // output button top right like the original
        Glyph(Modifier.align(Alignment.TopEnd).padding(top = inset.dp, end = inset.dp).size(34.dp).clip(CircleShape)
            .background(Color.White.copy(alpha = DesignTokens.ccFill.value)), Icons.output, Color.White, 0.5f)
        Column(Modifier.fillMaxSize().padding(inset.dp)) {
            Artwork(media, DesignTokens.ccArt.value)
            Spacer(Modifier.weight(1f))
            val title = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color.White)
            BasicText(media?.title?.takeIf { it.isNotEmpty() } ?: stringResource(R.string.cc_not_playing), style = title, maxLines = 1, softWrap = false)
            BasicText(media?.artist.orEmpty(), style = title.copy(fontWeight = FontWeight.Normal, color = Color.White.copy(alpha = 0.55f)), maxLines = 1, softWrap = false)
            Spacer(Modifier.size(6.dp))
            TransportRow(media, 26f)
        }
    }
}

@Composable
private fun Artwork(media: SystemControls.Media?, sizePt: Float) {
    val bmp = media?.art
    val shape = RoundedCornerShape((sizePt * ART_CORNER).dp)
    if (bmp != null) Image(remember(bmp) { bmp.asImageBitmap() }, null, Modifier.size(sizePt.dp).clip(shape), contentScale = ContentScale.Crop)
    else Box(Modifier.size(sizePt.dp).clip(shape).background(Color.White.copy(alpha = DesignTokens.ccFill.value)))
}

@Composable
private fun TransportRow(media: SystemControls.Media?, glyphPt: Float, playPt: Float = glyphPt) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        val t = media?.controller?.transportControls
        Glyph(Modifier.size(glyphPt.dp).ccPress(onTap = { t?.skipToPrevious() }), Icons.previous, Color.White, 0.9f)
        Glyph(Modifier.size(playPt.dp).ccPress(onTap = { if (media?.playing == true) t?.pause() else t?.play() }),
            if (media?.playing == true) Icons.pause else Icons.play, Color.White, 0.9f)
        Glyph(Modifier.size(glyphPt.dp).ccPress(onTap = { t?.skipToNext() }), Icons.next, Color.White, 0.9f)
    }
}

@Composable
private fun RoundToggle(m: Modifier, icon: Path, on: Boolean, activeGlyph: Color = Color.Black, tap: () -> Unit) {
    Glyph(m.ccGlassPress(CircleShape, if (on) Color.White else Color.White.copy(alpha = DesignTokens.ccFill.value), onTap = tap),
        icon, if (on) activeGlyph else Color.White, 0.42f)
}

@Composable
private fun FocusModule(m: Modifier, on: Boolean, tap: () -> Unit) {
    Box(m.ccGlassPress(CircleShape, if (on) Color.White else Color.White.copy(alpha = DesignTokens.ccFill.value), onTap = tap)) {
        Row(Modifier.fillMaxSize().padding(start = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Glyph(Modifier.size(38.dp).clip(CircleShape).background(if (on) PURPLE else Color.White.copy(alpha = 0.9f)), Icons.moon, if (on) Color.White else PURPLE, 0.6f)
            Spacer(Modifier.size(10.dp))
            BasicText(stringResource(R.string.cc_focus), style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                color = if (on) Color.Black else Color.White))
            Spacer(Modifier.size(5.dp))
            ChevronUpDown(Modifier.size(8.dp, 12.dp), if (on) Color.Black.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.6f))
        }
    }
}

/**
 * Slider (1×2 compact or the big one): white level from the bottom, glyph near the bottom. Dragging moves the level
 * relatively (like the original); holding still for M-CC-PRESS.long opens it big ([onLong]).
 */
@Composable
private fun Slider(m: Modifier, value: Float, icon: Path, glyph: Color, glyphPt: Float = 28f, onLong: (() -> Unit)? = null, onChange: (Float) -> Unit) {
    val current by rememberUpdatedState(value)
    val longNow by rememberUpdatedState(onLong)
    val change by rememberUpdatedState(onChange)
    val scope = rememberCoroutineScope()
    val press = remember { Animatable(1f) }
    // pulled past full/empty: the slider stretches that way, rubber-banded, and springs back (M-CC-SLIDER-STRETCH)
    val over = remember { Animatable(0f) }
    val pull = remember { io.github.janhurn9400.liquidy.glass.GlassPull(scope) }
    Box(m.graphicsLayer {
            val o = over.value; val hgt = size.height
            // rubber band that never gets longer than M-CC-SLIDER-STRETCH (stays clear of the next tile)
            val st = 1f + (if (hgt > 0f) Motion.rubberBand(abs(o), Motion.ccSliderStretch.value * density) / hgt else 0f)
            scaleX = press.value / kotlin.math.sqrt(st); scaleY = press.value * st
            transformOrigin = TransformOrigin(0.5f, if (o > 0f) 1f else 0f)
        }
        .glassPull(CircleShape) { pull }
        .ccGlass(CircleShape)
        .pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown()
                // sliders never grow when touched (user, iPhone) — only the shimmer and the stretch past the ends
                pull.press(down.position)
                var v = current
                var raw = v
                var dragging = false
                var expanded = false
                val longJob = longNow?.let { l -> scope.launch { delay(Motion.ccLongPress.value.toLong()); if (!dragging) { expanded = true; l() } } }
                while (true) {
                    val ev = awaitPointerEvent()
                    val c = ev.changes.firstOrNull { it.id == down.id } ?: break
                    if (!c.pressed) break
                    if (expanded) { c.consume(); continue }
                    if (!dragging && abs(c.position.y - down.position.y) > viewConfiguration.touchSlop) { dragging = true; longJob?.cancel() }
                    if (dragging) {
                        raw -= (c.position.y - c.previousPosition.y) / size.height
                        v = raw.coerceIn(0f, 1f); change(v); c.consume()
                        val o = (raw - v) * size.height
                        scope.launch { over.snapTo(o) }
                    }
                }
                longJob?.cancel()
                pull.release()
                scope.launch { over.animateTo(0f, Motion.glassPullBack.spec()) }
                scope.launch { press.animateTo(1f, Motion.ccPress.spec()) }
            }
        }
        .drawBehind {
            val h = size.height * value.coerceIn(0f, 1f)
            drawRect(Color.White.copy(alpha = SLIDER_FILL), Offset(0f, size.height - h), Size(size.width, h))
        }) {
        Glyph(Modifier.align(Alignment.BottomCenter).offset(y = (-(CcLayout.SLIDER_GLYPH_FROM_BOTTOM - glyphPt / 2)).dp).size(glyphPt.dp), icon, glyph, 0.95f)
    }
}

// ── expanded modules ─────────────────────────────────────────────────────────────────

@Composable
private fun Expanded(context: Context, model: ControlCenterModel, m: CcExpand, g: CcGrid, density: Float) {
    val from = g.from(m)
    val to = g.to(m)
    val wPt = g.centerX * 2
    val e = { model.expand.value }
    val rect = { from.lerp(to, e()) }
    val c = SystemControls
    when (m) {
        CcExpand.Volume, CcExpand.Brightness -> {
            val bright = m == CcExpand.Brightness
            // glyph above the big slider and (brightness) three round buttons below fade in with it
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = ((e() - 0.2f) / 0.6f).coerceIn(0f, 1f) }) {
                Glyph(Modifier.at(PtRect((wPt - CcLayout.BIG_GLYPH) / 2, g.bigGlyphY - CcLayout.BIG_GLYPH / 2, CcLayout.BIG_GLYPH, CcLayout.BIG_GLYPH)),
                    if (bright) Icons.sun else Icons.speaker, Color.White, 0.9f)
                if (bright) {
                    val items = listOf(
                        Triple(Icons.contrast, R.string.cc_dark_mode, c.darkMode.value) to { c.toggleDarkMode(context) },
                        Triple(Icons.moon, R.string.cc_night_light, c.nightLight.value) to { c.toggleNightLight(context) },
                        Triple(Icons.brightnessAuto, R.string.cc_auto_brightness, c.autoBrightness.value) to { c.toggleAutoBrightness(context) },
                    )
                    items.forEachIndexed { i, (t, tap) ->
                        val cx = wPt / 2 + (i - 1) * CcLayout.BIG_BUTTON_PITCH
                        val d = CcLayout.BIG_BUTTON_D
                        RoundToggle(Modifier.at(PtRect(cx - d / 2, g.bigButtonY - d / 2, d, d)), t.first, t.third) { tap() }
                        Label(Modifier.at(PtRect(cx - 50f, g.bigButtonY + d / 2 + 6f, 100f, 34f)), stringResource(t.second),
                            stringResource(if (t.third) R.string.cc_on else R.string.cc_off), TextAlign.Center, 12.sp)
                    }
                }
            }
            // the slider itself morphs from its slot to the big capsule (no crossfade — it's the same object)
            Slider(Modifier.at(density, rect), if (bright) c.brightness.floatValue else c.volume.floatValue,
                if (bright) Icons.sun else Icons.speaker, if (bright) YELLOW else BLUE, glyphPt = 28f + 14f * e().coerceIn(0f, 1f)) { v ->
                if (bright) c.setBrightness(context, v) else c.setVolume(context, v)
            }
        }
        CcExpand.Media -> {
            Box(Modifier.at(density, rect).ccGlass(RoundedCornerShape(DesignTokens.ccCorner.dp))) {
                // compact content fades out in place, the big one fades in grown from the slot (scaled with the panel)
                Box(Modifier.requiredSize(from.w.dp, from.h.dp).align(Alignment.TopStart).graphicsLayer {
                    transformOrigin = TransformOrigin(0f, 0f)
                    val s = rect().w / from.w; scaleX = s; scaleY = s
                    alpha = (1f - e() / 0.35f).coerceIn(0f, 1f)
                }) { MediaCompact() }
                Box(Modifier.requiredSize(to.w.dp, to.h.dp).align(Alignment.TopStart).graphicsLayer {
                    transformOrigin = TransformOrigin(0f, 0f)
                    val s = rect().w / to.w; scaleX = s; scaleY = s
                    alpha = ((e() - 0.25f) / 0.5f).coerceIn(0f, 1f)
                }) { MediaBig(context) }
            }
        }
        CcExpand.Connectivity -> {
            // the platter grows towards the panel bounds and dissolves; every control travels out of its own small
            // circle to its big place (airplane circle → capsule, Wi-Fi circle → tile, …) [MEASURED] 21.35–21.55 s
            Box(Modifier.at(density, rect).graphicsLayer { alpha = (1f - e() / 0.5f).coerceIn(0f, 1f) }
                .ccGlass(RoundedCornerShape(DesignTokens.ccCorner.dp)))
            ConnectivityMorph(context, g, to, e, density)
        }
    }
}

/** Expanded connectivity: each item morphs from its compact circle ([src]) to its capsule/tile ([dst]). */
@Composable
private fun ConnectivityMorph(context: Context, g: CcGrid, to: PtRect, e: () -> Float, density: Float) {
    val platter = g.connectivity
    val c = SystemControls
    val d = DesignTokens.ccInner.value
    val inset = DesignTokens.ccInnerInset.value
    val far = platter.w - inset - d
    val cx = far + d / 2; val o = CcLayout.QUAD_OFFSET; val q = CcLayout.QUAD_D
    fun big(x: Float, y: Float) = PtRect(platter.x + x, platter.y + y, d, d)
    fun small(dx: Float, dy: Float) = PtRect(platter.x + cx + dx - q / 2, platter.y + cx + dy - q / 2, q, q)
    val gap = g.expGap; val row = CcLayout.EXP_ROW; val tile = g.expTile
    val tw = (to.w - gap) / 2
    var y = to.y
    fun next(h: Float): Float { val v = y; y += h + gap; return v }
    val yAir = next(row); val yT1 = next(tile); val yT2 = next(tile); val yHot = next(row); val yNfc = next(row); val ySave = next(row)
    fun s(on: Boolean) = if (on) R.string.cc_on else R.string.cc_off
    MorphItem(big(inset, inset), PtRect(to.x, yAir, to.w, row), false, e, density, Icons.airplane, ORANGE, c.airplane.value,
        stringResource(R.string.cc_airplane), stringResource(s(c.airplane.value))) { c.toggleAirplane(context) }
    MorphItem(big(inset, far), PtRect(to.x, yT1, tw, tile), true, e, density, Icons.wifi, BLUE, c.wifi.value, stringResource(R.string.cc_wifi),
        stringResource(if (!c.wifi.value) R.string.cc_off else if (c.wifiConnected.value) R.string.cc_connected else R.string.cc_not_connected)) { c.toggleWifi(context) }
    MorphItem(big(far, inset), PtRect(to.x + tw + gap, yT1, tw, tile), true, e, density, Icons.location, BLUE, c.location.value,
        stringResource(R.string.cc_location), stringResource(s(c.location.value))) { c.toggleLocation(context) }
    MorphItem(small(-o, -o), PtRect(to.x, yT2, tw, tile), true, e, density, Icons.cellular, GREEN, c.mobileData.value, stringResource(R.string.cc_mobile_data),
        c.carrier.value.takeIf { it.isNotEmpty() && c.mobileData.value } ?: stringResource(s(c.mobileData.value))) { c.toggleMobileData(context) }
    MorphItem(small(o, -o), PtRect(to.x + tw + gap, yT2, tw, tile), true, e, density, Icons.bluetooth, BLUE, c.bluetooth.value,
        stringResource(R.string.cc_bluetooth), stringResource(s(c.bluetooth.value))) { c.toggleBluetooth(context) }
    MorphItem(small(-o, o), PtRect(to.x, yHot, to.w, row), false, e, density, Icons.link, GREEN, c.hotspot.value,
        stringResource(R.string.cc_hotspot), stringResource(s(c.hotspot.value))) { launch(context, Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS)) }
    MorphItem(small(o, o), PtRect(to.x, yNfc, to.w, row), false, e, density, Icons.nfc, BLUE, c.nfc.value, stringResource(R.string.cc_nfc),
        stringResource(if (!c.hasNfc.value) R.string.cc_not_available else s(c.nfc.value))) { c.toggleNfc(context) }
    // no compact counterpart: grows out of the middle of the small circles
    MorphItem(small(0f, 0f), PtRect(to.x, ySave, to.w, row), false, e, density, Icons.dataSaver, ORANGE, c.dataSaver.value,
        stringResource(R.string.cc_data_saver), stringResource(s(c.dataSaver.value)), fromNothing = true) { c.toggleDataSaver(context) }
}

/**
 * One control of the expanded connectivity: its glass grows from the compact circle [src] to [dst] (capsule or [tile]),
 * the icon circle travels from filling the circle to its corner, the label fades in last.
 */
@Composable
private fun MorphItem(src: PtRect, dst: PtRect, tile: Boolean, e: () -> Float, density: Float, icon: Path, color: Color, on: Boolean,
                      title: String, subtitle: String, fromNothing: Boolean = false, tap: () -> Unit) {
    val r = { src.lerp(dst, e()) }
    val shape = if (tile) RoundedCornerShape(DesignTokens.ccCorner.dp) else CircleShape
    Box(Modifier.at(density, r).graphicsLayer { if (fromNothing) alpha = e().coerceIn(0f, 1f) }.ccPress(onTap = tap)) {
        // own glass appears as it leaves the platter
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = (e() / 0.6f).coerceIn(0f, 1f) }.ccGlass(shape))
        val iconAt = {
            val t = e()
            val endX = if (tile) CcLayout.EXP_ICON_INSET else (dst.h - CcLayout.EXP_ICON) / 2
            val size = src.w + (CcLayout.EXP_ICON - src.w) * t
            PtRect(endX * t, endX * t, size, size)
        }
        Glyph(Modifier.at(density, iconAt).clip(CircleShape)
            .background(if (on) color else Color.White.copy(alpha = DesignTokens.ccFill.value * 1.6f)), icon, Color.White, 0.52f)
        val labelMod = if (tile) Modifier.align(Alignment.BottomStart).padding(start = 16.dp, bottom = 22.dp)
            else Modifier.align(Alignment.CenterStart).padding(start = CcLayout.EXP_TEXT_X.dp)
        Label(labelMod.graphicsLayer { alpha = ((e() - 0.45f) / 0.55f).coerceIn(0f, 1f) }, title, subtitle)
    }
}

@Composable
private fun Label(m: Modifier, title: String, subtitle: String, align: TextAlign = TextAlign.Start, size: TextUnit = 16.sp) {
    Column(m, horizontalAlignment = if (align == TextAlign.Center) Alignment.CenterHorizontally else Alignment.Start) {
        BasicText(title, style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = size, color = Color.White, textAlign = align), maxLines = 1)
        BasicText(subtitle, style = TextStyle(fontFamily = Inter, fontSize = size * 0.92f, color = Color.White.copy(alpha = 0.6f), textAlign = align), maxLines = 1)
    }
}

/** Big media panel (the original's music page): artwork, title, progress, transport, volume, output. */
@Composable
private fun MediaBig(context: Context) {
    val c = SystemControls
    val media = c.media.value
    val inset = CcLayout.MEDIA_INSET
    // position ticks while playing
    var now by remember { mutableLongStateOf(android.os.SystemClock.elapsedRealtime()) }
    LaunchedEffect(media?.playing) { while (media?.playing == true) { now = android.os.SystemClock.elapsedRealtime(); delay(500) } }
    Column(Modifier.fillMaxSize().padding(start = inset.dp, end = inset.dp, top = (inset + 1f).dp)) {
        Artwork(media, CcLayout.MEDIA_ART)
        Spacer(Modifier.height(30.dp))
        val title = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color.White)
        BasicText(media?.title?.takeIf { it.isNotEmpty() } ?: stringResource(R.string.cc_not_playing), style = title, maxLines = 1, softWrap = false)
        BasicText(media?.artist.orEmpty(), style = title.copy(fontWeight = FontWeight.Normal, color = Color.White.copy(alpha = 0.55f)), maxLines = 1, softWrap = false)
        Spacer(Modifier.height(18.dp))
        val dur = media?.duration ?: 0L
        val pos = media?.let { m -> if (m.playing) m.position + ((now - m.positionAt) * m.speed).toLong() else m.position }?.coerceIn(0L, dur.coerceAtLeast(0L)) ?: 0L
        Bar(Modifier.fillMaxWidth().height(4.dp), if (dur > 0) pos.toFloat() / dur else 0f)
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth()) {
            val ts = TextStyle(fontFamily = Inter, fontSize = 11.sp, color = Color.White.copy(alpha = 0.55f))
            BasicText(time(pos), style = ts, modifier = Modifier.align(Alignment.CenterStart))
            BasicText("−" + time((dur - pos).coerceAtLeast(0L)), style = ts, modifier = Modifier.align(Alignment.CenterEnd))
        }
        Spacer(Modifier.height(18.dp))
        TransportRow(media, 34f, 40f)
        Spacer(Modifier.height(30.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Glyph(Modifier.size(14.dp), Icons.speakerLow, Color.White.copy(alpha = 0.6f), 1f)
            Spacer(Modifier.width(10.dp))
            Bar(Modifier.weight(1f).height(6.dp), c.volume.floatValue)
            Spacer(Modifier.width(10.dp))
            Glyph(Modifier.size(18.dp), Icons.speaker, Color.White.copy(alpha = 0.6f), 1f)
        }
        Spacer(Modifier.height(22.dp))
        Row(Modifier.align(Alignment.CenterHorizontally).height(32.dp).ccGlassPress(CircleShape, Color.White.copy(alpha = DesignTokens.ccFill.value),
                onTap = { launch(context, Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)) }, plain = true).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Glyph(Modifier.size(16.dp), Icons.output, Color.White, 1f)
            Spacer(Modifier.width(6.dp))
            BasicText(stringResource(R.string.cc_output), style = TextStyle(fontFamily = Inter, fontSize = 13.sp, color = Color.White))
        }
    }
}

@Composable
private fun Bar(m: Modifier, v: Float) = Canvas(m) {
    val r = androidx.compose.ui.geometry.CornerRadius(size.height / 2)
    drawRoundRect(Color.White.copy(alpha = 0.25f), cornerRadius = r)
    drawRoundRect(Color.White.copy(alpha = 0.9f), size = Size(size.width * v.coerceIn(0f, 1f), size.height), cornerRadius = r)
}

private fun time(ms: Long): String { val s = ms / 1000; return "%d:%02d".format(s / 60, s % 60) }

@Composable
private fun ChevronUpDown(m: Modifier, color: Color) = Canvas(m) {
    val w = size.width; val h = size.height; val sw = 1.4f * density
    drawLine(color, Offset(0f, h * 0.38f), Offset(w / 2, h * 0.12f), sw, StrokeCap.Round)
    drawLine(color, Offset(w / 2, h * 0.12f), Offset(w, h * 0.38f), sw, StrokeCap.Round)
    drawLine(color, Offset(0f, h * 0.62f), Offset(w / 2, h * 0.88f), sw, StrokeCap.Round)
    drawLine(color, Offset(w / 2, h * 0.88f), Offset(w, h * 0.62f), sw, StrokeCap.Round)
}

private fun launch(context: Context, intent: Intent) {
    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** Translucent module glass: light veil, hairline and the object light rim (the blurred home screen shows through). */
private fun Modifier.ccGlass(shape: Shape, fill: Color = Color.White.copy(alpha = DesignTokens.ccFill.value)) =
    this.clip(shape).drawBehind {
        val outline = shape.createOutline(size, layoutDirection, this)
        drawOutline(outline, fill)
        drawOutline(outline, Color.White.copy(alpha = HAIRLINE), style = Stroke(1.2f * density))
        drawObjectRim(this, shape)
    }

/**
 * M-CC-PRESS: grows a little under the finger and springs back; tap and optional long press. For things without
 * their own glass (transport buttons, rows) — glass modules use [ccGlassPress].
 */
@Composable
private fun Modifier.ccPress(onTap: () -> Unit, onLong: (() -> Unit)? = null): Modifier {
    val scope = rememberCoroutineScope()
    val s = remember { Animatable(1f) }
    val pull = remember { io.github.janhurn9400.liquidy.glass.GlassPull(scope) }
    val tap = rememberUpdatedState(onTap); val long = rememberUpdatedState(onLong)
    return composedPress(s, pull, tap, long, grow = true, stretch = false)
}

/**
 * A glass module that reacts like Liquid Glass (M-CC-PRESS + M-GLASS-PULL): grows a little and shimmers under the
 * finger; held and pulled, only the glass stretches along x/y (the edge facing away stays put) — the icon inside does
 * not. [plain] = flat [fill] without hairline and rim (coloured connectivity circles).
 */
@Composable
private fun Modifier.ccGlassPress(shape: Shape, fill: Color = Color.White.copy(alpha = DesignTokens.ccFill.value),
                                  onTap: () -> Unit, onLong: (() -> Unit)? = null, plain: Boolean = false): Modifier {
    val scope = rememberCoroutineScope()
    val s = remember { Animatable(1f) }
    val pull = remember { io.github.janhurn9400.liquidy.glass.GlassPull(scope) }
    val tap = rememberUpdatedState(onTap); val long = rememberUpdatedState(onLong)
    return this
        .composedPress(s, pull, tap, long, grow = true, stretch = true)
        // the element (with its icon) stretches only along x/y …
        .graphicsLayer {
            val st = pull.stretch(density) ?: return@graphicsLayer
            transformOrigin = st.origin; scaleX = st.cx; scaleY = st.cy
        }
        // … the glass behind it along the pull direction (undo the x/y part, then stretch along the angle)
        .drawBehind {
            val st = pull.stretch(density)
            withTransform({
                if (st != null) {
                    val pv = Offset(st.origin.pivotFractionX * size.width, st.origin.pivotFractionY * size.height)
                    scale(1f / st.cx, 1f / st.cy, pv)
                    rotate(st.angle, pv); scale(1f + st.e, st.thin, pv); rotate(-st.angle, pv)
                }
            }) {
                val outline = shape.createOutline(size, layoutDirection, this)
                drawOutline(outline, fill)
                if (!plain) {
                    drawOutline(outline, Color.White.copy(alpha = HAIRLINE), style = Stroke(1.2f * density))
                    drawObjectRim(this, shape)
                }
                drawGlassGlow(pull, shape)
            }
        }
        .clip(shape)
}

/** Shared press handling: scale, shimmer, optional pull, tap / long press (a release near the element still taps). */
private fun Modifier.composedPress(s: Animatable<Float, *>, pull: io.github.janhurn9400.liquidy.glass.GlassPull,
                                   onTap: androidx.compose.runtime.State<() -> Unit>, onLong: androidx.compose.runtime.State<(() -> Unit)?>,
                                   grow: Boolean, stretch: Boolean): Modifier = this
    .graphicsLayer { scaleX = s.value; scaleY = s.value }
    .pointerInput(Unit) {
        kotlinx.coroutines.coroutineScope {
            val scope = this
            awaitEachGesture {
                val down = awaitFirstDown()
                if (grow) scope.launch { s.animateTo(Motion.ccPressScale.value, Motion.ccPress.spec()) }
                pull.press(down.position)
                var longFired = false
                val longJob = onLong.value?.let { l -> scope.launch { delay(Motion.ccLongPress.value.toLong()); longFired = true; l() } }
                var moved = false
                var last = down.position
                var cancelled = false
                while (true) {
                    val ev = awaitPointerEvent()
                    val c = ev.changes.firstOrNull { it.id == down.id } ?: run { cancelled = true; null } ?: break
                    last = c.position
                    val d = c.position - down.position
                    if (!moved && d.getDistance() > viewConfiguration.touchSlop) { moved = true; longJob?.cancel() }
                    if (moved) { if (stretch) pull.drag(d); c.consume() }
                    if (!c.pressed) break
                }
                longJob?.cancel()
                pull.release()
                scope.launch { s.animateTo(1f, Motion.ccPress.spec()) }
                val slack = CcLayout.PRESS_TAP_SLACK * density
                val inside = last.x > -slack && last.y > -slack && last.x < size.width + slack && last.y < size.height + slack
                if (!cancelled && !longFired && inside) onTap.value()
            }
        }
    }

/** Draws a 24-unit vector path centred, filling [share] of the box. */
@Composable
private fun Glyph(m: Modifier, path: Path, color: Color, share: Float) = Box(m) {
    Canvas(Modifier.fillMaxSize()) { drawGlyph(path, color, share) }
}

private fun DrawScope.drawGlyph(path: Path, color: Color, share: Float) {
    val s = minOf(size.width, size.height) * share / 24f
    translate((size.width - 24f * s) / 2, (size.height - 24f * s) / 2) {
        scale(s, s, pivot = Offset.Zero) { drawPath(path, color) }
    }
}

/** Glyphs: Material icon paths (Apache 2.0) and simple own shapes — no SF Symbols. */
private object Icons {
    private fun p(d: String) = PathParser().parsePathString(d).toPath()
    val airplane = p("M21,16v-2l-8,-5V3.5C13,2.67 12.33,2 11.5,2S10,2.67 10,3.5V9l-8,5v2l8,-2.5V19l-2,1.5V22l3.5,-1 3.5,1v-1.5L13,19v-5.5l8,2.5z")
    val cellular = p("M17,4h3v16h-3zM5,14h3v6H5zM11,9h3v11h-3z")
    val wifi = p("M1,9l2,2c4.97,-4.97 13.03,-4.97 18,0l2,-2C16.93,2.93 7.08,2.93 1,9zM9,17l3,3 3,-3c-1.65,-1.66 -4.34,-1.66 -6,0zM5,13l2,2c2.76,-2.76 7.24,-2.76 10,0l2,-2C15.14,9.14 8.87,9.14 5,13z")
    val bluetooth = p("M17.71,7.71L12,2h-1v7.59L6.41,5 5,6.41 10.59,12 5,17.59 6.41,19 11,14.41V22h1l5.71,-5.71 -4.3,-4.29 4.3,-4.29zM13,5.83l1.88,1.88L13,9.59V5.83zM14.88,16.29L13,18.17v-3.76l1.88,1.88z")
    val location = p("M21,3L3,10.53v0.98l6.84,2.65L12.48,21h0.98L21,3z")
    val nfc = p("M20,2H4c-1.1,0 -2,0.9 -2,2v16c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V4c0,-1.1 -0.9,-2 -2,-2zM20,20H4V4h16v16zM18,6h-5c-1.1,0 -2,0.9 -2,2v2.28c-0.6,0.35 -1,0.98 -1,1.72 0,1.1 0.9,2 2,2s2,-0.9 2,-2c0,-0.74 -0.4,-1.38 -1,-1.72V8h3v8H8V8h2V6H6v12h12V6z")
    val link = p("M3.9,12c0,-1.71 1.39,-3.1 3.1,-3.1h4V7H7c-2.76,0 -5,2.24 -5,5s2.24,5 5,5h4v-1.9H7c-1.71,0 -3.1,-1.39 -3.1,-3.1zM8,13h8v-2H8v2zM17,7h-4v1.9h4c1.71,0 3.1,1.39 3.1,3.1s-1.39,3.1 -3.1,3.1h-4V17h4c2.76,0 5,-2.24 5,-5s-2.24,-5 -5,-5z")
    val dataSaver = p("M13,2.05v3.03c3.39,0.49 6,3.39 6,6.92 0,0.9 -0.18,1.75 -0.48,2.54l2.6,1.53c0.56,-1.24 0.88,-2.62 0.88,-4.07 0,-5.18 -3.95,-9.45 -9,-9.95zM12,19c-3.87,0 -7,-3.13 -7,-7 0,-3.53 2.61,-6.43 6,-6.92V2.05c-5.06,0.5 -9,4.76 -9,9.95 0,5.52 4.47,10 9.99,10 3.31,0 6.24,-1.61 8.06,-4.09l-2.6,-1.53C16.17,17.98 14.21,19 12,19z")
    val brightnessAuto = p("M10.85,12.65h2.3L12,9l-1.15,3.65zM20,8.69V4h-4.69L12,0.69 8.69,4H4v4.69L0.69,12 4,15.31V20h4.69L12,23.31 15.31,20H20v-4.69L23.31,12 20,8.69zM14.3,16l-0.7,-2h-3.2l-0.7,2H7.8L11,7h2l3.2,9h-1.9z")
    val output = p("M6,22h12l-6,-6zM21,3H3c-1.1,0 -2,0.9 -2,2v12c0,1.1 0.9,2 2,2h4v-2H3V5h18v12h-4v2h4c1.1,0 2,-0.9 2,-2V5c0,-1.1 -0.9,-2 -2,-2z")
    val bell = p("M12,22c1.1,0 2,-0.9 2,-2h-4c0,1.1 0.89,2 2,2zM18,16v-5c0,-3.07 -1.64,-5.64 -4.5,-6.32V4c0,-0.83 -0.67,-1.5 -1.5,-1.5s-1.5,0.67 -1.5,1.5v0.68C7.63,5.36 6,7.92 6,11v5l-2,2v1h16v-1l-2,-2z")
    val bellOff = p("M12,22c1.1,0 2,-0.9 2,-2h-4c0,1.1 0.89,2 2,2zM18,11c0,-3.07 -1.64,-5.64 -4.5,-6.32V4c0,-0.83 -0.67,-1.5 -1.5,-1.5s-1.5,0.67 -1.5,1.5v0.68c-0.15,0.03 -0.29,0.08 -0.42,0.12L18,13.68V11zM5.41,3.35L4,4.76l2.81,2.81C6.29,8.57 6,9.73 6,11v5l-2,2v1h14.24l1.74,1.74 1.41,-1.41L5.41,3.35z")
    val speaker = p("M3,9v6h4l5,5V4L7,9H3zM16.5,12c0,-1.77 -1.02,-3.29 -2.5,-4.03v8.05c1.48,-0.73 2.5,-2.25 2.5,-4.02zM14,3.23v2.06c2.89,0.86 5,3.54 5,6.71s-2.11,5.850 -5,6.71v2.06c4.01,-0.91 7,-4.49 7,-8.77s-2.99,-7.86 -7,-8.77z")
    val speakerLow = p("M7,9v6h4l5,5V4l-5,5H7z")
    val sun = p("M12,7c-2.76,0 -5,2.24 -5,5s2.24,5 5,5 5,-2.24 5,-5 -2.24,-5 -5,-5zM2,13h2c0.55,0 1,-0.45 1,-1s-0.45,-1 -1,-1H2c-0.55,0 -1,0.45 -1,1s0.45,1 1,1zM20,13h2c0.55,0 1,-0.45 1,-1s-0.45,-1 -1,-1h-2c-0.55,0 -1,0.45 -1,1s0.45,1 1,1zM11,2v2c0,0.55 0.45,1 1,1s1,-0.45 1,-1V2c0,-0.55 -0.45,-1 -1,-1s-1,0.45 -1,1zM11,20v2c0,0.55 0.45,1 1,1s1,-0.45 1,-1v-2c0,-0.55 -0.45,-1 -1,-1s-1,0.45 -1,1zM5.99,4.58c-0.39,-0.39 -1.03,-0.39 -1.41,0 -0.39,0.39 -0.39,1.03 0,1.41l1.06,1.06c0.39,0.39 1.03,0.39 1.41,0s0.39,-1.03 0,-1.41L5.99,4.58zM18.36,16.95c-0.39,-0.39 -1.03,-0.39 -1.41,0 -0.39,0.39 -0.39,1.03 0,1.41l1.06,1.06c0.39,0.39 1.03,0.39 1.41,0 0.39,-0.39 0.39,-1.03 0,-1.41l-1.06,-1.06zM19.42,5.99c0.39,-0.39 0.39,-1.03 0,-1.41 -0.39,-0.39 -1.03,-0.39 -1.41,0l-1.06,1.06c-0.39,0.39 -0.39,1.03 0,1.41s1.03,0.39 1.41,0l1.06,-1.06zM7.05,18.36c0.39,-0.39 0.39,-1.03 0,-1.41 -0.39,-0.39 -1.03,-0.39 -1.41,0l-1.06,1.06c-0.39,0.39 -0.39,1.03 0,1.41s1.03,0.39 1.41,0l1.06,-1.06z")
    val torch = p("M6,2h12v3H6V2zM6,7v1l2,3v11h8V11l2,-3V7H6zM12,15.5c-0.83,0 -1.5,-0.67 -1.5,-1.5s0.67,-1.5 1.5,-1.5 1.5,0.67 1.5,1.5 -0.67,1.5 -1.5,1.5z")
    val camera = p("M9,2L7.17,4H4c-1.1,0 -2,0.9 -2,2v12c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V6c0,-1.1 -0.9,-2 -2,-2h-3.17L15,2H9zM12,17c-2.76,0 -5,-2.24 -5,-5s2.24,-5 5,-5 5,2.24 5,5 -2.24,5 -5,5zM12,15.2c1.77,0 3.2,-1.43 3.2,-3.2S13.77,8.8 12,8.8 8.8,10.23 8.8,12s1.43,3.2 3.2,3.2z")
    val play = p("M7,4.5v15c0,0.8 0.85,1.3 1.55,0.9l12.5,-7.5c0.65,-0.4 0.65,-1.4 0,-1.8L8.55,3.6C7.85,3.2 7,3.7 7,4.5z")
    val pause = p("M6,4h4v16H6zM14,4h4v16h-4z")
    val next = p("M2,6.5v11c0,0.6 0.65,0.95 1.15,0.6L11,12.6v4.9c0,0.6 0.65,0.95 1.15,0.6l8.1,-5.5c0.45,-0.3 0.45,-0.9 0,-1.2l-8.1,-5.5C11.65,5.55 11,5.9 11,6.5v4.9L3.15,5.9C2.65,5.55 2,5.9 2,6.5z")
    val previous = p("M22,6.5v11c0,0.6 -0.65,0.95 -1.15,0.6L13,12.6v4.9c0,0.6 -0.65,0.95 -1.15,0.6l-8.1,-5.5c-0.45,-0.3 -0.45,-0.9 0,-1.2l8.1,-5.5C12.35,5.55 13,5.9 13,6.5v4.9l7.85,-5.5C21.35,5.55 22,5.9 22,6.5z")
    val plus = p("M11,4h2v7h7v2h-7v7h-2v-7H4v-2h7z")
    val power = p("M11,2h2v10h-2zM16.56,5.44l-1.45,1.45C16.84,7.94 18,9.83 18,12c0,3.31 -2.69,6 -6,6s-6,-2.69 -6,-6c0,-2.17 1.16,-4.06 2.88,-5.12L7.44,5.44C5.36,6.88 4,9.28 4,12c0,4.42 3.58,8 8,8s8,-3.58 8,-8c0,-2.72 -1.36,-5.12 -3.44,-6.56z")
    val moon = p("M12.3,2.5c-5.2,0.4 -9.3,4.7 -9.3,10 0,5.5 4.5,10 10,10 5.3,0 9.6,-4.1 10,-9.3 -1.3,1.7 -3.4,2.8 -5.7,2.8 -4,0 -7.2,-3.2 -7.2,-7.2 0,-2.3 1.1,-4.4 2.2,-6.3z")
    val rotationLock = p("M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10c4.75,0 8.73,-3.31 9.75,-7.75h-2.07C18.7,17.6 15.6,20 12,20c-4.41,0 -8,-3.59 -8,-8s3.59,-8 8,-8c2.21,0 4.2,0.9 5.65,2.35L15,9h7V2l-2.95,2.95C17.26,3.13 14.76,2 12,2zM14.5,11v-1c0,-1.38 -1.12,-2.5 -2.5,-2.5S9.5,8.62 9.5,10v1C8.950,11 8.5,11.45 8.5,12v3.5c0,0.55 0.45,1 1,1h5c0.55,0 1,-0.45 1,-1V12c0,-0.55 -0.45,-1 -1,-1zM13.3,11h-2.6v-1c0,-0.72 0.58,-1.3 1.3,-1.3s1.3,0.58 1.3,1.3v1z")
    val mirror = p("M2,5c0,-1.1 0.9,-2 2,-2h11c1.1,0 2,0.9 2,2v2h-2V5H4v8h2v2H4c-1.1,0 -2,-0.9 -2,-2V5zM9,11c0,-1.1 0.9,-2 2,-2h9c1.1,0 2,0.9 2,2v8c0,1.1 -0.9,2 -2,2h-9c-1.1,0 -2,-0.9 -2,-2v-8zM11,11v8h9v-8h-9z")
    val contrast = p("M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM12,20V4c4.41,0 8,3.59 8,8s-3.59,8 -8,8z")
    val battery = p("M4,7h14c1.1,0 2,0.9 2,2v6c0,1.1 -0.9,2 -2,2H4c-1.1,0 -2,-0.9 -2,-2V9c0,-1.1 0.9,-2 2,-2zM4,8.8c-0.11,0 -0.2,0.09 -0.2,0.2v6c0,0.11 0.09,0.2 0.2,0.2h14c0.11,0 0.2,-0.09 0.2,-0.2V9c0,-0.11 -0.09,-0.2 -0.2,-0.2H4zM5,10h5v4H5zM21,10h1v4h-1z")
    val timer = p("M12,3c-4.97,0 -9,4.03 -9,9s4.03,9 9,9 9,-4.03 9,-9h-2c0,3.87 -3.13,7 -7,7s-7,-3.13 -7,-7 3.13,-7 7,-7V3zM12,3v6h-0.01l-0.99,-0.01V3zM11.3,12.7l-3.6,-4.9 0.8,-0.6 4.9,3.6c0.5,0.4 0.6,1.1 0.2,1.6 -0.4,0.5 -1.1,0.6 -1.6,0.2 -0.3,-0.2 -0.5,-0.5 -0.7,-0.9z")
    val calculator = p("M6,2h12c1.1,0 2,0.9 2,2v16c0,1.1 -0.9,2 -2,2H6c-1.1,0 -2,-0.9 -2,-2V4c0,-1.1 0.9,-2 2,-2zM7,5v4h10V5H7zM7,11v2h2v-2H7zM11,11v2h2v-2h-2zM15,11v2h2v-2h-2zM7,15v2h2v-2H7zM11,15v2h2v-2h-2zM15,15v4h2v-4h-2z")
    val alarm = p("M22,5.72l-4.6,-3.86 -1.29,1.53 4.6,3.86L22,5.72zM7.88,3.39L6.6,1.86 2,5.71l1.29,1.53 4.59,-3.85zM12.5,8H11v6l4.75,2.85 0.75,-1.23 -4,-2.37V8zM12,4c-4.97,0 -9,4.03 -9,9s4.02,9 9,9c4.97,0 9,-4.03 9,-9s-4.03,-9 -9,-9zM12,20c-3.87,0 -7,-3.13 -7,-7s3.13,-7 7,-7 7,3.13 7,7 -3.13,7 -7,7z")
}

private val ORANGE = Color(0xFFFF9F0A)
private val GREEN = Color(0xFF30D158)
private val BLUE = Color(0xFF0A84FF)
private val RED = Color(0xFFFF453A)
private val YELLOW = Color(0xFFFFCC00)
private val PURPLE = Color(0xFF5E5CE6)
private const val SLIDER_FILL = 0.95f
private const val HAIRLINE = 0.16f
private const val ART_CORNER = 0.22f
private const val REFRESH_MS = 1000L
