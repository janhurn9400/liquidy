package io.github.janhurn9400.liquidy.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.animation.core.VectorConverter
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import io.github.janhurn9400.liquidy.design.DesignTokens
import io.github.janhurn9400.liquidy.glass.glassRect
import io.github.janhurn9400.liquidy.ui.widgets.AndroidWidget
import io.github.janhurn9400.liquidy.ui.widgets.IosWidget
import kotlin.math.roundToInt

/**
 * Today view (recording8 2–20 s, T-TODAY): widgets without labels one below the other on the blurred wallpaper,
 * same sizes as on the home screen, 2 columns (small) or full width, margin 26.33 pt, gap 21.33 pt, top ≈ 91.67 pt.
 */
object TodayLayout {
    /** Rects (content coordinates in px, before scroll) per widget key, in order. */
    fun place(keys: List<String>, size: (String) -> Pair<Int, Int>, geo: HomeGeometry, density: Float): List<Pair<String, Rect>> {
        val used = HashMap<Int, BooleanArray>() // row → 2 columns
        fun row(r: Int) = used.getOrPut(r) { BooleanArray(2) }
        // Duo: Today is one page (448 pt), column 333 pt at 57.5 pt, first row at 54.5 pt; row gap incl.
        // label 40 pt [MEASURED] recording 10-08 21:28, 31 s. Otherwise iPhone values (recording8).
        val small = geo.widgetSize(2, 2).width / density
        val gap = if (geo.duo) DUO_TODAY_ROW_GAP else DesignTokens.todayGap.value
        val colGap = if (geo.duo) geo.widgetSize(4, 2).width / density - 2 * small else DesignTokens.todayGap.value
        val unit = (geo.widgetSize(2, 2).height / density + gap) * density
        // folded: centred in the space left of the dock (otherwise the wide widget reaches into the dock) [MEASURED 19:07, ≈ 25 pt margin]
        val avail = if (geo.unfolded) DUO_TODAY_PAGE else geo.duoDockLeftPt
        val side = (if (geo.duo) (avail - geo.widgetSize(4, 2).width / density) / 2 else DesignTokens.todaySide.value) * density
        val colX = floatArrayOf(side, side + (small + colGap) * density)
        val top = (if (geo.duo) DUO_TODAY_TOP else DesignTokens.todayTop.value) * density
        return keys.map { k ->
            val (c, r) = size(k)
            val w = if (c >= 4) 2 else 1
            val h = if (r >= 4) 2 else 1
            var placed: Rect? = null
            var y = 0
            while (placed == null) {
                for (x in 0..(2 - w)) {
                    if ((y until y + h).all { yy -> (x until x + w).none { xx -> row(yy)[xx] } }) {
                        for (yy in y until y + h) for (xx in x until x + w) row(yy)[xx] = true
                        placed = Rect(Offset(colX[x], top + y * unit), geo.widgetSize(c, r))
                        break
                    }
                }
                y++
            }
            k to placed!!
        }
    }

    /** Duo unfolded: at the bottom the column ends at the glass surface (41 pt above the edge) — scrolled to the very bottom the
     *  last label and below it ≈ as much glass as at the top (14 pt) should stay visible. */
    fun contentHeight(rects: List<Pair<String, Rect>>, density: Float, duoUnfolded: Boolean = false) =
        (rects.maxOfOrNull { it.second.bottom } ?: 0f) +
            (if (duoUnfolded) TODAY_PANEL_BOTTOM + DUO_TODAY_LABEL_ROOM + DUO_TODAY_TOP - TODAY_PANEL_TOP else DesignTokens.todayBottom.value) * density
}

