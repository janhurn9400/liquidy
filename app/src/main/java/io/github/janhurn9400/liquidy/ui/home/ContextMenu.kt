package io.github.janhurn9400.liquidy.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.janhurn9400.liquidy.design.DesignTokens
import io.github.janhurn9400.liquidy.glass.LiquidGlass
import io.github.janhurn9400.liquidy.glass.glassRect
import io.github.janhurn9400.liquidy.motion.Motion
import io.github.janhurn9400.liquidy.ui.Inter
import kotlin.math.roundToInt

/**
 * Context menu on long press on an icon (recording4, M-CTXMENU / T-CTX):
 * background dimmed (not blurred), the icon floats lifted above it, the Liquid Glass menu grows out of it.
 * If the icon is in the lower half, the menu opens above it (order mirrored: app actions closest to the icon).
 * Entries: Remove App, Edit Home Screen, App Info + the app's native Android shortcuts.
 */
@Composable
fun ContextMenuOverlay(
    model: HomeModel,
    m: ContextMenuState,
    screenW: Float,
    screenH: Float,
    density: Float,
    onSize: (Int, Int) -> Unit = { _, _ -> },
    icon: @Composable () -> Unit,
) {
    val iconPx = DesignTokens.iconSize.value * density
    val isWidgetMenu = m.widgetRect != null
    // Rest position of the icon: shrunk around the cell centre to its real size (Duo dock 48 instead of 60 pt)
    val rest = m.widgetRect ?: Rect(m.iconTopLeft + Offset(iconPx, iconPx) * ((1f - m.iconScale) / 2), Size(iconPx * m.iconScale, iconPx * m.iconScale))
    val above = rest.center.y > screenH / 2
    // App actions (rank 0) are always closest to the icon, system entries on the outside
    val system = m.items.filter { it.glyph !is MenuGlyph.Bitmap }
    val actions = m.items.filter { it.glyph is MenuGlyph.Bitmap }
    val items = if (above) system + actions.reversed() else actions + system.reversed()

    // Tapping anywhere outside closes (iOS: already on touch down)
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(m) { detectTapGestures(onPress = { model.closeContextMenu() }) }
    ) {
        Canvas(Modifier.fillMaxSize()) { drawRect(Color.Black.copy(alpha = m.dim.value * DesignTokens.ctxDim.value)) }

        // Lifted icon (without label) above the dimming
        Box(
            Modifier
                .offset { IntOffset(cellOffsetX(m.iconTopLeft.x, density).roundToInt(), m.iconTopLeft.y.roundToInt()) }
                .graphicsLayer {
                    val s = m.lift.value * m.iconScale
                    scaleX = s; scaleY = s
                    translationY = -m.liftY.value * density
                    // Origin: icon centre or widget centre (the cell is wider/taller than the icon)
                    transformOrigin = if (isWidgetMenu) TransformOrigin(
                        ((rest.center.x - cellOffsetX(m.iconTopLeft.x, density)) / size.width).coerceIn(0f, 1f),
                        ((rest.center.y - m.iconTopLeft.y) / size.height).coerceIn(0f, 1f))
                    else TransformOrigin(0.5f, DesignTokens.iconSize.value / 2 / (size.height / density))
                }
        ) { icon() }

        // Menu: position from the END state of the icon (1.15×, 10 pt up), so it doesn't wander while lifting
        val lifted = if (isWidgetMenu) rest else run {
            val s = Motion.ctxLiftScale.value
            val c = rest.center - Offset(0f, Motion.ctxLiftY.value * density)
            val ls = rest.width * s
            Rect(c - Offset(ls / 2, ls / 2), Size(ls, ls))
        }
        Layout(
            content = {
                MenuPanel(items, m.dim.value * DesignTokens.ctxDim.value,
                    header = if (isWidgetMenu) ({ SizeRow(m, model) { c, r -> model.closeContextMenu(); onSize(c, r) } }) else null,
                ) { item -> model.closeContextMenu(); item.action() }
            },
        ) { ms, c ->
            val w = DesignTokens.ctxWidth.dp.roundToPx()
            val p = ms[0].measure(Constraints.fixedWidth(w))
            val side = DesignTokens.ctxSide.dp.toPx()
            val gap = DesignTokens.ctxGap.dp.toPx()
            val leftAligned = rest.center.x < screenW / 2
            // Widgets: menu centred below/above the widget (recording8 15.2 s); icons: flush with the icon edge
            val x = (if (isWidgetMenu) lifted.center.x - w / 2f else if (leftAligned) lifted.left else lifted.right - w).coerceIn(side, screenW - side - w)
            val y = if (above) lifted.top - gap - p.height else lifted.bottom + gap
            layout(c.maxWidth, c.maxHeight) {
                p.placeWithLayer(x.roundToInt(), y.roundToInt()) {
                    // grows out of the icon: origin = icon centre (x) at the icon edge (y)
                    val v = m.menu.value
                    val from = Motion.ctxMenuFrom.value
                    val s = from + (1f - from) * v
                    scaleX = s; scaleY = s
                    alpha = (v * MENU_FADE).coerceIn(0f, 1f)
                    transformOrigin = TransformOrigin(
                        ((lifted.center.x - x) / w).coerceIn(0f, 1f),
                        if (above) 1f else 0f,
                    )
                }
            }
        }
    }
}

