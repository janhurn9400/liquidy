package io.github.janhurn9400.liquidy.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import io.github.janhurn9400.liquidy.R
import io.github.janhurn9400.liquidy.design.DesignTokens
import io.github.janhurn9400.liquidy.glass.LiquidGlass
import io.github.janhurn9400.liquidy.glass.glassRect
import io.github.janhurn9400.liquidy.ui.Inter
import io.github.janhurn9400.liquidy.ui.widgets.AndroidWidget
import io.github.janhurn9400.liquidy.ui.widgets.IosWidget
import io.github.janhurn9400.liquidy.ui.widgets.widgetLabel
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * "Edit Stack" like iOS 26/27 (screen recording 10-08 10:21, analysed): home screen blurred, in the middle a
 * large Liquid Glass panel with the vertical list of the stack widgets (name below, minus top left, neighbours peek
 * out at the top/bottom), dots on the right, at the bottom two blue round buttons "Smart Rotate" / "Widget Suggestions", at the top "+" and
 * "✓". Swiping pages, long press + drag reorders or puts the widget onto the home screen outside the panel.
 */
@Composable
fun StackEditor(model: HomeModel, key: String, geo: HomeGeometry, density: Float, wallpaper: ImageBitmap, w: Float, h: Float) {
    val spec = model.widgets[key] ?: return
    val items = spec.stack
    if (items.isEmpty()) return
    val unit = w / REF_W                                     // px per reference px (603 px wide recording)
    val ws = geo.widgetSize(spec.cols, spec.rows)
    val margin = minOf(PANEL_MARGIN * unit, (w - ws.width) / 2 - 12f * density).coerceAtLeast(10f * density)
    val panel = Rect(Offset((w - ws.width) / 2 - margin, h * PANEL_CENTER_Y - ws.height / 2 - PANEL_MARGIN * unit),
        Size(ws.width + 2 * margin, ws.height + 2 * PANEL_MARGIN * unit))
    val pitch = ITEM_PITCH * unit
    val pos = model.stackPosition(key)
    // Morph (frame 288–302): t = 0 → the panel covers exactly the stack on the home screen, t = 1 → large panel in the middle.
    // The spring may overshoot slightly (the panel gets a bit larger briefly) — so don't clamp t.
    val t = { model.stackEdit.value }
    val from = model.stackEditFrom ?: Rect(Offset((w - ws.width) / 2, h * PANEL_CENTER_Y - ws.height / 2), ws)
    val target = panel
    fun panelNow(): Rect = lerpR(from, target, t())
    fun itemRect(i: Int): Rect = Rect(Offset(panel.center.x - ws.width / 2, panel.center.y - ws.height / 2 + (i - pos.value) * pitch), ws)
    /** List slot → current slot: the current widget flies from the stack slot into the list, the others hang on it. */
    fun morph(r: Rect): Rect {
        val cur = itemRect(pos.value.roundToInt())
        val home = Rect(from.center - Offset(ws.width / 2, ws.height / 2), ws)
        return r.translate(lerpR(home, cur, t()).topLeft - cur.topLeft)
    }
    val appear = { ((t() - CHROME_FROM) / (1f - CHROME_FROM)).coerceIn(0f, 1f) }   // buttons, dots, names: last
    var drag by remember { mutableStateOf<Pair<Int, Offset>?>(null) } // dragged widget + finger travel
    val tracker = remember { VelocityTracker() }


    Box(
        Modifier.fillMaxSize()
            .pointerInput(key, items.size) {
                detectTapGestures { p ->
                    if (geo.editButtonRect(false).inflate(12f * density).contains(p)) { model.closeStackEditor(); return@detectTapGestures }
                    if (geo.editButtonRect(true).inflate(12f * density).contains(p)) { model.addingToStack = true; model.widgetPickerOpen = true; return@detectTapGestures }
                    for (i in items.indices) {
                        val c = itemRect(i).topLeft + Offset(DesignTokens.badgeOffsetX.value * density, DesignTokens.badgeOffsetY.value * density)
                        if ((p - c).getDistance() < geo.badgeHitRadius && panel.contains(c)) { model.stackRemoveAsk = i; return@detectTapGestures }
                    }
                    for ((k, cx) in listOf(true to BTN_LEFT_X, false to BTN_RIGHT_X)) {
                        if ((p - Offset(cx * w, BTN_Y * h)).getDistance() < BTN_D * unit * 0.7f) { model.toggleStackOption(rotate = k); return@detectTapGestures }
                    }
                    if (!panel.contains(p)) model.closeStackEditor()
                }
            }
            .pointerInput(key, items.size) {
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f; tracker.resetTracking(); model.stackDragStart(key) },
                    onDragEnd = { model.stackDragEnd(key, tracker.calculateVelocity().y, pitch) },
                    onDragCancel = { model.stackDragEnd(key, 0f, pitch) },
                ) { ch, dy ->
                    ch.consume(); total += dy
                    tracker.addPosition(ch.uptimeMillis, ch.position)
                    model.stackDrag(key, total, pitch)
                }
            }
            .pointerInput(key, items.size) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { p -> val i = pos.value.roundToInt(); if (itemRect(i).contains(p)) drag = i to Offset.Zero },
                    onDrag = { ch, d -> ch.consume(); drag = drag?.let { (i, o) -> i to (o + d) } },
                    onDragEnd = {
                        drag?.let { (i, o) ->
                            val center = itemRect(i).center + o
                            if (!panel.contains(center)) model.stackDetach(key, i) // out of the panel → onto the home screen
                            else model.stackMove(key, i, (i + (o.y / pitch).roundToInt()).coerceIn(0, items.lastIndex))
                        }
                        drag = null
                    },
                    onDragCancel = { drag = null },
                )
            }
    ) {
        // Panel of Liquid Glass (refracts the blurred home screen)
        val cornerFrom = DesignTokens.widgetCorner.value * density
        fun corner() = cornerFrom + (PANEL_CORNER * unit - cornerFrom) * t().coerceIn(0f, 1f)
        LiquidGlass(glassRect((corner() / density).dp),
            Modifier.layout { m, c ->
                val r = panelNow(); val pl = m.measure(Constraints.fixed(r.width.roundToInt().coerceAtLeast(1), r.height.roundToInt().coerceAtLeast(1)))
                layout(c.maxWidth, c.maxHeight) { pl.place(r.left.roundToInt(), r.top.roundToInt()) }
            }.graphicsLayer { clip = false },
            thickness = 2f, blur = PANEL_BLUR.dp, whiteTint = 0.06f, blackTint = 0f) // strongly scattered: the (sharp) home screen lies behind it
        // List, clipped to the panel
        Box(
            Modifier.layout { m, c ->
                val r = panelNow(); val pl = m.measure(Constraints.fixed(r.width.roundToInt().coerceAtLeast(1), r.height.roundToInt().coerceAtLeast(1)))
                layout(c.maxWidth, c.maxHeight) { pl.place(r.left.roundToInt(), r.top.roundToInt()) }
            }.graphicsLayer { clip = true; shape = androidx.compose.foundation.shape.RoundedCornerShape(corner()) }
        ) {
            items.forEachIndexed { i, child ->
                if (drag?.first == i) return@forEachIndexed
                val current = i == pos.value.roundToInt()
                // Neighbours only appear with the panel (at the start you only see the stack itself)
                StackItem(child, wallpaper, density, { morph(itemRect(i)).topLeft - panelNow().topLeft }, ws,
                    alpha = { if (current) 1f else appear() }, chrome = appear)
            }
        }
        // Dragged widget above everything (not clipped)
        drag?.let { (i, o) ->
            Box(Modifier.zIndex(3f)) {
                StackItem(items[i], wallpaper, density, { itemRect(i).topLeft + o }, ws, lifted = true)
            }
        }
        // Dots to the right of the panel
        Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = appear() }) {
            val r = DOT_D * unit / 2; val pitchD = DOT_PITCH * unit
            val x = panel.right + DOTS_GAP * unit
            val top = panel.center.y - (items.size - 1) * pitchD / 2
            for (k in items.indices) {
                val on = (1f - abs(k - pos.value)).coerceIn(0f, 1f)
                drawCircle(Color.White.copy(alpha = 0.45f + 0.55f * on), r, Offset(x, top + k * pitchD))
            }
        }
        // Two blue round buttons at the bottom
        Box(Modifier.fillMaxSize().graphicsLayer { val a = appear(); alpha = a; val sc = 0.85f + 0.15f * a; scaleX = sc; scaleY = sc
            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, BTN_Y) }) {
            OptionButton(stringResource(R.string.stack_smart_rotate), spec.smartRotate, BTN_LEFT_X * w, BTN_Y * h, unit, density, rotateGlyph = true)
            OptionButton(stringResource(R.string.stack_suggestions), spec.suggestions, BTN_RIGHT_X * w, BTN_Y * h, unit, density, rotateGlyph = false)
        }
        // Top: + and ✓
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = appear() }) { EditButtons(geo, wallpaper, density, leftPlus = true) { 1f } }
    }
    model.stackRemoveAsk?.let { i ->
        val name = widgetLabel(items.getOrNull(i) ?: return@let)
        TwoButtonAlert(
            title = stringResource(R.string.stack_remove_title, name), message = stringResource(R.string.stack_remove_body),
            confirm = stringResource(R.string.stack_remove), cancel = stringResource(R.string.stack_cancel),
            onConfirm = { model.stackRemoveAsk = null; model.stackRemoveAt(key, i) }, onCancel = { model.stackRemoveAsk = null },
        )
    }
}

