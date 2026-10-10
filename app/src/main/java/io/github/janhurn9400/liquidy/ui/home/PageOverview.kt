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
 * Seitenübersicht wie iOS 26/27 („Seiten bearbeiten“, Bildschirmaufnahme 10-08 09:55, ausgewertet):
 * 3 × n Karten aus dickem Liquid Glass (starke Brechung) mit der verkleinerten Seite (ohne Dock/Beschriftung, ≈ 0,24×),
 * darunter ein runder Glaskreis — Haken = sichtbar, leer = ausgeblendet; ausgeblendete Seiten haben ein Minus zum Löschen.
 * Öffnen: Icons verschwinden sofort, die aktuelle Seite schrumpft als Karte in ihren Platz, die anderen blenden danach ein.
 * Schließen: gewählte Karte zoomt wieder auf Vollbild. Tippen auf eine sichtbare Karte → diese Seite, „Fertig“ → zurück.
 */
@Composable
fun PageOverview(model: HomeModel, geo: HomeGeometry, density: Float, wallpaper: ImageBitmap, w: Float, h: Float, onPage: (Int) -> Unit) {
    val pages = model.overviewPages()
    val n = pages.size
    val pt = { v: Float -> v * density * (w / density / REF_W) } // pt → px, skaliert auf die Bildschirmbreite
    val rows = ceil(n / 3f).toInt().coerceAtLeast(1)
    val blockH = (rows - 1) * pt(ROW_PITCH) + pt(CARD_H) + pt(CIRCLE_GAP) + pt(CIRCLE_D) / 2
    val top = (h * BLOCK_CENTER - blockH / 2).coerceAtLeast(pt(70f))
    val left = (w - (2 * pt(COL_PITCH) + pt(CARD_W))) / 2
    fun gridRect(i: Int) = Rect(Offset(left + (i % 3) * pt(COL_PITCH), top + (i / 3) * pt(ROW_PITCH)), Size(pt(CARD_W), pt(CARD_H)))
    // Vollbild-Rechteck der Karte (Seite in Originalgröße): gleicher Ausschnitt wie in der Karte
    val regionTop = (DesignTokens.gridTop.value - REGION_ABOVE_GRID) * density // Ausschnitt-Oberkante in px
    val regionH = w * CARD_H / CARD_W
    val full = Rect(Offset(0f, regionTop), Size(w, regionH))
    val p = { model.pagesOverview.value }

    // Umsortieren: lange drücken hebt die Karte an, sie folgt dem Finger, die anderen weichen federnd aus
    var drag by remember { mutableStateOf<CardDrag?>(null) }
    fun slotOf(i: Int): Int {
        val d = drag ?: return i
        val order = (0 until n).toMutableList().apply { remove(d.from); add(d.target, d.from) }
        return order.indexOf(i)
    }
    fun nearestSlot(center: Offset): Int = (0 until n).minByOrNull { (gridRect(it).center - center).getDistance() } ?: 0

    Box(
        Modifier.fillMaxSize().pointerInput(n) {
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
        }.pointerInput(n) {
            detectTapGestures { pos ->
                if (geo.editButtonRect(false).inflate(12f * density).contains(pos)) { model.closePagesOverview(onPage = onPage); return@detectTapGestures }
                for (i in 0 until n) {
                    val r = gridRect(i)
                    val circle = Offset(r.center.x, r.bottom + pt(CIRCLE_GAP))
                    if ((pos - circle).getDistance() < pt(CIRCLE_D) * 0.75f) { model.togglePage(i); return@detectTapGestures }
                    if (!pages[i].visible && (pos - r.topLeft).getDistance() < pt(22f)) { model.deleteHiddenPage(i); return@detectTapGestures }
                    if (r.contains(pos)) { if (pages[i].visible) model.closePagesOverview(i, onPage); return@detectTapGestures }
                }
            }
        }
    ) {
        val ids = remember(pages) { val seen = HashMap<String, Int>(); pages.map { pg -> val k = pg.items.joinToString(","); k + "#" + (seen.merge(k, 1, Int::plus)) } }
        pages.forEachIndexed { i, page -> key(ids[i]) {
            val focus = i == model.overviewFocus
            val dragged = drag?.from == i
            val home = gridRect(slotOf(i)).topLeft
            val pos = remember { Animatable(home, Offset.VectorConverter) }
            LaunchedEffect(home, dragged) { if (!dragged) pos.animateTo(home, io.github.janhurn9400.liquidy.motion.Motion.todayReflow.spec()) }
            if (dragged) LaunchedEffect(Unit) {
                androidx.compose.runtime.snapshotFlow { drag?.let { gridRect(it.from).topLeft + it.offset } }.collect { it?.let { o -> pos.snapTo(o) } }
            }
            val g = Rect(pos.value, Size(pt(CARD_W), pt(CARD_H)))
            // Zustand je Karte: Fokus-Karte wächst/schrumpft mit p; die anderen blenden verzögert ein
            fun rect(): Rect = if (focus) lerpRect(full, g, p()) else g
            fun appear(): Float = if (focus) 1f else ((p() - OTHERS_FROM) / (1f - OTHERS_FROM)).coerceIn(0f, 1f)
            // Fokus-Karte: Glas erst, wenn sie deutlich kleiner ist — beim Vollbild darf die Linse nichts vergrößern
            // (sonst bricht sie die zurückkehrenden echten Icons riesig auf)
            fun glass(): Float = if (focus) ((p() - FOCUS_GLASS_FROM) / (1f - FOCUS_GLASS_FROM)).coerceIn(0f, 1f) else p().coerceIn(0f, 1f)
            Box(
                Modifier
                    .offset { rect().topLeft.let { IntOffset(it.x.roundToInt(), it.y.roundToInt()) } }
                    .zIndex(if (dragged) 2f else 0f)
                    .graphicsLayer {
                        val a = appear(); alpha = a
                        val s = (0.92f + 0.08f * a) * (if (dragged) DRAG_LIFT else 1f); scaleX = s; scaleY = s
                    }
                    .size(Dp(g.width / density), Dp(g.height / density))
                    .graphicsLayer {
                        // Größe über Skalierung (Fokus-Karte ist beim Öffnen bildschirmgroß)
                        val r = rect(); scaleX = r.width / g.width; scaleY = r.height / g.height
                        transformOrigin = TransformOrigin(0f, 0f)
                    }
            ) {
                PageCardGlass(Modifier.fillMaxSize(), corner = pt(CARD_CORNER) / density, strength = { glass() })
                // Seite verkleinert (ohne Beschriftung, Android-Widgets als Platzhalter)
                Box(
                    Modifier.fillMaxSize().clip(RoundedRectangle(Dp(pt(CARD_CORNER) / density)))
                        .wrapContentSize(Alignment.TopStart, unbounded = true)
                        .requiredSize(Dp(w / density), Dp(regionH / density))
                        .graphicsLayer { val s = g.width / w; scaleX = s; scaleY = s; transformOrigin = TransformOrigin(0f, 0f) }
                ) { PageThumbnail(model, geo, page.items, wallpaper, density, regionTop) }
            }
            // Auswahlkreis + Minus (blenden mit den Karten ein)
            val circleA = { ((p() - CIRCLES_FROM) / (1f - CIRCLES_FROM)).coerceIn(0f, 1f) }
            Box(
                Modifier
                    .offset { IntOffset((g.center.x - pt(CIRCLE_D) / 2).roundToInt(), (g.bottom + pt(CIRCLE_GAP) - pt(CIRCLE_D) / 2).roundToInt()) }
                    .size(Dp(pt(CIRCLE_D) / density))
                    .graphicsLayer { val a = circleA() * (if (dragged) 0f else 1f); alpha = a; scaleX = 0.8f + 0.2f * a; scaleY = 0.8f + 0.2f * a }
            ) {
                PageCardGlass(Modifier.fillMaxSize(), corner = pt(CIRCLE_D) / 2 / density, strength = { 1f })
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

/** Dickes Liquid Glass der Karten (starke Brechung über die ganze Fläche, wie im Video); [strength] 0…1 blendet es aus. */
@Composable
private fun PageCardGlass(modifier: Modifier, corner: Float, strength: () -> Float) {
    // Ohne Stärke gar nicht zeichnen: die Glasfläche malt sonst das (skaliert falsch abgebildete) Hintergrundbild mit,
    // in dem beim Schließen die echten Icons wieder auftauchen → riesige Geisterbilder
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

/** Inhalt einer Seite in Originalgröße (wird außen skaliert): Icons ohne Beschriftung, Ordner, Widgets. */
@Composable
private fun PageThumbnail(model: HomeModel, geo: HomeGeometry, items: List<String>, wallpaper: ImageBitmap, density: Float, regionTop: Float) {
    val cells = GridPlacement.place(items, model::sizeOf)
    Box(Modifier.fillMaxSize()) {
        items.forEachIndexed { slot, key ->
            val cell = cells.getOrNull(slot) ?: -1
            if (cell < 0) return@forEachIndexed
            val tl = geo.slotPosition(cell) - Offset(0f, regionTop)
            val spec = model.widgets[key]
            if (spec != null) {
                val r = geo.widgetRect(tl, spec.cols, spec.rows)
                Box(
                    Modifier.offset { IntOffset(r.left.roundToInt(), r.top.roundToInt()) }
                        .size(Dp(r.width / density), Dp(r.height / density))
                        .clip(io.github.janhurn9400.liquidy.glass.glassRect(DesignTokens.widgetCorner.dp))
                ) {
                    // Android-Widgets haben nur eine Ansicht (läuft schon auf der echten Seite) → Platzhalter
                    if (spec.type == "android") Box(Modifier.fillMaxSize().background(Color(0xFF1C1C1E)))
                    else WithWallpaperGlass(wallpaper) { IosWidget(spec.type, spec.cols, Modifier.fillMaxSize()) }
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

// Maße [GEMESSEN] Aufnahme 10-08 09:55 Bild 460 (3×, iPhone 402 pt breit)
private const val REF_W = 402f
private const val CARD_W = 96f
private const val CARD_H = 165f
private const val CARD_CORNER = 13f
private const val COL_PITCH = 122f
private const val ROW_PITCH = 246f
private const val CIRCLE_D = 36f
private const val CIRCLE_GAP = 30f       // Kreismitte unter der Kartenunterkante
private const val BLOCK_CENTER = 0.553f  // Rastermitte bei 55 % der Höhe
private const val REGION_ABOVE_GRID = 44f // Seitenausschnitt der Karte beginnt 44 pt über der ersten Reihe
private const val OTHERS_FROM = 0.45f    // andere Karten erscheinen ab diesem Fortschritt
private const val CIRCLES_FROM = 0.6f
private const val DRAG_LIFT = 1.06f

private data class CardDrag(val from: Int, val target: Int, val offset: Offset)
private const val FOCUS_GLASS_FROM = 0.3f