/** Liquid Glass menu (T-CTX) — also for the edit menu in edit mode. [dim] = dimming below (as tint). */
@Composable
internal fun MenuPanel(items: List<MenuItem>, dim: Float, header: (@Composable () -> Unit)? = null, onTap: (MenuItem) -> Unit) {
    LiquidGlass(
        shape = glassRect(DesignTokens.ctxCorner.dp),
        // The glass source is not dimmed, but the menu lies above the dimming → same dimming as tint
        blackTint = dim.coerceAtMost(1f),
        whiteTint = DesignTokens.ctxTint.value,
        blur = DesignTokens.ctxBlur.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = DesignTokens.ctxPadV.dp)) {
            header?.invoke()
            items.forEach { item -> MenuRow(item, onTap) }
        }
    }
}

/**
 * Duo edit menu as a glass droplet [MEASURED video 4 (19:05), 3.91–4.10 s]: the brush button detaches as a round glass droplet,
 * flows towards the bottom left and stretches meanwhile (width ahead of height) into the menu shape; the entries appear first
 * blurred (from ≈ 55 %) and sharpen. Real size per frame (no scaling → the glass always shows what lies behind).
 * [t] 0 = button, 1 = menu; [from] button rect, [to] target rect (height from the content, estimated beforehand).
 */
