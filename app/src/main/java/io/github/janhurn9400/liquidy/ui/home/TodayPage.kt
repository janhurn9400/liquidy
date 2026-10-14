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
 * Heute-Ansicht (recording8 2–20 s, T-TODAY): Widgets ohne Beschriftung untereinander auf dem verwischten Wallpaper,
 * gleiche Größen wie auf dem Homescreen, 2 Spalten (klein) bzw. ganze Breite, Rand 26,33 pt, Abstand 21,33 pt, oben ≈ 91,67 pt.
 */
object TodayLayout {
    /** Rechtecke (Inhaltskoordinaten in px, vor Scroll) je Widget-Key, in Reihenfolge. */
    fun place(keys: List<String>, size: (String) -> Pair<Int, Int>, geo: HomeGeometry, density: Float): List<Pair<String, Rect>> {
        val used = HashMap<Int, BooleanArray>() // Reihe → 2 Spalten
        fun row(r: Int) = used.getOrPut(r) { BooleanArray(2) }
        // Duo: Heute ist eine Seite (448 pt), Spalte 333 pt bei 57,5 pt, erste Reihe bei 54,5 pt; Reihenabstand inkl.
        // Beschriftung 40 pt [GEMESSEN] Aufnahme 10-08 21:28, 31 s. Sonst iPhone-Werte (recording8).
        val small = geo.widgetSize(2, 2).width / density
        val gap = if (geo.duo) DUO_TODAY_ROW_GAP else DesignTokens.todayGap.value
        val colGap = if (geo.duo) geo.widgetSize(4, 2).width / density - 2 * small else DesignTokens.todayGap.value
        val unit = (geo.widgetSize(2, 2).height / density + gap) * density
        // zugeklappt: mittig im Platz links vom Dock (sonst ragt das breite Widget ins Dock) [GEMESSEN 19:07, ≈ 25 pt Rand]
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

    /** Duo aufgeklappt: unten endet die Spalte an der Glasfläche (41 pt über dem Rand) — ganz unten gescrollt sollen die
     *  letzte Beschriftung und darunter ≈ so viel Glas wie oben (14 pt) sichtbar bleiben. */
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
    // Duo aufgeklappt: unter der Heute-Seite wird NICHTS verwischt — Wallpaper bleibt scharf [GEMESSEN 19:18, 2,6 s].
    // Zugeklappt: wie iPhone — die bildschirmweite Unschärfe von HomeScreen (sideBlur) übernimmt.
    val panel = if (geo.duo && geo.unfolded) rememberTodayPanel(model) else null
    val panelClip = if (panel != null && rects.isNotEmpty()) todayPanelRect(rects, geo, density, 1f) else null
    if (panel != null) TodayScrollPanel(model, geo, density, rects, pageOffsetX, panel)
    // Sonst schneidet jedes Widget selbst (s. u.). Im Bearbeiten-Modus laufen die Widgets bis zum Displayrand; nur solange
    // die Glasfläche da ist, endet dann alles an ihrem Rand [GEMESSEN 14:57, 14,0 s]
    Box(Modifier.fillMaxSize().then(if (panel == null) Modifier else Modifier
        .graphicsLayer {
            compositingStrategy = if (model.editing && panel.value > 0.001f) androidx.compose.ui.graphics.CompositingStrategy.Offscreen
            else androidx.compose.ui.graphics.CompositingStrategy.Auto
        }
        .drawWithContent {
            drawContent()
            val k = panel.value.coerceIn(0f, 1f)
            if (!model.editing || k <= 0.001f || rects.isEmpty()) return@drawWithContent
            // Schnittkante immer an der fertigen Fläche — wächst sie mit, läuft beim Schließen ein Band über die Widgets
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
        // key(): Zustand (Position-Animatable) hängt am Widget, nicht am Listenplatz — sonst springen nach dem
        // Löschen alle folgenden Widgets hart statt einzufedern
        rects.forEach { (key, r) -> androidx.compose.runtime.key(key) {
            val spec = model.widgets[key] ?: return@forEach
            if (io.github.janhurn9400.liquidy.data.AppLaunchTransition.launchingKey.value == key) return@forEach // App wächst gerade daraus
            if (model.contextMenu?.key == key) return@forEach // schwebt im Kontextmenü
            if (model.flyingKey == key || model.widgetConfigKey == key) return@forEach // App fliegt hinein, das Bild des Widgets steht an seiner Stelle
            val phases = androidx.compose.runtime.remember(key) { jigglePhases(key) }
            val dragged = drag?.key == key
            // Neuer Platz (anderes Widget wird größer/verschoben) → federnd hinwandern statt springen
            val pos = androidx.compose.runtime.remember(key) { androidx.compose.animation.core.Animatable(r.topLeft, androidx.compose.ui.geometry.Offset.VectorConverter) }
            // beim Ziehen die sichtbare Position merken → nach dem Loslassen von dort aus einfedern (kein Sprung)
            val dropFrom = androidx.compose.runtime.remember(key) { androidx.compose.runtime.mutableStateOf<androidx.compose.ui.geometry.Offset?>(null) }
            if (dragged) {
                val from = (TodayLayout.place(model.today, model::sizeOf, geo, density).firstOrNull { it.first == key }?.second ?: r)
                dropFrom.value = from.topLeft + drag!!.offset
            }
            androidx.compose.runtime.LaunchedEffect(r.topLeft, dragged) {
                if (dragged) return@LaunchedEffect // folgt dem Finger, eigene Verschiebung unten
                dropFrom.value?.let { pos.snapTo(it); dropFrom.value = null }
                pos.animateTo(r.topLeft, io.github.janhurn9400.liquidy.motion.Motion.todayReflow.spec())
            }
            Box(
                Modifier
                    // angefasstes Widget (Größe ändern / ziehen) liegt über den ausweichenden
                    .zIndex(if (dragged || model.resize?.key == key) 1f else 0f)
                    .offset { val p = if (dragged) r.topLeft else pos.value; IntOffset(p.x.roundToInt(), p.y.roundToInt()) }
                    .graphicsLayer {
                        if (dragged) {
                            // gezogenes Widget folgt dem Finger (vom ursprünglichen Platz aus), leicht angehoben
                            val from = (TodayLayout.place(model.today, model::sizeOf, geo, density).firstOrNull { it.first == key }?.second ?: r)
                            translationX = from.left - r.left + drag!!.offset.x
                            translationY = from.top - r.top + drag.offset.y
                            scaleX = DRAG_LIFT; scaleY = DRAG_LIFT
                        } else applyJiggle(model.jiggleAmount.value * WIDGET_JIGGLE_TODAY, clockSeconds(), phases)
                    }
                    // Duo aufgeklappt: oben und unten an der Glasfläche abgeschnitten (auch wenn sie ausgeblendet ist) — die
                    // Schnittkante bekommt wieder die Widget-Ecken, nicht die Form der Fläche [Duo, 14:57: 30,6 / 36,2 s]
                    .drawWithContent {
                        val clip = panelClip
                        if (clip == null || dragged || model.editing) { drawContent(); return@drawWithContent }
                        val y0 = pos.value.y - model.todayScroll.value
                        val ct = clip.top - y0; val cb = clip.bottom - y0
                        val w = size.width; val h = size.height
                        if (ct <= 0f && cb >= h) {
                            // Widget ganz drin — nur die Beschriftung darunter kann über die Kante ragen
                            clipRect(-w, -100_000f, 2 * w, cb) { this@drawWithContent.drawContent() }
                            return@drawWithContent
                        }
                        val rad = DesignTokens.widgetCorner.value * density
                        val keep = androidx.compose.ui.graphics.Path().apply {
                            val t = maxOf(0f, ct); val b = minOf(h, cb)
                            if (b > t) addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, t, w, b, androidx.compose.ui.geometry.CornerRadius(rad)))
                            val lt = maxOf(h, ct)
                            if (cb > lt) addRect(Rect(-w, lt, 2 * w, cb)) // Beschriftung darunter
                        }
                        clipPath(keep) { this@drawWithContent.drawContent() }
                    }
            ) {
                // Live-Rahmen beim Größe ändern (folgt dem Finger, rastet ein), sonst Platz aus dem Layout
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
                // Duo: Name unter dem Widget (wie auf dem Homescreen; iPhone-Heute-Seite hat keinen) [GEMESSEN] 31 s
                if (geo.duo) androidx.compose.foundation.text.BasicText(
                    io.github.janhurn9400.liquidy.ui.widgets.widgetLabel(model.shownWidget(key) ?: spec),
                    style = androidx.compose.ui.text.TextStyle(fontFamily = io.github.janhurn9400.liquidy.ui.Inter,
                        fontSize = androidx.compose.ui.unit.TextUnit(DesignTokens.labelFontSize.value, androidx.compose.ui.unit.TextUnitType.Sp),
                        color = DesignTokens.labelColor, shadow = DesignTokens.labelShadow, textAlign = androidx.compose.ui.text.style.TextAlign.Center),
                    maxLines = 1,
                    modifier = Modifier.offset { IntOffset(0, (r.height + DUO_TODAY_LABEL_GAP * density).roundToInt()) }
                        .size(Dp(r.width / density), Dp(20f)),
                )
                // Größengriff unten rechts am (Live-)Rahmen
                if (model.editing || model.badgeScale.value > 0f) ResizeHandle(
                    Modifier
                        .offset {
                            val side = (handleBox() * density).roundToInt(); val out = (handleOutset() * density).roundToInt()
                            IntOffset((resize?.w?.value ?: r.width).roundToInt() - side + out, (resize?.h?.value ?: r.height).roundToInt() - side + out)
                        }
                        .systemGestureExclusion()
                        .graphicsLayer { alpha = model.badgeScale.value.coerceIn(0f, 1f) }
                )
                // Minus-Badge oben links (Bearbeitungsmodus)
                if (model.editing || model.badgeScale.value > 0f) MinusBadge(wallpaper, Modifier
                    .offset { IntOffset((DesignTokens.badgeOffsetX.value * density).roundToInt() - (DesignTokens.badgeSize.value * density / 2).roundToInt(),
                        (DesignTokens.badgeOffsetY.value * density).roundToInt() - (DesignTokens.badgeSize.value * density / 2).roundToInt()) }
                    .graphicsLayer { val s = model.badgeScale.value; scaleX = s; scaleY = s })
            }
        }
    }}
    }
}

