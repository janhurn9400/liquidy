package io.github.janhurn9400.liquidy.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawWithContent
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
        val side = (if (geo.duo) (DUO_TODAY_PAGE - geo.widgetSize(4, 2).width / density) / 2 else DesignTokens.todaySide.value) * density
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

    fun contentHeight(rects: List<Pair<String, Rect>>, density: Float) =
        (rects.maxOfOrNull { it.second.bottom } ?: 0f) + DesignTokens.todayBottom.value * density
}

@Composable
fun TodayPage(model: HomeModel, geo: HomeGeometry, density: Float, wallpaper: androidx.compose.ui.graphics.ImageBitmap,
              clockSeconds: () -> Float = { 0f }, pageOffsetX: () -> Float) {
    val rects = TodayLayout.place(model.todayOrder(), model::sizeOf, geo, density)
    val drag = model.todayDrag
    val shape = glassRect(DesignTokens.widgetCorner.dp)
    // Duo aufgeklappt: unter der Heute-Seite wird NICHTS verwischt — Wallpaper bleibt scharf [GEMESSEN 19:18, 2,6 s].
    // Zugeklappt: wie iPhone — die bildschirmweite Unschärfe von HomeScreen (sideBlur) übernimmt.
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
            if (model.flyingKey == key) return@forEach // App fliegt hinein, das Bild des Widgets steht an seiner Stelle
            val phases = androidx.compose.runtime.remember(key) { jigglePhases(key) }
            val dragged = drag?.key == key
            // Neuer Platz (anderes Widget wird größer/verschoben) → federnd hinwandern statt springen
            val pos = androidx.compose.runtime.remember(key) { androidx.compose.animation.core.Animatable(r.topLeft, androidx.compose.ui.geometry.Offset.VectorConverter) }
            // beim Ziehen die sichtbare Position merken → nach dem Loslassen von dort aus einfedern (kein Sprung)
            val dropFrom = androidx.compose.runtime.remember(key) { androidx.compose.runtime.mutableStateOf<androidx.compose.ui.geometry.Offset?>(null) }
            if (dragged) {
                val from = TodayLayout.place(model.today, model::sizeOf, geo, density).first { it.first == key }.second
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
                            val from = TodayLayout.place(model.today, model::sizeOf, geo, density).first { it.first == key }.second
                            translationX = from.left - r.left + drag!!.offset.x
                            translationY = from.top - r.top + drag.offset.y
                            scaleX = DRAG_LIFT; scaleY = DRAG_LIFT
                        } else applyJiggle(model.jiggleAmount.value * WIDGET_JIGGLE_TODAY, clockSeconds(), phases)
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
                        else WithWallpaperGlass(wallpaper) { IosWidget(spec.type, spec.cols, Modifier.fillMaxSize()) }
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

private const val WIDGET_JIGGLE_TODAY = 0.35f
private const val DUO_TODAY_PAGE = 448f
private const val DUO_TODAY_TOP = 54.5f
private const val DUO_TODAY_ROW_GAP = 40f
private const val DUO_TODAY_LABEL_GAP = 7f
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
