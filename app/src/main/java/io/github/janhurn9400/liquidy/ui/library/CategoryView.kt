package io.github.janhurn9400.liquidy.ui.library

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import io.github.janhurn9400.liquidy.data.AppEntry
import io.github.janhurn9400.liquidy.design.ContinuousRoundedShape
import io.github.janhurn9400.liquidy.design.DesignTokens
import io.github.janhurn9400.liquidy.motion.Motion
import io.github.janhurn9400.liquidy.ui.Inter
import io.github.janhurn9400.liquidy.ui.home.HomeModel
import io.github.janhurn9400.liquidy.ui.home.OpenCategory
import kotlin.math.roundToInt

/**
 * Aufgeklappte Kategorie (recording2 17,5–21 s, frameweise): KEIN Panel — großer weißer Titel und das
 * Homescreen-Raster (4 Spalten, 64 pt) direkt auf dem verwischten Hintergrund.
 * Bewegung (M-LIB-CAT): Titel + Raster zoomen als EINE Gruppe aus der Mitte des Mini-Clusters (Skala = Fortschritt).
 * Die 4 Cluster-Icons fliegen dagegen einzeln aus ihren Mini-Plätzen (der Cluster in der Kachel ist leer) —
 * beim Zuklappen sieht man genau das: die vier werden zum 2×2-Cluster, der Rest schrumpft in dessen Mitte.
 */
@Composable
fun CategoryView(model: HomeModel, open: OpenCategory, geo: LibraryGeometry, density: Float, onLaunch: (AppEntry, Rect) -> Unit,
    /** Bildschirm-x der Mediathek-Spalte (Duo aufgeklappt: rechte Seite), sonst 0. */
    originX: Float = 0f) {
    val apps = open.category.apps
    val icon = DesignTokens.iconSize.value * density
    val maxRows = ((geo.height - DesignTokens.libCatGridTop.value * density) / (DesignTokens.gridRowPitch.value * density)).toInt().coerceAtLeast(1)
    val shown = apps.take(DesignTokens.GRID_COLUMNS * maxRows)
    val clusterApps = if (apps.size > 4) apps.drop(3).take(4).map { it.key }.toSet() else emptySet()

    // Duo: Raster der Kategorie wie das Homescreen-Raster einer Seite (Spalte 1 bei 65 pt, Abstand 88 pt), in der Spalte
    val side = if (geo.duo) DUO_CAT_SIDE else DesignTokens.gridSide.value
    val pitch = if (geo.duo) DUO_CAT_PITCH else DesignTokens.gridColPitch.value
    fun gridTopLeft(k: Int) = Offset(
        originX + (side + pitch * (k % DesignTokens.GRID_COLUMNS)) * density,
        (DesignTokens.libCatGridTop.value + DesignTokens.gridRowPitch.value * (k / DesignTokens.GRID_COLUMNS)) * density,
    )
    /** Inhalts- → Bildschirmkoordinate inkl. Scroll beim Öffnen und der aktuellen Schrumpfung der Mediathek. */
    fun libToScreen(p: Offset): Offset {
        val s = model.libCategoryScale()
        val pivot = Offset(geo.columnWidth / 2, geo.height * Motion.libCategoryPivotY.value)
        return Offset(originX, 0f) + pivot + (p - Offset(0f, open.scrollAtOpen) - pivot) * s
    }
    val clusterRect = geo.appRect(open.tileIndex, 3, 4) // Quadrant unten rechts
    val pivot = clusterRect.center.let { Offset(originX + it.x, it.y - open.scrollAtOpen) }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(open) {
                detectTapGestures { p ->
                    if (open.progress.targetValue < 1f) return@detectTapGestures
                    val k = shown.indices.firstOrNull { k ->
                        Rect(gridTopLeft(k), Size(icon, icon)).inflate(icon * TAP_SLOP).contains(p)
                    }
                    if (k != null) onLaunch(shown[k], Rect(gridTopLeft(k), Size(icon, icon)))
                    else model.closeCategory()
                }
            }
    ) {
        val shape = ContinuousRoundedShape(DesignTokens.iconCorner.dp)
        // ── Gruppe: Titel + alle Nicht-Cluster-Icons, gemeinsam um die Cluster-Mitte gezoomt ──
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = open.progress.value.coerceAtLeast(0f)
                    scaleX = p; scaleY = p
                    transformOrigin = TransformOrigin(pivot.x / size.width, pivot.y / size.height)
                    alpha = (p / GROUP_FADE).coerceIn(0f, 1f)
                }
        ) {
            Layout(
                modifier = Modifier.graphicsLayer {
                    val from = Motion.libCategoryTitleFrom.value
                    alpha = ((open.progress.value - from) / (1f - from)).coerceIn(0f, 1f)
                },
                content = {
                    BasicText(
                        open.category.name,
                        style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = DesignTokens.folderTitleSize.value.sp, color = Color.White),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                },
            ) { ms, c ->
                val t = ms[0].measure(Constraints(maxWidth = c.maxWidth - (2 * DesignTokens.libCatTitleX.value * density).roundToInt()))
                layout(c.maxWidth, c.maxHeight) {
                    t.place((originX + DesignTokens.libCatTitleX.value * density).roundToInt(), (DesignTokens.libCatTitleBaseline.value * density).roundToInt() - t[FirstBaseline])
                }
            }
            shown.forEachIndexed { k, app ->
                if (app.key in clusterApps) return@forEachIndexed
                val dst = gridTopLeft(k)
                Box(Modifier.offset { IntOffset(dst.x.roundToInt(), dst.y.roundToInt()) }) {
                    CategoryCell(app, shape) { 1f }
                }
            }
        }
        // ── Cluster-Icons: einzeln vom Mini-Platz in den Rasterplatz ──
        shown.forEachIndexed { k, app ->
            if (app.key !in clusterApps) return@forEachIndexed
            val m = apps.indexOf(app)
            val dst = gridTopLeft(k)
            Box(
                Modifier
                    .offset {
                        val p = open.progress.value
                        val src = geo.appRect(open.tileIndex, m, apps.size)
                        val tl = libToScreen(src.topLeft)
                        IntOffset((tl.x + (dst.x - tl.x) * p).roundToInt(), (tl.y + (dst.y - tl.y) * p).roundToInt())
                    }
                    .graphicsLayer {
                        val p = open.progress.value
                        val from = geo.mini * model.libCategoryScale() / icon
                        val s = from + (1f - from) * p
                        scaleX = s; scaleY = s
                        transformOrigin = TransformOrigin(0f, 0f)
                    }
            ) {
                // Label gehört zur Gruppe: wächst mit deren Skala ein
                CategoryCell(app, shape) { (open.progress.value / LABEL_FADE).coerceIn(0f, 1f) }
            }
        }
    }
}