/** A widget in the editor list: content at original size, name below, minus top left. */
@Composable
private fun StackItem(child: io.github.janhurn9400.liquidy.data.WidgetSpec, wallpaper: ImageBitmap, density: Float, topLeft: () -> Offset, ws: Size,
                      lifted: Boolean = false, alpha: () -> Float = { 1f }, chrome: () -> Float = { 1f }) {
    Box(Modifier.offset { topLeft().let { IntOffset(it.x.roundToInt(), it.y.roundToInt()) } }
        .graphicsLayer { val s = if (lifted) 1.06f else 1f; scaleX = s; scaleY = s; this.alpha = alpha() }) {
        Box(Modifier.size(Dp(ws.width / density), Dp(ws.height / density)).clip(glassRect(DesignTokens.widgetCorner.dp))) {
            if (child.type == "android") AndroidWidget(child, Modifier.fillMaxSize())
            else WithWallpaperGlass(wallpaper) { IosWidget(child.type, child.cols, child.rows, Modifier.fillMaxSize(), child.config) }
        }
        BasicText(widgetLabel(child), Modifier.graphicsLayer { this.alpha = chrome() }.layout { m, _ ->
            val p = m.measure(Constraints())
            layout(0, 0) { p.place(((ws.width - p.width) / 2).roundToInt(), (ws.height + LABEL_GAP * density).roundToInt()) }
        }, style = TextStyle(fontFamily = Inter, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color.White))
        MinusBadge(wallpaper, Modifier.graphicsLayer { val a = chrome(); this.alpha = a; scaleX = a; scaleY = a }.offset {
            IntOffset((DesignTokens.badgeOffsetX.value * density - DesignTokens.badgeSize.value * density / 2).roundToInt(),
                (DesignTokens.badgeOffsetY.value * density - DesignTokens.badgeSize.value * density / 2).roundToInt())
        })
    }
}