@Composable
fun TodayPage(model: HomeModel, geo: HomeGeometry, density: Float, wallpaper: androidx.compose.ui.graphics.ImageBitmap,
              clockSeconds: () -> Float = { 0f }, pageOffsetX: () -> Float) {
    val rects = TodayLayout.place(model.todayOrder(), model::sizeOf, geo, density)
    val drag = model.todayDrag
    val shape = glassRect(DesignTokens.widgetCorner.dp)
    // Duo unfolded: NOTHING is blurred below the Today page — the wallpaper stays sharp [MEASURED 19:18, 2.6 s].
    // Folded: like iPhone — the screen-wide blur from HomeScreen (sideBlur) takes over.
    val panel = if (geo.duo && geo.unfolded) rememberTodayPanel(model) else null
    val panelClip = if (panel != null && rects.isNotEmpty()) todayPanelRect(rects, geo, density, 1f) else null
    if (panel != null) TodayScrollPanel(model, geo, density, rects, pageOffsetX, panel)
    // Otherwise every widget clips itself (see below). In edit mode the widgets run to the display edge; only while
    // the glass surface is there does everything end at its edge [MEASURED 14:57, 14.0 s]
    Box(Modifier.fillMaxSize().then(if (panel == null) Modifier else Modifier
        .graphicsLayer {
            compositingStrategy = if (model.editing && panel.value > 0.001f) androidx.compose.ui.graphics.CompositingStrategy.Offscreen
            else androidx.compose.ui.graphics.CompositingStrategy.Auto
        }
        .drawWithContent {
            drawContent()
            val k = panel.value.coerceIn(0f, 1f)
            if (!model.editing || k <= 0.001f || rects.isEmpty()) return@drawWithContent
            // Clip edge always at the finished surface — if it grew along, a band would run over the widgets when closing
            val r = todayPanelRect(rects, geo, density, 1f).translate(pageOffsetX(), 0f)
            val outside = androidx.compose.ui.graphics.Path().apply {
                fillType = androidx.compose.ui.graphics.PathFillType.EvenOdd
                addRect(Rect(Offset.Zero, size))
                addRoundRect(androidx.compose.ui.geometry.RoundRect(r, androidx.compose.ui.geometry.CornerRadius(TODAY_PANEL_CORNER * density)))
            }
            drawPath(outside, Color.Black.copy(alpha = k), blendMode = androidx.compose.ui.graphics.BlendMode.DstOut)
        })) {
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationX = pageOffsetX()
                translationY = -model.todayScroll.value
            }
    ) {
        // key(): state (position Animatable) is tied to the widget, not the list slot — otherwise after
        // deleting all following widgets jump hard instead of springing in
        rects.forEach { (key, r) -> androidx.compose.runtime.key(key) {
            val spec = model.widgets[key] ?: return@forEach
            if (io.github.janhurn9400.liquidy.data.AppLaunchTransition.launchingKey.value == key) return@forEach // the app is growing out of it right now
            if (model.contextMenu?.key == key) return@forEach // floats in the context menu
            if (model.flyingKey == key || model.widgetConfigKey == key) return@forEach // the app flies in, the widget's image stands in its place
            val phases = androidx.compose.runtime.remember(key) { jigglePhases(key) }
            val dragged = drag?.key == key
            // New slot (another widget grows/moves) → glide there springily instead of jumping
            val pos = androidx.compose.runtime.remember(key) { androidx.compose.animation.core.Animatable(r.topLeft, androidx.compose.ui.geometry.Offset.VectorConverter) }
            // remember the visible position while dragging → spring in from there after release (no jump)
            val dropFrom = androidx.compose.runtime.remember(key) { androidx.compose.runtime.mutableStateOf<androidx.compose.ui.geometry.Offset?>(null) }
            if (dragged) {
                val from = (TodayLayout.place(model.today, model::sizeOf, geo, density).firstOrNull { it.first == key }?.second ?: r)
                dropFrom.value = from.topLeft + drag!!.offset
            }
            androidx.compose.runtime.LaunchedEffect(r.topLeft, dragged) {
                if (dragged) return@LaunchedEffect // follows the finger, own offset below
                dropFrom.value?.let { pos.snapTo(it); dropFrom.value = null }
                pos.animateTo(r.topLeft, io.github.janhurn9400.liquidy.motion.Motion.todayReflow.spec())
            }
            Box(
                Modifier
                    // touched widget (resize / drag) lies above the ones making way
                    .zIndex(if (dragged || model.resize?.key == key || model.todayLiftKey == key) 1f else 0f)
                    // first frame after release: still at the finger position (dropFrom) until the spring has taken over —
                    // otherwise the widget flashed for one frame at its target slot
                    .offset { val p = if (dragged) r.topLeft else dropFrom.value ?: pos.value; IntOffset(p.x.roundToInt(), p.y.roundToInt()) }
                    .graphicsLayer {
                        if (dragged) {
                            // dragged widget follows the finger (from its original slot), lifted like a dragged icon (T-DRAG.scale)
                            val from = (TodayLayout.place(model.today, model::sizeOf, geo, density).firstOrNull { it.first == key }?.second ?: r)
                            translationX = from.left - r.left + drag!!.offset.x
                            translationY = from.top - r.top + drag.offset.y
                            val lift = model.todayLift.value; scaleX = lift; scaleY = lift
                        } else {
                            applyJiggle(model.jiggleAmount.value * WIDGET_JIGGLE_TODAY, clockSeconds(), phases)
                            // just dropped: springs back from the lifted size
                            if (model.todayLiftKey == key) { val l = model.todayLift.value; scaleX *= l; scaleY *= l }
                        }
                    }
                    // Duo unfolded: clipped at the top and bottom at the glass surface (even if it's hidden) — the
                    // clip edge gets the widget corners again, not the shape of the surface [Duo, 14:57: 30.6 / 36.2 s]
                    .drawWithContent {
                        val clip = panelClip
                        if (clip == null || dragged || model.editing) { drawContent(); return@drawWithContent }
                        val y0 = pos.value.y - model.todayScroll.value
                        val ct = clip.top - y0; val cb = clip.bottom - y0
                        val w = size.width; val h = size.height
                        if (ct <= 0f && cb >= h) {
                            // widget fully inside — only the label below can reach over the edge
                            clipRect(-w, -100_000f, 2 * w, cb) { this@drawWithContent.drawContent() }
                            return@drawWithContent
                        }
                        val rad = DesignTokens.widgetCorner.value * density
                        val keep = androidx.compose.ui.graphics.Path().apply {
                            val t = maxOf(0f, ct); val b = minOf(h, cb)
                            if (b > t) addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, t, w, b, androidx.compose.ui.geometry.CornerRadius(rad)))
                            val lt = maxOf(h, ct)
                            if (cb > lt) addRect(Rect(-w, lt, 2 * w, cb)) // label below
                        }
                        clipPath(keep) { this@drawWithContent.drawContent() }
                    }
            ) {
                // Live rect while resizing (follows the finger, snaps), otherwise the slot from the layout
                val resize = model.resize?.takeIf { it.key == key }
                Box(
                    Modifier
                        .layout { m, _ ->
                            val w = (resize?.w?.value ?: r.width).toInt().coerceAtLeast(1)
                            val h = (resize?.h?.value ?: r.height).toInt().coerceAtLeast(1)
                            val p = m.measure(androidx.compose.ui.unit.Constraints.fixed(w, h))
                            layout(w, h) { p.place(0, 0) }
                        }
                        .clip(shape)
                ) {
                    Box(Modifier.fillMaxSize().graphicsLayer {
                        val b = (resize?.blur?.value ?: 0f) * DesignTokens.widgetResizeBlur.value * density
                        renderEffect = if (b > 0.5f) androidx.compose.ui.graphics.BlurEffect(b, b, androidx.compose.ui.graphics.TileMode.Clamp) else null
                    }) {
                        if (spec.type == "android") AndroidWidget(spec, Modifier.fillMaxSize())
                        else WithWallpaperGlass(wallpaper) { IosWidget(spec.type, spec.cols, spec.rows, Modifier.fillMaxSize(), spec.config) }
                    }
                }
                // Duo: name below the widget (like on the home screen; the iPhone Today page has none) [MEASURED] 31 s
                if (geo.duo) androidx.compose.foundation.text.BasicText(
                    io.github.janhurn9400.liquidy.ui.widgets.widgetLabel(model.shownWidget(key) ?: spec),
                    style = androidx.compose.ui.text.TextStyle(fontFamily = io.github.janhurn9400.liquidy.ui.Inter,
                        fontSize = androidx.compose.ui.unit.TextUnit(DesignTokens.labelFontSize.value, androidx.compose.ui.unit.TextUnitType.Sp),
                        color = DesignTokens.labelColor, shadow = DesignTokens.labelShadow, textAlign = androidx.compose.ui.text.style.TextAlign.Center),
                    maxLines = 1,
                    modifier = Modifier.offset { IntOffset(0, (r.height + DUO_TODAY_LABEL_GAP * density).roundToInt()) }
                        .size(Dp(r.width / density), Dp(20f)),
                )
                // Resize handle bottom right on the (live) rect
                if (model.editing || model.badgeScale.value > 0f) ResizeHandle(
                    Modifier
                        .offset {
                            val side = (handleBox() * density).roundToInt(); val out = (handleOutset() * density).roundToInt()
                            IntOffset((resize?.w?.value ?: r.width).roundToInt() - side + out, (resize?.h?.value ?: r.height).roundToInt() - side + out)
                        }
                        .systemGestureExclusion()
                        .graphicsLayer { alpha = model.badgeScale.value.coerceIn(0f, 1f) }
                )
                // Minus badge top left (edit mode)
                if (model.editing || model.badgeScale.value > 0f) MinusBadge(wallpaper, Modifier
                    .offset { IntOffset((DesignTokens.badgeOffsetX.value * density).roundToInt() - (DesignTokens.badgeSize.value * density / 2).roundToInt(),
                        (DesignTokens.badgeOffsetY.value * density).roundToInt() - (DesignTokens.badgeSize.value * density / 2).roundToInt()) }
                    .graphicsLayer { val s = model.badgeScale.value; scaleX = s; scaleY = s })
            }
        }
    }}
    }
}