@Composable
internal fun DuoDropletMenu(items: List<MenuItem>, dim: Float, t: () -> Float, from: Rect, toLeftTop: Offset, widthPx: Float,
                            density: Float, flash: () -> Float = { 0f }, closing: () -> Boolean = { false }, onTap: (MenuItem) -> Unit) {
    var contentH by remember { mutableStateOf(0) }
    val estH = (items.size * DesignTokens.ctxRow.value + 2 * DesignTokens.ctxPadV.value) * density
    val corner = DesignTokens.ctxCorner.value * density
    // Peak progress: entries don't fade out again during the overshoot (k < 1); when closing it follows
    val peak = remember { floatArrayOf(0f) }
    fun tp(): Float { val k = t(); if (k > peak[0] || k < peak[0] - 0.15f) peak[0] = k; return peak[0].coerceAtMost(1f) }
    // Progress comes from a springy animation (overshoots) → overshoot as squash/stretch
    fun rect(): Rect {
        val k = t()
        val kc = k.coerceIn(0f, 1f)
        val h1 = if (contentH > 0) contentH.toFloat() else estH
        val to = Rect(toLeftTop, Size(widthPx, h1))
        val ease = { x: Float -> val q = x.coerceIn(0f, 1f); 1f - (1f - q) * (1f - q) }
        val o = k - kc                                   // >0 overshoot, otherwise 0
        val sq = if (k > 1f) o else (k - 1f).coerceAtMost(0f) * 0f
        // [MEASURED video 4, centre/size per frame] arc instead of a straight line: first almost straight down, then to the left;
        // height is done early (93 % at 68 % width), width catches up; at the end it swings a little below the final position
        val kw = ease(kc * 1.12f) + sq * 1.1f            // wider during the overshoot …
        val kh = ease(kc * 0.92f) - sq * 0.7f            // … but flatter (water squash)
        val kp = ease(kc)
        var w = from.width + (to.width - from.width) * kw
        var h = from.height + (to.height - from.height) * kh
        var c = from.center + (to.center - from.center) * kp
        if (closing()) {
            // Closing [video 4, 4.91–5.10 s]: the remaining droplet hangs below the button and is drawn in —
            // shortly before the end a vertical oval just below the button centre
            val e = (1f - kc / 0.25f).coerceIn(0f, 1f) * (kc / 0.25f).coerceIn(0f, 1f) * 4f
            h *= 1f + 0.28f * e; w *= 1f - 0.12f * e
            c += Offset(0f, from.height * 0.32f * e)
        }
        return Rect(c - Offset(w / 2, h / 2), Size(w, h))
    }
    // Shape per progress: at first a round droplet with a tail towards the brush, then wobbling (edges bulge, the bottom-left
    // corner stays round longer) and at the end the normal menu [video 4: 3.95 droplet+tail, 4.04 skewed, 4.09 round]
    fun shape(): androidx.compose.ui.graphics.Shape {
        val k = t(); val kc = k.coerceIn(0f, 1f)
        val r = rect(); val half = minOf(r.width, r.height) / 2
        // [video 4, 3.95–4.14 s] never pointed: the droplet is a slanted egg/oval along the flight direction — the top-right
        // corner (at the brush) and bottom-left stay fully round for long, top-left/bottom-right tighten early;
        // top-right is the roundest corner until shortly before the end (4.04–4.09 s), only at the end all equal
        val inv = (1f - kc).coerceIn(0f, 1f)
        val radTR = corner + (half - corner) * Math.pow(inv.toDouble(), 0.35).toFloat()
        val radBL = corner + (half - corner) * Math.pow(inv.toDouble(), 0.6).toFloat()
        val rad = corner + (half - corner) * Math.pow(inv.toDouble(), 1.6).toFloat()
        // Opening: never pointed. Closing: small droplet with a round tip towards the button (4.99–5.06 s)
        // Closing: droplet shape = all corners fully round, only the corner towards the button (top right) tighter → points to the brush,
        // without any attached tip (that looked like a separate bubble). At the end a circle again = button.
        val tail = 0f
        val pointing = if (closing()) (kc / 0.45f).coerceIn(0f, 1f).let { x -> x * (1f - x) * 4f } else 0f
        // Edges only bulge OUTWARDS (water has no dents): belly in the middle of the path + during the overshoot
        val wobble = if (closing()) 0f else kotlin.math.sin(kc * Math.PI.toFloat()) * half * 0.05f + (k - 1f).coerceAtLeast(0f) * half * 0.4f
        val trC = if (closing()) half + (half * 0.42f - half) * pointing else radTR
        val roundC = if (closing()) maxOf(rad, half * pointing) else rad
        val blC = if (closing()) maxOf(radBL, half * pointing) else radBL
        return io.github.janhurn9400.liquidy.glass.DropletShape(roundC, trC, roundC, blC, wobble, tail, inset = DROP_MARGIN * density)
    }
    Layout(content = {
        Box {
            // The brush button ITSELF becomes the droplet: at t = 0 the droplet looks exactly like the button (same clear
            // glass, white brush), lights up ([flash]) and then flows smoothly into menu glass [video 4, 3.84–3.95 s]
            io.github.janhurn9400.liquidy.glass.DropletGlass(
                shape = { shape() },
                modifier = Modifier.fillMaxSize(),
                blur = DesignTokens.ctxBlur.dp,
                whiteTint = { val m = (t() / 0.35f).coerceIn(0f, 1f)
                    DUO_BTN_WHITE + (DesignTokens.ctxTint.value - DUO_BTN_WHITE) * m + DROP_FLASH_WHITE * flash() },
                blackTint = { dim.coerceAtMost(1f) * t().coerceIn(0f, 1f) },
            )
            // Brush glyph: sits at the button centre (top right in the droplet) and fades out as it flows off
            Box(Modifier.fillMaxSize().padding(DROP_MARGIN.dp).graphicsLayer { alpha = (1f - t() / 0.3f).coerceIn(0f, 1f) }, contentAlignment = Alignment.TopEnd) {
                Box(Modifier.size((from.width / density).dp), contentAlignment = Alignment.Center) {
                    BrushGlyph(DUO_GLYPH.dp, Color.White, DUO_GLYPH_STROKE)
                }
            }
            // Content at final size, anchored top left; clipped by the droplet, fades in blurred → sharp
            Box(Modifier.fillMaxSize().graphicsLayer { clip = true; shape = shape() }.padding(DROP_MARGIN.dp)) {
                // Blurred → sharp as a crossfade of two copies with a FIXED blur: a running radius creates new
                // blur shaders every frame (stutter, especially on first open)
                Box(Modifier.wrapContentSize(Alignment.TopStart, unbounded = true).width((widthPx / density).dp)) {
                    Column(
                        Modifier.graphicsLayer {
                            val a = ((tp() - 0.45f) / 0.25f).coerceIn(0f, 1f); val b = ((tp() - 0.7f) / 0.3f).coerceIn(0f, 1f)
                            alpha = a * (1f - b)
                            renderEffect = androidx.compose.ui.graphics.BlurEffect(DROP_TEXT_BLUR * density, DROP_TEXT_BLUR * density)
                        }.padding(vertical = DesignTokens.ctxPadV.dp)
                    ) { items.forEach { item -> MenuRow(item, onTap = {}) } }
                    Column(
                        Modifier.onSizeChanged { contentH = it.height }
                            .graphicsLayer { alpha = ((tp() - 0.7f) / 0.3f).coerceIn(0f, 1f) }
                            .padding(vertical = DesignTokens.ctxPadV.dp)
                    ) { items.forEach { item -> MenuRow(item, onTap) } }
                }
            }
        }
    }) { ms, c ->
        val r = rect(); val m = DROP_MARGIN * density
        val p = ms[0].measure(Constraints.fixed((r.width + 2 * m).roundToInt().coerceAtLeast(1), (r.height + 2 * m).roundToInt().coerceAtLeast(1)))
        layout(c.maxWidth, c.maxHeight) { p.place((r.left - m).roundToInt(), (r.top - m).roundToInt()) }
    }
}