/** Blue round button with a glyph, below it name + "On/Off" (frame 330). */
@Composable
private fun OptionButton(label: String, on: Boolean, cx: Float, cy: Float, unit: Float, density: Float, rotateGlyph: Boolean) {
    val d = BTN_D * unit
    Box(Modifier.offset { IntOffset((cx - d / 2).roundToInt(), (cy - d / 2).roundToInt()) }.size(Dp(d / density))) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(if (on) BLUE else Color.White.copy(alpha = 0.25f))
            val s = size.width; val wc = Color.White
            if (rotateGlyph) { // three bars (rising)
                drawRoundRect(wc.copy(alpha = 0.55f), Offset(s * 0.27f, s * 0.5f), Size(s * 0.12f, s * 0.12f), CornerRadius(s * 0.02f))
                drawRoundRect(wc.copy(alpha = 0.75f), Offset(s * 0.43f, s * 0.43f), Size(s * 0.12f, s * 0.19f), CornerRadius(s * 0.02f))
                drawRoundRect(wc, Offset(s * 0.59f, s * 0.33f), Size(s * 0.13f, s * 0.29f), CornerRadius(s * 0.02f))
            } else { // tile with plus
                drawRoundRect(wc.copy(alpha = 0.85f), Offset(s * 0.3f, s * 0.36f), Size(s * 0.34f, s * 0.34f), CornerRadius(s * 0.07f))
                drawCircle(wc, s * 0.11f, Offset(s * 0.64f, s * 0.36f))
                drawLine(BLUE, Offset(s * 0.64f, s * 0.31f), Offset(s * 0.64f, s * 0.41f), s * 0.03f)
                drawLine(BLUE, Offset(s * 0.59f, s * 0.36f), Offset(s * 0.69f, s * 0.36f), s * 0.03f)
            }
        }
    }
    Box(Modifier.offset { IntOffset(0, (cy + d / 2 + 6 * density).roundToInt()) }.layout { m, c ->
        val p = m.measure(Constraints()); layout(c.maxWidth, p.height) { p.place((cx - p.width / 2f).roundToInt(), 0) }
    }) {
        androidx.compose.foundation.layout.Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
            BasicText(label, style = TextStyle(fontFamily = Inter, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White))
            BasicText(stringResource(if (on) R.string.stack_on else R.string.stack_off),
                style = TextStyle(fontFamily = Inter, fontSize = 15.sp, color = Color.White.copy(alpha = 0.7f)))
        }
    }
}

// Dimensions [MEASURED] recording 10-08 10:21, frame 330/1360 (603 px wide downscale)
private const val REF_W = 603f
private const val PANEL_MARGIN = 101f   // the panel reaches this far beyond the (small) widget
private const val PANEL_CORNER = 66f
private const val PANEL_CENTER_Y = 0.5f
private const val ITEM_PITCH = 301f
private const val LABEL_GAP = 13f       // dp below the widget to the text top
private const val DOT_D = 11f
private const val DOT_PITCH = 26.5f
private const val DOTS_GAP = 25f
private const val BTN_D = 82f
private const val BTN_LEFT_X = 0.31f
private const val BTN_RIGHT_X = 0.688f
private const val BTN_Y = 0.815f
private val BLUE = Color(0xFF1E8BFF)
private const val PANEL_BLUR = 28f
private const val CHROME_FROM = 0.55f // names, minus, dots, buttons appear in the second half (frame 294–302)

private fun lerpR(a: Rect, b: Rect, t: Float) = Rect(a.left + (b.left - a.left) * t, a.top + (b.top - a.top) * t,
    a.right + (b.right - a.right) * t, a.bottom + (b.bottom - a.bottom) * t)
