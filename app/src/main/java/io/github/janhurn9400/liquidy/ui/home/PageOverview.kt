package io.github.janhurn9400.liquidy.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.zIndex
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.janhurn9400.liquidy.design.DesignTokens
import io.github.janhurn9400.liquidy.glass.LocalGlassBackdrop
import io.github.janhurn9400.liquidy.ui.widgets.IosWidget
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.RoundedRectangle
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Page overview like iOS 26/27 ("Edit Pages", screen recording 10-08 09:55, analysed):
 * 3 × n cards of thick Liquid Glass (strong refraction) with the shrunk page (without dock/labels, ≈ 0.24×),
 * below them a round glass circle — checkmark = visible, empty = hidden; hidden pages have a minus to delete them.
 * Open: icons disappear immediately, the current page shrinks as a card into its slot, the others fade in afterwards.
 * Close: the chosen card zooms back to full screen. Tap on a visible card → that page, "Done" → back.
 */
@Composable
fun PageOverview(model: HomeModel, geo: HomeGeometry, density: Float, wallpaper: ImageBitmap, w: Float, h: Float, onPage: (Int) -> Unit) {
    // Back gesture: close the overview (back into edit mode) — otherwise edit mode ended below it and
    // the overview stayed with ✓ above the clock
    androidx.activity.compose.BackHandler(enabled = model.pagesOverviewOpen) { model.closePagesOverview(onPage = onPage) }
    val pages = model.overviewPages()
    val n = pages.size
    val duo = geo.duo
    // iPhone: 3 columns, dimensions scaled to 402 pt. Duo [MEASURED video 3, 9 s]: 2 columns, cards 205 × 313 pt
    // (46.8 % of the height), 22 pt gap, rows 395 pt, first card top 82 pt, content × 0.492; scrollable.
    val cols = if (duo) 2 else 3
    val pt = { v: Float -> v * density * (w / density / REF_W) } // pt → px, scaled to the screen width (iPhone)
    val cardH = if (duo) h * DUO_CARD_H_SHARE else pt(CARD_H)
    val cardW = if (duo) minOf(cardH * DUO_CARD_W / DUO_CARD_H, (w - 3 * DUO_GAP * density) / 2) else pt(CARD_W)
    val cardHf = if (duo) cardW * DUO_CARD_H / DUO_CARD_W else cardH
    val colPitch = if (duo) cardW + DUO_GAP * density else pt(COL_PITCH)
    val rowPitch = if (duo) cardHf * DUO_ROW_PITCH / DUO_CARD_H else pt(ROW_PITCH)
    val cardCorner = if (duo) DUO_CARD_CORNER * density else pt(CARD_CORNER)
    val circleD = if (duo) CIRCLE_D * density else pt(CIRCLE_D)
    val circleGap = if (duo) DUO_CIRCLE_GAP * cardHf / DUO_CARD_H else pt(CIRCLE_GAP)
    val rows = ceil(n / cols.toFloat()).toInt().coerceAtLeast(1)
    val blockH = (rows - 1) * rowPitch + cardHf + circleGap + circleD / 2
    val top = if (duo) h * DUO_TOP_SHARE else (h * BLOCK_CENTER - blockH / 2).coerceAtLeast(pt(70f))
    val left = (w - ((cols - 1) * colPitch + cardW)) / 2
    // Duo: scroll vertically if there are more rows than space
    val maxScroll = if (duo) (top + blockH + h * 0.06f - h).coerceAtLeast(0f) else 0f
    val scroll = remember { Animatable(0f) }
    val scrollScope = rememberCoroutineScope()
    fun gridRect(i: Int) = Rect(Offset(left + (i % cols) * colPitch, top + (i / cols) * rowPitch - scroll.value), Size(cardW, cardHf))
    // Page region of the card (page coordinates, px) and its screen rect at original size
    val regionW = if (duo) cardW / DUO_CONTENT_SCALE else w
    val regionH = regionW * cardHf / cardW
    val regionLeft = if (duo) geo.duoFolderCenterX(0f) - regionW / 2 else 0f
    val regionTop = if (duo) (DUO_ROW0_TOP - DUO_REGION_ABOVE) * density
        else (DesignTokens.gridTop.value - REGION_ABOVE_GRID) * density // region top in px
    // Screen area of a page at original size: left half or (Duo unfolded) right half
    val full = Rect(Offset(regionLeft, regionTop), Size(regionW, regionH))
    val fullRight = full.translate(geo.pageShift(1f), 0f)
    val p = { model.pagesOverview.value }

    // Reordering: long press lifts the card, it follows the finger, the others make way springily
    var drag by remember { mutableStateOf<CardDrag?>(null) }
    fun slotOf(i: Int): Int {
        val d = drag ?: return i
        val order = (0 until n).toMutableList().apply { remove(d.from); add(d.target, d.from) }
        return order.indexOf(i)
    }
    fun nearestSlot(center: Offset): Int = (0 until n).minByOrNull { (gridRect(it).center - center).getDistance() } ?: 0

    Box(
        Modifier.fillMaxSize().then(if (maxScroll > 0f) Modifier.pointerInput(maxScroll) {
            detectVerticalDragGestures { ch, dy ->
                if (drag != null) return@detectVerticalDragGestures
                ch.consume(); scrollScope.launch { scroll.snapTo((scroll.value - dy).coerceIn(0f, maxScroll)) }
            }
        } else Modifier).pointerInput(pages) { // key = content: showing/hiding and reordering don't change n
            detectDragGesturesAfterLongPress(
                onDragStart = { pos ->
                    val i = (0 until n).firstOrNull { gridRect(it).contains(pos) } ?: return@detectDragGesturesAfterLongPress
                    drag = CardDrag(i, i, Offset.Zero)
                },
                onDrag = { ch, delta ->
                    ch.consume()
                    drag = drag?.let { d -> val o = d.offset + delta; d.copy(offset = o, target = nearestSlot(gridRect(d.from).center + o)) }
                },
                onDragEnd = { drag?.let { d -> if (d.target != d.from) model.movePage(d.from, d.target) }; drag = null },
                onDragCancel = { drag = null },
            )
        }.pointerInput(pages) {
            detectTapGestures { pos ->
                if (geo.editButtonRect(false).inflate(12f * density).contains(pos)) { model.closePagesOverview(onPage = onPage); return@detectTapGestures }
                for (i in 0 until n) {
                    val r = gridRect(i)
                    val circle = Offset(r.center.x, r.bottom + circleGap)
                    if ((pos - circle).getDistance() < circleD * 0.75f) { model.togglePage(i); return@detectTapGestures }
                    if (!pages[i].visible && (pos - r.topLeft).getDistance() < pt(22f)) { model.deleteHiddenPage(i); return@detectTapGestures }
                    if (r.contains(pos)) { if (pages[i].visible) model.closePagesOverview(i, onPage); return@detectTapGestures }
                }
            }
        }
    ) {
        val ids = remember(pages) { val seen = HashMap<String, Int>(); pages.map { pg -> val k = pg.items.joinToString(","); k + "#" + (seen.merge(k, 1, Int::plus)) } }
        pages.forEachIndexed { i, page -> key(ids[i]) {
            // "Focus" = cards of the pages visible on open/close (Duo unfolded: left AND right) — they grow
            // out of or into their screen half instead of just fading in
            val vis = model.overviewVisibleIndex(i)
            val half: Rect? = when {
                vis == null -> null
                vis == model.overviewLeftPage -> full
                geo.unfolded && vis == model.overviewLeftPage + 1 -> fullRight
                else -> null
            }
            val focus = half != null
            val dragged = drag?.from == i
            val home = gridRect(slotOf(i)).topLeft
            val pos = remember { Animatable(home, Offset.VectorConverter) }
            LaunchedEffect(home, dragged) { if (!dragged) pos.animateTo(home, io.github.janhurn9400.liquidy.motion.Motion.todayReflow.spec()) }
            if (dragged) LaunchedEffect(Unit) {
                androidx.compose.runtime.snapshotFlow { drag?.let { gridRect(it.from).topLeft + it.offset } }.collect { it?.let { o -> pos.snapTo(o) } }
            }
            val g = Rect(pos.value, Size(cardW, cardHf))
            // State per card: the focus card grows/shrinks with p; the others fade in delayed
            fun rect(): Rect = if (half != null) lerpRect(half, g, p()) else g
            if (half != null && vis != null) model.overviewZoom[vis] = { rect() to half }
            // Focus card ↔ real home screen [MEASURED Duo 19:05:45]: the card only fades out once the home screen below is already
            // fully there (HomeScreen.overviewHide) — so there's neither a hard cut nor a half-dark in-between frame
            fun appear(): Float = if (focus) ((p() - FOCUS_FADE_END) / (FOCUS_FADE_START - FOCUS_FADE_END)).coerceIn(0f, 1f) else ((p() - OTHERS_FROM) / (1f - OTHERS_FROM)).coerceIn(0f, 1f)
            // Focus card: glass only once it's clearly smaller — at full screen the lens must not magnify anything
            // (otherwise it refracts the returning real icons hugely)
            fun glass(): Float = if (focus) ((p() - FOCUS_GLASS_FROM) / (1f - FOCUS_GLASS_FROM)).coerceIn(0f, 1f) else p().coerceIn(0f, 1f)
            Box(
                Modifier
                    .offset { rect().topLeft.let { IntOffset(it.x.roundToInt(), it.y.roundToInt()) } }
                    .zIndex(if (dragged) 2f else 0f)
                    .graphicsLayer {
                        val a = appear(); alpha = a
                        val s = (if (focus) 1f else 0.92f + 0.08f * a) * (if (dragged) DRAG_LIFT else 1f); scaleX = s; scaleY = s
                    }
                    .size(Dp(g.width / density), Dp(g.height / density))
                    .graphicsLayer {
                        // Size via scaling (the focus card is screen-sized on open)
                        val r = rect(); scaleX = r.width / g.width; scaleY = r.height / g.height
                        transformOrigin = TransformOrigin(0f, 0f)
                    }
            ) {
                PageCardGlass(Modifier.fillMaxSize(), corner = cardCorner / density, strength = { glass() })
                // Page shrunk (without labels, Android widgets as placeholders)
                Box(
                    Modifier.fillMaxSize().clip(RoundedRectangle(Dp(cardCorner / density)))
                        .wrapContentSize(Alignment.TopStart, unbounded = true)
                        .requiredSize(Dp(regionW / density), Dp(regionH / density))
                        .graphicsLayer { val s = g.width / regionW; scaleX = s; scaleY = s; transformOrigin = TransformOrigin(0f, 0f) }
                ) { PageThumbnail(model, geo, page.items, wallpaper, density, regionTop, regionLeft) }
            }
            // Selection circle + minus (fade in with the cards)
            val circleA = { ((p() - CIRCLES_FROM) / (1f - CIRCLES_FROM)).coerceIn(0f, 1f) }
            Box(
                Modifier
                    .offset { IntOffset((g.center.x - circleD / 2).roundToInt(), (g.bottom + circleGap - circleD / 2).roundToInt()) }
                    .size(Dp(circleD / density))
                    .graphicsLayer { val a = circleA() * (if (dragged) 0f else 1f); alpha = a; scaleX = 0.8f + 0.2f * a; scaleY = 0.8f + 0.2f * a }
            ) {
                PageCardGlass(Modifier.fillMaxSize(), corner = circleD / 2 / density, strength = { 1f })
                if (page.visible) Canvas(Modifier.fillMaxSize()) {
                    val s = size.width; val sw = s * 0.09f
                    drawLine(Color.White, Offset(s * 0.3f, s * 0.52f), Offset(s * 0.44f, s * 0.67f), sw, StrokeCap.Round)
                    drawLine(Color.White, Offset(s * 0.44f, s * 0.67f), Offset(s * 0.71f, s * 0.35f), sw, StrokeCap.Round)
                }
            }
            if (!page.visible) MinusBadge(wallpaper, Modifier
                .offset { IntOffset((g.left - DesignTokens.badgeSize.value * density / 2 + pt(4f)).roundToInt(), (g.top - DesignTokens.badgeSize.value * density / 2 + pt(4f)).roundToInt()) }
                .graphicsLayer { val a = circleA(); alpha = a; scaleX = a; scaleY = a })
        } }
    }
}