/** 0 = no glass surface, 1 = fully there. Comes with the finger, goes as soon as the column stands still. */
@Composable
private fun rememberTodayPanel(model: HomeModel): androidx.compose.animation.core.Animatable<Float, androidx.compose.animation.core.AnimationVector1D> {
    val shown = remember { androidx.compose.animation.core.Animatable(0f) }
    val active = model.todayTouching || model.todayScroll.isRunning
    androidx.compose.runtime.LaunchedEffect(active) {
        // [MEASURED 14:57, 27.48–27.70 s / 35.58–35.67 s, 60 fps]: open ≈ 0.22 s (fast, then gentle), close ≈ 0.1 s
        if (active) shown.animateTo(1f, androidx.compose.animation.core.tween(TODAY_PANEL_IN_MS, easing = androidx.compose.animation.core.LinearOutSlowInEasing))
        else shown.animateTo(0f, androidx.compose.animation.core.tween(TODAY_PANEL_OUT_MS, easing = androidx.compose.animation.core.FastOutLinearInEasing))
    }
    return shown
}

/**
 * Glass surface (screen coordinates without page offset) at progress [k]: fixed to the screen, 13 pt wider than the
 * column on each side, 40 pt gap at the top, 41 pt at the bottom (measured at the straight edges, 30.6/36.2 s) —
 * scrolled to the very top ≈ 14 pt of glass stays above the topmost widget. Grows from the centre — barely horizontally (≈ 3.6 pt each),
 * clearly vertically (≈ 17 pt each) [MEASURED 27.50 → 27.70 s: left 256 → 251 px, top 278 → 256, bottom 970 → 994].
 */