private const val DROP_TEXT_BLUR = 8f
private const val DROP_MARGIN = 40f // pt drawing margin for bulge/tip

private const val DROP_FLASH_WHITE = 0.75f

@Composable
private fun MenuRow(item: MenuItem, onTap: (MenuItem) -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val color = if (item.destructive) DesignTokens.ctxDestructive else DesignTokens.ctxText
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = DesignTokens.ctxRow.dp)
            .drawBehind {
                if (pressed) drawRoundRect(DesignTokens.ctxPressedFill, Offset(PRESS_INSET.dp.toPx(), 0f),
                    Size(size.width - 2 * PRESS_INSET.dp.toPx(), size.height), CornerRadius(size.height / 2))
            }
            .pointerInput(item) {
                detectTapGestures(
                    onPress = { pressed = true; tryAwaitRelease(); pressed = false },
                    onTap = { onTap(item) },
                )
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.offset(x = DesignTokens.ctxGlyphX.dp - DesignTokens.ctxGlyph.dp / 2)) {
            MenuGlyphView(item.glyph, color)
        }
        BasicText(
            item.label,
            style = TextStyle(fontFamily = Inter, fontSize = DesignTokens.ctxFont.value.sp, color = color,
                lineHeight = DesignTokens.ctxLine.value.sp),
            modifier = Modifier
                .padding(start = DesignTokens.ctxTextX.dp, end = DesignTokens.ctxTextRight.dp)
                .padding(vertical = ROW_TEXT_PAD.dp),
        )
    }
}

/**
 * Size bar at the top of the widget menu (recording8 15.2 s): four glyphs small · medium · large · extra large,
 * current size with a grey background, extra large greyed out (doesn't exist on the iPhone).
 */