/** Thick Liquid Glass of the cards (strong refraction over the whole area, like in the video); [strength] 0…1 fades it out. */
@Composable
private fun PageCardGlass(modifier: Modifier, corner: Float, strength: () -> Float) {
    // Don't draw at all without strength: the glass surface would otherwise paint the (wrongly scaled) background image,
    // in which the real icons reappear when closing → huge ghost images
    val on by androidx.compose.runtime.remember { androidx.compose.runtime.derivedStateOf { strength() > 0.01f } }
    if (!on) return
    val backdrop = LocalGlassBackdrop.current
    if (backdrop == null) { Box(modifier.clip(RoundedRectangle(corner.dp)).background(Color.White.copy(alpha = 0.15f))); return }
    Box(
        modifier.graphicsLayer { alpha = strength().coerceIn(0f, 1f) }.drawBackdrop(
            backdrop = backdrop,
            shape = { RoundedRectangle(corner.dp) },
            effects = {
                val k = strength()
                if (k > 0.01f) {
                    blur(1.5.dp.toPx() * k)
                    val m = size.minDimension
                    val r = minOf(corner.dp.toPx(), m / 2)
                    lens(refractionHeight = r * 0.95f * k, refractionAmount = m * 0.55f * k, depthEffect = true, chromaticAberration = true)
                }
            },
            highlight = { Highlight(width = 1.2.dp, alpha = 0.65f * strength(), style = HighlightStyle.Default(angle = 45f, falloff = 2f)) },
            shadow = { Shadow(radius = 18.dp, color = Color.Black.copy(alpha = 0.14f * strength())) },
            onDrawSurface = { drawRect(Color.White.copy(alpha = 0.06f * strength())) },
        )
    )
}

