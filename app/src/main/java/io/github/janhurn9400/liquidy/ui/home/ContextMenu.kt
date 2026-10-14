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
 * Kontextmenü beim Long-Press auf ein Icon (recording4, M-CTXMENU / T-CTX):
 * Hintergrund abgedunkelt (nicht verwischt), das Icon schwebt angehoben darüber, das Liquid-Glass-Menü wächst aus ihm heraus.
 * Liegt das Icon in der unteren Hälfte, öffnet das Menü darüber (Reihenfolge gespiegelt: App-Aktionen am nächsten zum Icon).
 * Einträge: App entfernen, Home-Bildschirm bearbeiten, App-Info + die nativen Android-Shortcuts der App.
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
    // Ruhelage des Icons: um die Zellmitte auf seine echte Größe verkleinert (Duo-Dock 48 statt 60 pt)
    val rest = m.widgetRect ?: Rect(m.iconTopLeft + Offset(iconPx, iconPx) * ((1f - m.iconScale) / 2), Size(iconPx * m.iconScale, iconPx * m.iconScale))
    val above = rest.center.y > screenH / 2
    // App-Aktionen (Rang 0) liegen immer am nächsten zum Icon, System-Einträge außen
    val system = m.items.filter { it.glyph !is MenuGlyph.Bitmap }
    val actions = m.items.filter { it.glyph is MenuGlyph.Bitmap }
    val items = if (above) system + actions.reversed() else actions + system.reversed()

    // Tippen irgendwo außerhalb schließt (iOS: schon beim Aufsetzen)
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(m) { detectTapGestures(onPress = { model.closeContextMenu() }) }
    ) {
        Canvas(Modifier.fillMaxSize()) { drawRect(Color.Black.copy(alpha = m.dim.value * DesignTokens.ctxDim.value)) }

        // Angehobenes Icon (ohne Label) über der Abdunkelung
        Box(
            Modifier
                .offset { IntOffset(cellOffsetX(m.iconTopLeft.x, density).roundToInt(), m.iconTopLeft.y.roundToInt()) }
                .graphicsLayer {
                    val s = m.lift.value * m.iconScale
                    scaleX = s; scaleY = s
                    translationY = -m.liftY.value * density
                    // Ursprung: Icon-Mitte bzw. Widget-Mitte (Zelle ist breiter/höher als das Icon)
                    transformOrigin = if (isWidgetMenu) TransformOrigin(
                        ((rest.center.x - cellOffsetX(m.iconTopLeft.x, density)) / size.width).coerceIn(0f, 1f),
                        ((rest.center.y - m.iconTopLeft.y) / size.height).coerceIn(0f, 1f))
                    else TransformOrigin(0.5f, DesignTokens.iconSize.value / 2 / (size.height / density))
                }
        ) { icon() }

        // Menü: Position aus der END-Lage des Icons (1,15×, 10 pt hoch), damit es beim Anheben nicht wandert
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
            // Widgets: Menü mittig unter/über dem Widget (recording8 15,2 s); Icons: bündig an der Icon-Kante
            val x = (if (isWidgetMenu) lifted.center.x - w / 2f else if (leftAligned) lifted.left else lifted.right - w).coerceIn(side, screenW - side - w)
            val y = if (above) lifted.top - gap - p.height else lifted.bottom + gap
            layout(c.maxWidth, c.maxHeight) {
                p.placeWithLayer(x.roundToInt(), y.roundToInt()) {
                    // wächst aus dem Icon: Ursprung = Icon-Mitte (x) an der Icon-Kante (y)
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

/** Liquid-Glass-Menü (T-CTX) — auch fürs Edit-Menü im Bearbeitungsmodus. [dim] = Abdunkelung darunter (als Tönung). */
@Composable
internal fun MenuPanel(items: List<MenuItem>, dim: Float, header: (@Composable () -> Unit)? = null, onTap: (MenuItem) -> Unit) {
    LiquidGlass(
        shape = glassRect(DesignTokens.ctxCorner.dp),
        // Glas-Quelle ist nicht abgedunkelt, das Menü liegt aber über der Abdunkelung → gleiche Abdunkelung als Tönung
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
 * Duo-Edit-Menü als Glastropfen [GEMESSEN Video 4 (19:05), 3,91–4,10 s]: der Pinsel-Knopf löst sich als runder Glastropfen,
 * fließt nach links unten und dehnt sich dabei (Breite eilt der Höhe voraus) zur Menüform; die Einträge erscheinen erst
 * unscharf (ab ≈ 55 %) und werden scharf. Echte Größe je Frame (kein Skalieren → das Glas zeigt immer, was dahinter liegt).
 * [t] 0 = Knopf, 1 = Menü; [from] Knopf-Rechteck, [to] Ziel-Rechteck (Höhe aus dem Inhalt, vorher geschätzt).
 */
@Composable
internal fun DuoDropletMenu(items: List<MenuItem>, dim: Float, t: () -> Float, from: Rect, toLeftTop: Offset, widthPx: Float,
                            density: Float, flash: () -> Float = { 0f }, closing: () -> Boolean = { false }, onTap: (MenuItem) -> Unit) {
    var contentH by remember { mutableStateOf(0) }
    val estH = (items.size * DesignTokens.ctxRow.value + 2 * DesignTokens.ctxPadV.value) * density
    val corner = DesignTokens.ctxCorner.value * density
    // Höchststand des Fortschritts: Einträge blenden beim Nachschwingen (k < 1) nicht wieder ab; beim Schließen folgt er
    val peak = remember { floatArrayOf(0f) }
    fun tp(): Float { val k = t(); if (k > peak[0] || k < peak[0] - 0.15f) peak[0] = k; return peak[0].coerceAtMost(1f) }
    // Fortschritt kommt von einer federnden Animation (überschwingt) → Überschwinger als Quetschen/Strecken
    fun rect(): Rect {
        val k = t()
        val kc = k.coerceIn(0f, 1f)
        val h1 = if (contentH > 0) contentH.toFloat() else estH
        val to = Rect(toLeftTop, Size(widthPx, h1))
        val ease = { x: Float -> val q = x.coerceIn(0f, 1f); 1f - (1f - q) * (1f - q) }
        val o = k - kc                                   // >0 Überschwinger, sonst 0
        val sq = if (k > 1f) o else (k - 1f).coerceAtMost(0f) * 0f
        // [GEMESSEN Video 4, Mittelpunkt/Größe je Bild] Bogen statt Gerade: erst fast senkrecht nach unten, dann nach links;
        // Höhe ist früh fertig (93 % bei 68 % Breite), Breite holt nach; am Ende schwingt er etwas unter die Endlage
        val kw = ease(kc * 1.12f) + sq * 1.1f            // breiter beim Überschwingen …
        val kh = ease(kc * 0.92f) - sq * 0.7f            // … dafür flacher (Wasser-Quetschen)
        val kp = ease(kc)
        var w = from.width + (to.width - from.width) * kw
        var h = from.height + (to.height - from.height) * kh
        var c = from.center + (to.center - from.center) * kp
        if (closing()) {
            // Zuklappen [Video 4, 4,91–5,10 s]: der Rest-Tropfen hängt unter dem Knopf und wird hineingezogen —
            // kurz vor dem Ende ein senkrechtes Oval knapp unterhalb der Knopfmitte
            val e = (1f - kc / 0.25f).coerceIn(0f, 1f) * (kc / 0.25f).coerceIn(0f, 1f) * 4f
            h *= 1f + 0.28f * e; w *= 1f - 0.12f * e
            c += Offset(0f, from.height * 0.32f * e)
        }
        return Rect(c - Offset(w / 2, h / 2), Size(w, h))
    }
    // Form je Fortschritt: anfangs runder Tropfen mit Schweif zum Pinsel, dann wabbelnd (Kanten wölben sich, Ecke
    // unten links bleibt länger rund) und am Ende das normale Menü [Video 4: 3,95 Tropfen+Schweif, 4,04 schief, 4,09 rund]
    fun shape(): androidx.compose.ui.graphics.Shape {
        val k = t(); val kc = k.coerceIn(0f, 1f)
        val r = rect(); val half = minOf(r.width, r.height) / 2
        // [Video 4, 3,95–4,14 s] nie spitz: der Tropfen ist ein schräges Ei/Oval entlang der Flugrichtung — Ecke oben
        // rechts (am Pinsel) und unten links bleiben lange voll rund, oben links/unten rechts werden früh enger;
        // oben rechts ist bis kurz vor Schluss die rundeste Ecke (4,04–4,09 s), erst am Ende alle gleich
        val inv = (1f - kc).coerceIn(0f, 1f)
        val radTR = corner + (half - corner) * Math.pow(inv.toDouble(), 0.35).toFloat()
        val radBL = corner + (half - corner) * Math.pow(inv.toDouble(), 0.6).toFloat()
        val rad = corner + (half - corner) * Math.pow(inv.toDouble(), 1.6).toFloat()
        // Öffnen: nie spitz. Zuklappen: kleiner Tropfen mit runder Kuppe Richtung Knopf (4,99–5,06 s)
        // Zuklappen: Tropfenform = alle Ecken voll rund, nur die Ecke zum Knopf (oben rechts) enger → zeigt zum Pinsel,
        // ganz ohne angesetzte Kuppe (die wirkte wie ein eigenes Bläschen). Am Ende wieder Kreis = Knopf.
        val tail = 0f
        val pointing = if (closing()) (kc / 0.45f).coerceIn(0f, 1f).let { x -> x * (1f - x) * 4f } else 0f
        // Kanten wölben sich nur nach AUSSEN (Wasser hat keine Dellen): Bauch in der Mitte des Wegs + beim Überschwingen
        val wobble = if (closing()) 0f else kotlin.math.sin(kc * Math.PI.toFloat()) * half * 0.05f + (k - 1f).coerceAtLeast(0f) * half * 0.4f
        val trC = if (closing()) half + (half * 0.42f - half) * pointing else radTR
        val roundC = if (closing()) maxOf(rad, half * pointing) else rad
        val blC = if (closing()) maxOf(radBL, half * pointing) else radBL
        return io.github.janhurn9400.liquidy.glass.DropletShape(roundC, trC, roundC, blC, wobble, tail, inset = DROP_MARGIN * density)
    }
    Layout(content = {
        Box {
            // Der Pinsel-Knopf SELBST wird zum Tropfen: bei t = 0 sieht der Tropfen exakt wie der Knopf aus (gleiches klares
            // Glas, weißer Pinsel), leuchtet auf ([flash]) und geht dann fließend in Menü-Glas über [Video 4, 3,84–3,95 s]
            io.github.janhurn9400.liquidy.glass.DropletGlass(
                shape = { shape() },
                modifier = Modifier.fillMaxSize(),
                blur = DesignTokens.ctxBlur.dp,
                whiteTint = { val m = (t() / 0.35f).coerceIn(0f, 1f)
                    DUO_BTN_WHITE + (DesignTokens.ctxTint.value - DUO_BTN_WHITE) * m + DROP_FLASH_WHITE * flash() },
                blackTint = { dim.coerceAtMost(1f) * t().coerceIn(0f, 1f) },
            )
            // Pinsel-Symbol: sitzt an der Knopf-Mitte (oben rechts im Tropfen) und blendet beim Losfließen aus
            Box(Modifier.fillMaxSize().padding(DROP_MARGIN.dp).graphicsLayer { alpha = (1f - t() / 0.3f).coerceIn(0f, 1f) }, contentAlignment = Alignment.TopEnd) {
                Box(Modifier.size((from.width / density).dp), contentAlignment = Alignment.Center) {
                    BrushGlyph(DUO_GLYPH.dp, Color.White, DUO_GLYPH_STROKE)
                }
            }
            // Inhalt in Endgröße, links oben verankert; vom Tropfen beschnitten, blendet unscharf → scharf ein
            Box(Modifier.fillMaxSize().graphicsLayer { clip = true; shape = shape() }.padding(DROP_MARGIN.dp)) {
                // Unscharf → scharf als Überblendung zweier Kopien mit FESTEM Blur: ein mitlaufender Radius erzeugt je
                // Frame neue Blur-Shader (Ruckler, v. a. beim ersten Öffnen)
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
private const val DROP_MARGIN = 40f // pt Zeichenrand für Wölbung/Kuppe

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
 * Größenleiste oben im Widget-Menü (recording8 15,2 s): vier Symbole klein · mittel · groß · extragroß,
 * aktuelle Größe grau hinterlegt, extragroß ausgegraut (gibt es auf dem iPhone nicht).
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
                    // Rahmen in der Form der Widget-Größe
                    val (gw, gh) = when {
                        !enabled -> u * 0.62f to u * 0.92f
                        c == 2 -> u * 0.72f to u * 0.72f
                        r == 2 -> u to u * 0.62f
                        else -> u * 0.86f to u * 0.86f
                    }
                    val tl = Offset((u - gw) / 2, (u - gh) / 2)
                    drawRoundRect(color, tl, Size(gw, gh), CornerRadius(u * 0.14f), style = Stroke(sw))
                    // kleines Quadrat oben links + Inhaltslinien (wie die iOS-Symbole)
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

/** Eigene Umriss-Glyphen (keine SF Symbols, Proportionen nach recording4) bzw. Shortcut-Symbol. */
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
            MenuGlyph.Remove -> { // Kreis mit Minus
                drawCircle(color, w / 2 - sw / 2, c, style = stroke)
                drawLine(color, Offset(w * 0.3f, w / 2), Offset(w * 0.7f, w / 2), sw, StrokeCap.Round)
            }
            MenuGlyph.Info -> { // Kreis mit „i“
                drawCircle(color, w / 2 - sw / 2, c, style = stroke)
                drawCircle(color, sw * 0.75f, Offset(w / 2, w * 0.3f))
                drawLine(color, Offset(w / 2, w * 0.46f), Offset(w / 2, w * 0.72f), sw, StrokeCap.Round)
            }
            MenuGlyph.EditHome -> { // Telefon-Umriss mit App-Raster oben
                val pw = w * 0.58f
                drawRoundRect(color, Offset((w - pw) / 2, sw / 2), Size(pw, w - sw), CornerRadius(w * 0.14f), style = stroke)
                val dot = sw * 0.62f
                for (r in 0 until 3) for (col in 0 until 3) {
                    drawCircle(color, dot, Offset(w / 2 + (col - 1) * w * 0.12f, w * 0.22f + r * w * 0.12f))
                }
                drawLine(color, Offset(w * 0.4f, w * 0.82f), Offset(w * 0.6f, w * 0.82f), sw, StrokeCap.Round)
            }
            MenuGlyph.AddWidget -> { // Rahmen mit Plus (iOS „Add Widget“)
                drawRoundRect(color, Offset(sw / 2, w * 0.18f), Size(w * 0.72f, w * 0.72f), CornerRadius(w * 0.16f), style = stroke)
                drawLine(color, Offset(w * 0.78f, w * 0.02f), Offset(w * 0.78f, w * 0.34f), sw, StrokeCap.Round)
                drawLine(color, Offset(w * 0.62f, w * 0.18f), Offset(w * 0.94f, w * 0.18f), sw, StrokeCap.Round)
            }
            MenuGlyph.Customise -> { // Telefon mit Pinsel (iOS „Customise“)
                val pw = w * 0.5f
                drawRoundRect(color, Offset(w * 0.12f, sw / 2), Size(pw, w - sw), CornerRadius(w * 0.12f), style = stroke)
                drawLine(color, Offset(w * 0.55f, w * 0.85f), Offset(w * 0.92f, w * 0.38f), sw * 1.6f, StrokeCap.Round)
            }
            MenuGlyph.Stack -> { // Karte mit zwei Kanten dahinter (Stapel)
                drawRoundRect(color, Offset(w * 0.12f, w * 0.34f), Size(w * 0.76f, w * 0.56f), CornerRadius(w * 0.1f), style = stroke)
                drawLine(color, Offset(w * 0.2f, w * 0.22f), Offset(w * 0.8f, w * 0.22f), sw, StrokeCap.Round)
                drawLine(color, Offset(w * 0.3f, w * 0.1f), Offset(w * 0.7f, w * 0.1f), sw, StrokeCap.Round)
            }
            MenuGlyph.Pages -> { // zwei Seiten nebeneinander
                val pw = w * 0.36f
                for (k in 0..1) drawRoundRect(color, Offset(w * 0.1f + k * (pw + w * 0.08f), w * 0.12f), Size(pw, w * 0.76f), CornerRadius(w * 0.08f), style = stroke)
            }
            MenuGlyph.Settings -> { // Zahnrad: Ring + 8 Zähne
                drawCircle(color, w * 0.2f, c, style = stroke)
                for (k in 0 until 8) {
                    val a = Math.toRadians(k * 45.0)
                    val d = Offset(kotlin.math.cos(a).toFloat(), kotlin.math.sin(a).toFloat())
                    drawLine(color, c + d * (w * 0.3f), c + d * (w * 0.44f), sw * 1.5f, StrokeCap.Round)
                }
                drawCircle(color, w * 0.31f, c, style = Stroke(sw))
            }
            MenuGlyph.Haptics -> { // Telefon mit Schwingungsbögen
                val pw = w * 0.36f; val x0 = (w - pw) / 2
                drawRoundRect(color, Offset(x0, sw / 2), Size(pw, w - sw), CornerRadius(w * 0.1f), style = stroke)
                for (side in listOf(-1f, 1f)) for (k in 1..2) {
                    val r = w * (0.08f + 0.1f * k)
                    drawArc(color, if (side < 0) 145f else -35f, 70f, false,
                        Offset(c.x + side * (pw / 2 + w * 0.02f) - r, c.y - r), Size(2 * r, 2 * r), style = Stroke(sw * 0.8f, cap = StrokeCap.Round))
                }
            }
            MenuGlyph.Wallpaper -> { // Blüte (iOS „Edit Wallpaper“)
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

private const val MENU_FADE = 4f       // Menü ist ab 25 % Fortschritt voll deckend [GESCHÄTZT]
private const val PRESS_INSET = 8f     // pt, gedrückte Zeile [GESCHÄTZT]
private const val ROW_TEXT_PAD = 11f   // pt über/unter dem Text → 42 pt bei einer Zeile (T-CTX.row)
private const val GLYPH_STROKE = 0.085f // Strichstärke × Glyphengröße [GEMESSEN grob]