@Composable
private fun SizeRow(m: ContextMenuState, model: HomeModel, onPick: (Int, Int) -> Unit) {
    val current = model.widgets[m.key]?.let { it.cols to it.rows }
    androidx.compose.foundation.layout.Row(
        Modifier.fillMaxWidth().padding(horizontal = SIZE_ROW_PAD.dp).heightIn(min = SIZE_ROW_H.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        (m.sizes + listOf(-1 to -1)).forEach { (c, r) ->
            val enabled = c > 0
            val selected = current == (c to r)
            Box(
                Modifier
                    .size(SIZE_CELL_W.dp, SIZE_CELL_H.dp)
                    .drawBehind { if (selected) drawRoundRect(SIZE_SELECTED, cornerRadius = CornerRadius(10.dp.toPx())) }
                    .pointerInput(c, r) { detectTapGestures { if (enabled) onPick(c, r) } },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.size(SIZE_GLYPH.dp)) {
                    val color = Color.White.copy(alpha = if (enabled) 1f else 0.3f)
                    val sw = 1.6.dp.toPx()
                    val u = size.width
                    // Frame in the shape of the widget size
                    val (gw, gh) = when {
                        !enabled -> u * 0.62f to u * 0.92f
                        c == 2 -> u * 0.72f to u * 0.72f
                        r == 2 -> u to u * 0.62f
                        else -> u * 0.86f to u * 0.86f
                    }
                    val tl = Offset((u - gw) / 2, (u - gh) / 2)
                    drawRoundRect(color, tl, Size(gw, gh), CornerRadius(u * 0.14f), style = Stroke(sw))
                    // small square top left + content lines (like the iOS glyphs)
                    drawRoundRect(color, tl + Offset(u * 0.1f, u * 0.1f), Size(u * 0.18f, u * 0.18f), CornerRadius(u * 0.04f))
                    if (enabled && (c == 4)) drawLine(color, tl + Offset(u * 0.1f, gh - u * 0.16f), tl + Offset(gw - u * 0.1f, gh - u * 0.16f), sw, StrokeCap.Round)
                    if (enabled && r == 4) drawLine(color, tl + Offset(u * 0.1f, gh - u * 0.3f), tl + Offset(gw - u * 0.1f, gh - u * 0.3f), sw, StrokeCap.Round)
                }
            }
        }
    }
}

private const val SIZE_ROW_PAD = 12f
private const val SIZE_ROW_H = 52f
private const val SIZE_CELL_W = 52f
private const val SIZE_CELL_H = 40f
private const val SIZE_GLYPH = 24f
private val SIZE_SELECTED = Color(0x26FFFFFF)