/** Content of a page at original size (scaled outside): icons without labels, folders, widgets. */
@Composable
private fun PageThumbnail(model: HomeModel, geo: HomeGeometry, items: List<String>, wallpaper: ImageBitmap, density: Float, regionTop: Float, regionLeft: Float = 0f) {
    // Gaps (free placement) determine the cells, but aren't drawn
    val cells = GridPlacement.placeReal(items, model::sizeOf)
    Box(Modifier.fillMaxSize()) {
        GridPlacement.stripGaps(items).forEachIndexed { slot, key ->
            val cell = cells.getOrNull(slot) ?: -1
            if (cell < 0) return@forEachIndexed
            val tl = geo.slotPosition(cell) - Offset(regionLeft, regionTop)
            val spec = model.widgets[key]
            if (spec != null) {
                val r = geo.widgetRect(tl, spec.cols, spec.rows)
                Box(
                    Modifier.offset { IntOffset(r.left.roundToInt(), r.top.roundToInt()) }
                        .size(Dp(r.width / density), Dp(r.height / density))
                        .clip(io.github.janhurn9400.liquidy.glass.glassRect(DesignTokens.widgetCorner.dp))
                ) {
                    // Stack: the widget currently on top (otherwise a dark area that jumps into the real widget at the end)
                    val shown = model.shownWidget(key) ?: spec
                    // Android widgets have only one view (already running on the real page) → placeholder
                    if (shown.type == "android") Box(Modifier.fillMaxSize().background(Color(0xFF1C1C1E)))
                    else WithWallpaperGlass(wallpaper) { IosWidget(shown.type, shown.cols, shown.rows, Modifier.fillMaxSize(), shown.config) }
                }
            } else {
                val iconPx = DesignTokens.iconSize.value * density
                Box(Modifier.offset { IntOffset(tl.x.roundToInt(), tl.y.roundToInt()) }.size(Dp(iconPx / density))) {
                    val folder = model.folders[key]
                    when {
                        folder != null -> FolderIconView(key, folder, model.apps, showLabel = false, wallpaper = wallpaper,
                            pressAlpha = { 0f }, jiggle = { 0f }, clockSeconds = { 0f }, mergeHint = false)
                        model.apps[key] != null -> Box(Modifier.fillMaxSize().clip(io.github.janhurn9400.liquidy.design.ContinuousRoundedShape(DesignTokens.iconCorner.dp))) {
                            StyledIcon(model.apps.getValue(key), wallpaper, Modifier.fillMaxSize())
                        }
                    }
                }
            }
        }
    }
}

