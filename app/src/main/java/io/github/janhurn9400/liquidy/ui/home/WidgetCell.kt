package io.github.janhurn9400.liquidy.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import io.github.janhurn9400.liquidy.data.WidgetSpec
import io.github.janhurn9400.liquidy.design.DesignTokens
import io.github.janhurn9400.liquidy.glass.glassRect
import io.github.janhurn9400.liquidy.ui.Inter
import io.github.janhurn9400.liquidy.ui.widgets.AndroidWidget
import io.github.janhurn9400.liquidy.ui.widgets.IosWidget
import io.github.janhurn9400.liquidy.ui.widgets.widgetLabel

/**
 * Widget auf einer Homescreen-Seite. Positioniert wie eine Icon-Zelle (cellOffsetX), zeichnet sich selbst
 * um [DesignTokens.widgetOverhang] nach links versetzt; Label darunter, Wackeln + Minus-Badge im Bearbeitungsmodus.
 */
@Composable
fun WidgetCell(
    key: String,
    spec: WidgetSpec,
    geo: HomeGeometry,
    density: Float,
    wallpaper: ImageBitmap,
    jiggle: () -> Float,
    clockSeconds: () -> Float,
    badgeScale: () -> Float,
    showBadge: Boolean,
    pressAlpha: () -> Float,
    showLabel: Boolean = true,
    /** Laufende Größenänderung (Live-Rahmen + Unschärfe), sonst null. */
    resize: io.github.janhurn9400.liquidy.ui.home.WidgetResize? = null,
    /** Fläche ausblenden (App fliegt gerade hinein, das Bild des Widgets steht an seiner Stelle) — Name bleibt. */
    hideBody: Boolean = false,
    /** Für Widget-Stapel (Position/Rahmen); null = nur das erste Widget zeigen. */
    stackModel: HomeModel? = null,
) {
    val size = geo.widgetSize(spec.cols, spec.rows)
    val phases = remember(key) { jigglePhases(key) }
    val shape = glassRect(DesignTokens.widgetCorner.dp)
    val label = widgetLabel(stackModel?.shownWidget(key) ?: spec.stack.firstOrNull() ?: spec)
    Layout(
        modifier = Modifier.graphicsLayer { applyJiggle(jiggle() * WIDGET_JIGGLE, clockSeconds(), phases) },
        content = {
            Box(
                Modifier
                    .layout { m, _ ->
                        // Live-Rahmen beim Größe ändern (folgt dem Finger, rastet mit Feder ein), sonst feste Größe
                        val w = (resize?.w?.value ?: size.width).toInt().coerceAtLeast(1)
                        val h = (resize?.h?.value ?: size.height).toInt().coerceAtLeast(1)
                        val p = m.measure(Constraints.fixed(w, h))
                        layout(w, h) { p.place(0, 0) }
                    }
                    .graphicsLayer { alpha = if (hideBody) 0f else 1f }
                    .clip(shape)
                    .drawWithContent {
                        drawContent()
                        val a = pressAlpha()
                        if (a > 0f) drawRect(Color.Black.copy(alpha = a))
                    }
            ) {
                Box(Modifier.fillMaxSize().graphicsLayer {
                    // Inhalt beim Größe ändern unscharf (recording8: sofort beim Anfassen, scharf nach dem Einrasten)
                    val b = (resize?.blur?.value ?: 0f) * DesignTokens.widgetResizeBlur.value * density
                    renderEffect = if (b > 0.5f) androidx.compose.ui.graphics.BlurEffect(b, b, androidx.compose.ui.graphics.TileMode.Clamp) else null
                }) {
                    if (spec.type == STACK && stackModel != null) WidgetStackContent(key, spec, stackModel, wallpaper)
                    else if (spec.type == "android") AndroidWidget(spec, Modifier.fillMaxSize())
                    else WithWallpaperGlass(wallpaper) { IosWidget(spec.type, spec.cols, spec.rows, Modifier.fillMaxSize(), spec.config) }
                }
            }
            if (showLabel) BasicText(
                label,
                style = TextStyle(fontFamily = Inter, fontSize = DesignTokens.labelFontSize.value.sp, color = DesignTokens.labelColor, shadow = DesignTokens.labelShadow),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (showBadge) MinusBadge(wallpaper, Modifier.graphicsLayer { val s = badgeScale(); scaleX = s; scaleY = s })
            // am Bildschirmrand: Griff von der Android-Zurück-Geste ausnehmen (sonst fängt das System das Ziehen ab)
            if (showBadge) ResizeHandle(Modifier.systemGestureExclusion().graphicsLayer { alpha = badgeScale().coerceIn(0f, 1f) })
            if (spec.type == STACK && stackModel != null) StackDots(key, spec.stack.size, stackModel, Modifier)
        },
    ) { ms, _ ->
        val w = ms[0].measure(Constraints())
        val l = if (showLabel) ms[1].measure(Constraints(maxWidth = w.width)) else null
        val hasDots = spec.type == STACK && stackModel != null
        val last = if (hasDots) ms.size - 1 else ms.size
        val dots = if (hasDots) ms.last().measure(Constraints.fixed((STACK_DOTS_W * density).toInt(), w.height)) else null
        val badge = if (showBadge) ms[last - 2].measure(Constraints()) else null
        val handle = if (showBadge) ms[last - 1].measure(Constraints()) else null
        // Zellen-Ursprung = Icon-Oberkante-links − (Label-Breite − Icon)/2 (cellOffsetX) → Widget-Kante daraus
        val x0 = ((DesignTokens.labelWidth.value - DesignTokens.iconSize.value) / 2 * density + geo.widgetOffset.x).toInt()
        val y0 = geo.widgetOffset.y.toInt()
        val baseline = y0 + w.height + DesignTokens.widgetLabelGap.dp.roundToPx()
        layout(x0 + w.width, baseline + (l?.let { it.height - it[FirstBaseline] } ?: 0)) {
            w.place(x0, y0)
            l?.place(x0 + (w.width - l.width) / 2, baseline - l[FirstBaseline])
            dots?.place(x0 + w.width + (STACK_DOTS_GAP * density).toInt(), y0)
            // Griff liegt in der unteren rechten Ecke, folgt dem Live-Rahmen
            val out = ((HANDLE_THICK / 2 - HANDLE_INSET) * density).toInt()
            handle?.place(x0 + w.width - handle.width + out, y0 + w.height - handle.height + out)
            badge?.place(
                x0 + (DesignTokens.badgeOffsetX.value * density).toInt() - badge.width / 2,
                y0 + (DesignTokens.badgeOffsetY.value * density).toInt() - badge.height / 2,
            )
        }
    }
}

/** Glas in Widgets sieht nur das Wallpaper (das Widget liegt selbst in der aufgenommenen Icon-Ebene). */
@Composable
internal fun WithWallpaperGlass(wallpaper: ImageBitmap, content: @Composable () -> Unit) {
    val wb = io.github.janhurn9400.liquidy.glass.LocalWallpaperBlur.current
    val backdrop = remember(wallpaper, wb) { io.github.janhurn9400.liquidy.glass.WallpaperBackdrop(wallpaper, wb.blurred, wb.amount) }
    androidx.compose.runtime.CompositionLocalProvider(io.github.janhurn9400.liquidy.glass.LocalGlassBackdrop provides backdrop) { content() }
}

/**
 * Größengriff wie iOS (recording8 39,55 s, Ausschnitt 2×): dicke gebogene Glas-„Bohne“ (≈ 13 pt) entlang der unteren
 * rechten Ecke, Mittellinie ≈ 3 pt innerhalb der Widget-Kante, Schenkel je ≈ 31 pt; klar, mit heller Lichtkante.
 */
@Composable
internal fun ResizeHandle(modifier: Modifier) {
    val side = handleBox().dp
    androidx.compose.foundation.Canvas(modifier.size(side)) {
        val t = HANDLE_THICK.dp.toPx()
        val rad = HANDLE_RADIUS.dp.toPx() // engere Rundung als das Widget (Schenkel ≈ 26 pt ab der Kante)
        val ext = HANDLE_EXT.dp.toPx()
        // Mittellinie: Kante (rechts/unten) liegt bei size − outset
        val outset = t / 2 - HANDLE_INSET.dp.toPx()
        val right = size.width - outset - HANDLE_INSET.dp.toPx()
        val bottom = size.height - outset - HANDLE_INSET.dp.toPx()
        val center = android.graphics.Path().apply {
            moveTo(right, bottom - rad - ext)
            lineTo(right, bottom - rad)
            arcTo(android.graphics.RectF(right - 2 * rad, bottom - 2 * rad, right, bottom), 0f, 90f, false)
            lineTo(right - rad - ext, bottom)
        }
        val outline = android.graphics.Path()
        android.graphics.Paint().apply { style = android.graphics.Paint.Style.STROKE; strokeWidth = t; strokeCap = android.graphics.Paint.Cap.ROUND }
            .getFillPath(center, outline)
        val shape = outline.asComposePath()
        // Glaskörper: leicht aufgehellt, oben-links heller (Licht), unten-rechts etwas dunkler
        drawPath(shape, androidx.compose.ui.graphics.Brush.linearGradient(
            listOf(Color.White.copy(alpha = HANDLE_FILL_TOP), Color.White.copy(alpha = HANDLE_FILL_BOTTOM)),
            androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Offset(size.width, size.height)))
        // Lichtkante
        drawPath(shape, androidx.compose.ui.graphics.Brush.linearGradient(
            listOf(Color.White.copy(alpha = HANDLE_RIM), Color.White.copy(alpha = HANDLE_RIM * 0.25f), Color.White.copy(alpha = HANDLE_RIM * 0.7f)),
            androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Offset(size.width, size.height)),
            style = androidx.compose.ui.graphics.drawscope.Stroke(HANDLE_RIM_W.dp.toPx()))
    }
}

/** Wie weit der Griff über die Widget-Ecke hinausragt (pt). */
internal fun handleOutset() = HANDLE_THICK / 2 - HANDLE_INSET

/** Kantenlänge der Griff-Fläche (pt). */
internal fun handleBox() = HANDLE_RADIUS + HANDLE_EXT + HANDLE_THICK + HANDLE_INSET

private const val HANDLE_THICK = 13f   // pt
private const val HANDLE_EXT = 2f      // gerade Stücke über die Rundung hinaus
private const val HANDLE_RADIUS = 15f  // Rundung der Mittellinie → Schenkel 15 + 2 + 6,5 + 3 ≈ 26 pt [GEMESSEN]
private const val HANDLE_INSET = 3f    // Mittellinie innerhalb der Kante
private const val HANDLE_FILL_TOP = 0.16f
private const val HANDLE_FILL_BOTTOM = 0.05f
private const val HANDLE_RIM = 0.85f
private const val HANDLE_RIM_W = 1.2f
private const val WIDGET_JIGGLE = 0.35f // große Flächen wackeln sichtbar weniger [GESCHÄTZT] recording8 31 s