/** Own outline glyphs (no SF Symbols, proportions after recording4) or shortcut icon. */
@Composable
private fun MenuGlyphView(glyph: MenuGlyph, color: Color) {
    val size = DesignTokens.ctxGlyph.dp
    if (glyph is MenuGlyph.Bitmap) {
        glyph.image?.let {
            Image(it, null, Modifier.size(size), colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(color))
        } ?: Box(Modifier.size(size))
        return
    }
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val sw = w * GLYPH_STROKE
        val stroke = Stroke(sw, cap = StrokeCap.Round)
        val c = Offset(w / 2, w / 2)
        when (glyph) {
            MenuGlyph.Remove -> { // circle with minus
                drawCircle(color, w / 2 - sw / 2, c, style = stroke)
                drawLine(color, Offset(w * 0.3f, w / 2), Offset(w * 0.7f, w / 2), sw, StrokeCap.Round)
            }
            MenuGlyph.Info -> { // circle with "i"
                drawCircle(color, w / 2 - sw / 2, c, style = stroke)
                drawCircle(color, sw * 0.75f, Offset(w / 2, w * 0.3f))
                drawLine(color, Offset(w / 2, w * 0.46f), Offset(w / 2, w * 0.72f), sw, StrokeCap.Round)
            }
            MenuGlyph.EditHome -> { // phone outline with app grid at the top
                val pw = w * 0.58f
                drawRoundRect(color, Offset((w - pw) / 2, sw / 2), Size(pw, w - sw), CornerRadius(w * 0.14f), style = stroke)
                val dot = sw * 0.62f
                for (r in 0 until 3) for (col in 0 until 3) {
                    drawCircle(color, dot, Offset(w / 2 + (col - 1) * w * 0.12f, w * 0.22f + r * w * 0.12f))
                }
                drawLine(color, Offset(w * 0.4f, w * 0.82f), Offset(w * 0.6f, w * 0.82f), sw, StrokeCap.Round)
            }
            MenuGlyph.AddWidget -> { // frame with plus (iOS "Add Widget")
                drawRoundRect(color, Offset(sw / 2, w * 0.18f), Size(w * 0.72f, w * 0.72f), CornerRadius(w * 0.16f), style = stroke)
                drawLine(color, Offset(w * 0.78f, w * 0.02f), Offset(w * 0.78f, w * 0.34f), sw, StrokeCap.Round)
                drawLine(color, Offset(w * 0.62f, w * 0.18f), Offset(w * 0.94f, w * 0.18f), sw, StrokeCap.Round)
            }
            MenuGlyph.Customise -> { // phone with brush (iOS "Customise")
                val pw = w * 0.5f
                drawRoundRect(color, Offset(w * 0.12f, sw / 2), Size(pw, w - sw), CornerRadius(w * 0.12f), style = stroke)
                drawLine(color, Offset(w * 0.55f, w * 0.85f), Offset(w * 0.92f, w * 0.38f), sw * 1.6f, StrokeCap.Round)
            }
            MenuGlyph.Stack -> { // card with two edges behind it (stack)
                drawRoundRect(color, Offset(w * 0.12f, w * 0.34f), Size(w * 0.76f, w * 0.56f), CornerRadius(w * 0.1f), style = stroke)
                drawLine(color, Offset(w * 0.2f, w * 0.22f), Offset(w * 0.8f, w * 0.22f), sw, StrokeCap.Round)
                drawLine(color, Offset(w * 0.3f, w * 0.1f), Offset(w * 0.7f, w * 0.1f), sw, StrokeCap.Round)
            }
            MenuGlyph.Pages -> { // two pages side by side
                val pw = w * 0.36f
                for (k in 0..1) drawRoundRect(color, Offset(w * 0.1f + k * (pw + w * 0.08f), w * 0.12f), Size(pw, w * 0.76f), CornerRadius(w * 0.08f), style = stroke)
            }
            MenuGlyph.Settings -> { // gear: ring + 8 teeth
                drawCircle(color, w * 0.2f, c, style = stroke)
                for (k in 0 until 8) {
                    val a = Math.toRadians(k * 45.0)
                    val d = Offset(kotlin.math.cos(a).toFloat(), kotlin.math.sin(a).toFloat())
                    drawLine(color, c + d * (w * 0.3f), c + d * (w * 0.44f), sw * 1.5f, StrokeCap.Round)
                }
                drawCircle(color, w * 0.31f, c, style = Stroke(sw))
            }
            MenuGlyph.Haptics -> { // phone with vibration arcs
                val pw = w * 0.36f; val x0 = (w - pw) / 2
                drawRoundRect(color, Offset(x0, sw / 2), Size(pw, w - sw), CornerRadius(w * 0.1f), style = stroke)
                for (side in listOf(-1f, 1f)) for (k in 1..2) {
                    val r = w * (0.08f + 0.1f * k)
                    drawArc(color, if (side < 0) 145f else -35f, 70f, false,
                        Offset(c.x + side * (pw / 2 + w * 0.02f) - r, c.y - r), Size(2 * r, 2 * r), style = Stroke(sw * 0.8f, cap = StrokeCap.Round))
                }
            }
            MenuGlyph.Wallpaper -> { // blossom (iOS "Edit Wallpaper")
                for (k in 0 until 6) {
                    val a = Math.toRadians(k * 60.0)
                    drawCircle(color, w * 0.17f, Offset(w / 2 + (kotlin.math.cos(a) * w * 0.25).toFloat(), w / 2 + (kotlin.math.sin(a) * w * 0.25).toFloat()), style = Stroke(sw * 0.8f))
                }
                drawCircle(color, w * 0.1f, c, style = Stroke(sw * 0.8f))
            }
            else -> Unit
        }
    }
}

private const val MENU_FADE = 4f       // menu is fully opaque from 25 % progress [ESTIMATED]
private const val PRESS_INSET = 8f     // pt, pressed row [ESTIMATED]
private const val ROW_TEXT_PAD = 11f   // pt above/below the text → 42 pt for one line (T-CTX.row)
private const val GLYPH_STROKE = 0.085f // stroke width × glyph size [MEASURED roughly]