private fun lerpRect(a: Rect, b: Rect, t: Float) = Rect(
    a.left + (b.left - a.left) * t, a.top + (b.top - a.top) * t,
    a.right + (b.right - a.right) * t, a.bottom + (b.bottom - a.bottom) * t,
)

// Dimensions [MEASURED] recording 10-08 09:55 frame 460 (3×, iPhone 402 pt wide)
private const val REF_W = 402f
private const val DUO_CARD_W = 205f
private const val DUO_CARD_H = 313f
private const val DUO_CARD_H_SHARE = 0.468f   // 313 / 669
private const val DUO_GAP = 22f
private const val DUO_ROW_PITCH = 395f
private const val DUO_TOP_SHARE = 0.1226f     // 82 / 669
private const val DUO_CARD_CORNER = 28f
private const val DUO_CIRCLE_GAP = 32f        // circle centre below the card bottom (with a 313 pt card)
private const val DUO_CONTENT_SCALE = 0.492f  // icon 29.5 pt instead of 60
private const val DUO_ROW0_TOP = 53.6f        // top of the first icon row (centre 83.6 − 30)
private const val DUO_REGION_ABOVE = 40.6f    // region starts this far above (20 pt in the card)
private const val CARD_W = 96f
private const val CARD_H = 165f
private const val CARD_CORNER = 13f
private const val COL_PITCH = 122f
private const val ROW_PITCH = 246f
private const val CIRCLE_D = 36f
private const val CIRCLE_GAP = 30f       // circle centre below the card bottom
private const val BLOCK_CENTER = 0.553f  // grid centre at 55 % of the height
private const val REGION_ABOVE_GRID = 44f // the card's page region starts 44 pt above the first row
private const val OTHERS_FROM = 0.45f    // other cards appear from this progress
private const val CIRCLES_FROM = 0.6f
private const val DRAG_LIFT = 1.06f

private data class CardDrag(val from: Int, val target: Int, val offset: Offset)
private const val FOCUS_GLASS_FROM = 0.3f

/** The focus card fades out between these progress values (home screen fully there from 0.15, Duo: card gone after ≈ 260 ms). */
private const val FOCUS_FADE_START = 0.15f
private const val FOCUS_FADE_END = 0.02f