private fun todayPanelRect(rects: List<Pair<String, Rect>>, geo: HomeGeometry, density: Float, k: Float): Rect {
    val colLeft = rects.minOf { it.second.left }; val colRight = rects.maxOf { it.second.right }
    val top = (TODAY_PANEL_TOP * density).coerceAtMost(geo.height * 0.25f)
    val bottom = (geo.height - TODAY_PANEL_BOTTOM * density).coerceAtLeast(top + 1f)
    val inv = 1f - k.coerceIn(0f, 1f)
    val dx = inv * TODAY_PANEL_GROW_X * density; val dy = inv * TODAY_PANEL_GROW_Y * density
    return Rect(colLeft - TODAY_PANEL_SIDE * density + dx, top + dy, colRight + TODAY_PANEL_SIDE * density - dx, bottom - dy)
}

/**
 * Duo unfolded, while scrolling the Today column [MEASURED recording 10-11 14:57]: clear glass surface behind the widgets
 * (corners ≈ 40 pt, wallpaper inside ≈ +28 brighter, sharp), grows and fades in at the same time. To the right (8.75 pt) a
 * white scroll bar (2.9 pt) in a pale track ≈ 98.5 pt long, centred to the surface — only if the column is longer than the screen.
 */
@Composable
private fun TodayScrollPanel(model: HomeModel, geo: HomeGeometry, density: Float, rects: List<Pair<String, Rect>>, pageOffsetX: () -> Float,
                             shown: androidx.compose.animation.core.Animatable<Float, androidx.compose.animation.core.AnimationVector1D>) {
    if (shown.value <= 0.001f || rects.isEmpty()) return
    val k = shown.value
    val r = todayPanelRect(rects, geo, density, k)
    Box(Modifier.fillMaxSize().graphicsLayer { translationX = pageOffsetX(); alpha = k.coerceIn(0f, 1f) }) {
        io.github.janhurn9400.liquidy.glass.LiquidGlass(
            glassRect(TODAY_PANEL_CORNER.dp),
            Modifier.offset { IntOffset(r.left.roundToInt(), r.top.roundToInt()) }
                .size((r.width / density).dp, (r.height / density).dp),
            blur = 0.dp, whiteTint = TODAY_PANEL_WHITE, blackTint = 0f,
        )
        val content = TodayLayout.contentHeight(rects, density, duoUnfolded = true)
        val hPx = geo.height
        if (content > hPx + 1f) androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val full = todayPanelRect(rects, geo, density, 1f)
            val track = (TODAY_BAR_TRACK * density).coerceAtMost(full.height)
            val maxScroll = content - hPx
            val scroll = model.todayScroll.value
            // beyond the ends (rubber band) the bar gets shorter [MEASURED 33.6 s: 117 → 88 px]
            val over = when { scroll < 0f -> -scroll; scroll > maxScroll -> scroll - maxScroll; else -> 0f }
            val thumb = (track * hPx / content - over * TODAY_BAR_SQUASH).coerceIn(TODAY_BAR_MIN * density, track)
            val frac = (scroll / maxScroll).coerceIn(0f, 1f)
            val top = full.center.y - track / 2
            val y = top + (track - thumb) * frac
            val x = full.right + TODAY_BAR_GAP * density
            // pale track, the white bar inside; straight ends [MEASURED 30.6 s, zoom]
            drawLine(Color.White.copy(alpha = TODAY_BAR_TRACK_ALPHA), Offset(x, top), Offset(x, top + track), TODAY_BAR_W * density)
            drawLine(Color.White, Offset(x, y), Offset(x, y + thumb), TODAY_BAR_W * density)
        }
    }
}