/** Icon + weißes Label (auf dem verwischten Hintergrund weiß, recording2 18,2 s). */
@Composable
private fun CategoryCell(app: AppEntry, shape: ContinuousRoundedShape, labelAlpha: () -> Float) {
    Layout(
        content = {
            io.github.janhurn9400.liquidy.ui.home.StyledAppIcon(app, Modifier.size(DesignTokens.iconSize.dp).clip(shape))
            BasicText(
                app.label,
                style = TextStyle(fontFamily = Inter, fontSize = DesignTokens.labelFontSize.value.sp, color = Color.White),
                maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.graphicsLayer { alpha = labelAlpha() },
            )
        },
    ) { ms, _ ->
        val iconP = ms[0].measure(Constraints())
        val label = ms[1].measure(Constraints(maxWidth = DesignTokens.labelWidth.dp.roundToPx()))
        val baseline = DesignTokens.labelBaseline.dp.roundToPx()
        layout(iconP.width, baseline + label.height - label[FirstBaseline]) {
            iconP.place(0, 0)
            label.place((iconP.width - label.width) / 2, baseline - label[FirstBaseline])
        }
    }
}

private const val TAP_SLOP = 0.2f    // × Icon-Größe
private const val GROUP_FADE = 0.12f // Gruppe ist ab diesem Fortschritt voll deckend (klein schon sichtbar, 17,59 s) [GEMESSEN grob]
private const val LABEL_FADE = 0.5f  // Labels der Cluster-Icons [GESCHÄTZT]

private const val DUO_CAT_SIDE = 65.1f // pt: Icon-Oberkante-links Spalte 1 (Mitte 95,1 − 30)
private const val DUO_CAT_PITCH = 88f