/** 0 = keine Glasfläche, 1 = ganz da. Kommt mit dem Finger, geht, sobald die Spalte steht. */
@Composable
private fun rememberTodayPanel(model: HomeModel): androidx.compose.animation.core.Animatable<Float, androidx.compose.animation.core.AnimationVector1D> {
    val shown = remember { androidx.compose.animation.core.Animatable(0f) }
    val active = model.todayTouching || model.todayScroll.isRunning
    androidx.compose.runtime.LaunchedEffect(active) {
        // [GEMESSEN 14:57, 27,48–27,70 s / 35,58–35,67 s, 60 fps]: auf ≈ 0,22 s (schnell, dann sanft), zu ≈ 0,1 s
        if (active) shown.animateTo(1f, androidx.compose.animation.core.tween(TODAY_PANEL_IN_MS, easing = androidx.compose.animation.core.LinearOutSlowInEasing))
        else shown.animateTo(0f, androidx.compose.animation.core.tween(TODAY_PANEL_OUT_MS, easing = androidx.compose.animation.core.FastOutLinearInEasing))
    }
    return shown
}

/**
 * Glasfläche (Bildschirmkoordinaten ohne Seitenversatz) bei Fortschritt [k]: fest am Bildschirm, 13 pt breiter als die
 * Spalte je Seite, oben 40 pt, unten 41 pt Abstand (an den geraden Kanten gemessen, 30,6/36,2 s) —
 * ganz oben gescrollt bleibt so ≈ 14 pt Glas über dem obersten Widget. Wächst aus der Mitte heraus — waagrecht kaum (je ≈ 3,6 pt),
 * senkrecht deutlich (je ≈ 17 pt) [GEMESSEN 27,50 → 27,70 s: links 256 → 251 px, oben 278 → 256, unten 970 → 994].
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
 * Duo aufgeklappt, beim Scrollen der Heute-Spalte [GEMESSEN Aufnahme 10-11 14:57]: klare Glasfläche hinter den Widgets
 * (Ecken ≈ 40 pt, Wallpaper darin ≈ +28 heller, scharf), wächst und blendet zugleich ein. Rechts daneben (8,75 pt) ein
 * weißer Scrollbalken (2,9 pt) in einer blassen, ≈ 98,5 pt langen Bahn, mittig zur Fläche — nur, wenn die Spalte länger als der Bildschirm ist.
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
            // über die Enden hinaus (Gummiband) wird der Balken kürzer [GEMESSEN 33,6 s: 117 → 88 px]
            val over = when { scroll < 0f -> -scroll; scroll > maxScroll -> scroll - maxScroll; else -> 0f }
            val thumb = (track * hPx / content - over * TODAY_BAR_SQUASH).coerceIn(TODAY_BAR_MIN * density, track)
            val frac = (scroll / maxScroll).coerceIn(0f, 1f)
            val top = full.center.y - track / 2
            val y = top + (track - thumb) * frac
            val x = full.right + TODAY_BAR_GAP * density
            // blasse Bahn, darin der weiße Balken; Enden gerade [GEMESSEN 30,6 s, Zoom]
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
private const val DUO_TODAY_LABEL_ROOM = 24f // Abstand + Beschriftung unter dem Widget
private const val DUO_TODAY_BLUR = 12f
private const val DUO_TODAY_FADE = 60f // pt weicher Übergang an der rechten Seitenkante
private const val DRAG_LIFT = 1.04f

/** Index in der Heute-Liste, an den ein gezogenes Widget mit Mitte [center] (Inhaltskoordinaten) gehört. */
fun todayTargetIndex(model: HomeModel, key: String, center: androidx.compose.ui.geometry.Offset, geo: HomeGeometry, density: Float): Int {
    val rest = model.today - key
    if (rest.isEmpty()) return 0
    val rects = TodayLayout.place(rest, model::sizeOf, geo, density)
    // vor das erste Widget, dessen Mitte „nach“ dem Finger liegt (zeilenweise)
    val i = rects.indexOfFirst { (_, r) -> center.y < r.top + r.height / 2 && (center.y < r.top || center.x < r.right) }
    return if (i < 0) rest.size else i
}