private const val TODAY_PANEL_SIDE = 13f
private const val TODAY_PANEL_TOP = 40f
private const val TODAY_PANEL_BOTTOM = 41f
private const val TODAY_PANEL_CORNER = 40f
private const val TODAY_PANEL_WHITE = 0.13f
private const val TODAY_PANEL_GROW_X = 3.6f
private const val TODAY_PANEL_GROW_Y = 17f
private const val TODAY_PANEL_IN_MS = 220
private const val TODAY_PANEL_OUT_MS = 110
private const val TODAY_BAR_GAP = 9.1f
private const val TODAY_BAR_W = 2.9f
private const val TODAY_BAR_TRACK = 98.5f
private const val TODAY_BAR_MIN = 20f
private const val TODAY_BAR_SQUASH = 0.5f
private const val TODAY_BAR_TRACK_ALPHA = 0.22f
private const val WIDGET_JIGGLE_TODAY = 0.35f
private const val DUO_TODAY_PAGE = 448f
private const val DUO_TODAY_TOP = 54.5f
private const val DUO_TODAY_ROW_GAP = 40f
private const val DUO_TODAY_LABEL_GAP = 7f
private const val DUO_TODAY_LABEL_ROOM = 24f // gap + label below the widget
private const val DUO_TODAY_BLUR = 12f
private const val DUO_TODAY_FADE = 60f // pt soft transition at the right page edge

/** Index in the Today list where a dragged widget with centre [center] (content coordinates) belongs. */
fun todayTargetIndex(model: HomeModel, key: String, center: androidx.compose.ui.geometry.Offset, geo: HomeGeometry, density: Float): Int {
    val rest = model.today - key
    if (rest.isEmpty()) return 0
    val rects = TodayLayout.place(rest, model::sizeOf, geo, density)
    // before the first widget whose centre lies "after" the finger (row by row)
    val i = rects.indexOfFirst { (_, r) -> center.y < r.top + r.height / 2 && (center.y < r.top || center.x < r.right) }
    return if (i < 0) rest.size else i
}
