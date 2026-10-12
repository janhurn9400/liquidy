package io.github.janhurn9400.liquidy.ui.library

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import io.github.janhurn9400.liquidy.data.AppEntry
import io.github.janhurn9400.liquidy.design.ContinuousRoundedShape
import io.github.janhurn9400.liquidy.design.DesignTokens
import io.github.janhurn9400.liquidy.glass.GlassSurface
import io.github.janhurn9400.liquidy.motion.Motion
import io.github.janhurn9400.liquidy.ui.Inter
import io.github.janhurn9400.liquidy.ui.home.HomeModel
import io.github.janhurn9400.liquidy.ui.home.LibraryCategory
import kotlin.math.roundToInt

/**
 * Geometrie der App-Mediathek (Inhaltskoordinaten = vor Scroll, Seiten-lokal), px. [GEMESSEN] recording2.
 * Kachel = 2×2 Quadranten à 64 pt: 3 große Icons + Mini-Cluster (2×2) unten rechts; ≤ 4 Apps → 4 große Icons.
 */
/**
 * [duo] = iPhone-Duo-Stil: die Mediathek ist eine Seitenbreite (448 pt) — aufgeklappt steht sie in der rechten Hälfte neben
 * der letzten Homescreen-Seite, zugeklappt füllt sie die Seite links der Dock-Spalte. Maße [GEMESSEN] Aufnahme 10-08 21:28,
 * 35,5 s: Kacheln 158,5 pt, Abstand 19, Rand 65,5 pt; Suchfeld 336 × 47,5 pt bei 54 pt; erste Kachelreihe bei 133 pt.
 */
class LibraryGeometry(private val density: Float, val width: Float, val height: Float, val duo: Boolean = false) {
    private fun px(v: Float) = v * density
    val densityValue: Float get() = density
    private val wPt get() = width / density
    private val unfolded get() = duo && width > height
    /**
     * Breite der Mediathek-Spalte in px, aus der Displaygröße (Duo-Werte als Anker, gilt für jedes Foldable):
     * aufgeklappt von der Mediathek-Kante (47,1 % der Breite) bis 8 pt vor die Dock-Spaltenmitte (Duo 448 pt);
     * zugeklappt Bildschirmbreite − 18 pt (Duo 448 pt).
     */
    val columnWidth get() = when {
        !duo -> width
        unfolded -> px((wPt - DUO_LIB_DOCK_FROM_RIGHT - DUO_LIB_DOCK_GAP) - wPt * DUO_LIB_LEFT_SHARE)
        else -> px(wPt - DUO_LIB_FOLDED_INSET)
    }
    private val colPt get() = columnWidth / density
    /** Seitenrand der Kacheln: 65,5 pt bei 448 pt Spalte, auf schmaleren Spalten anteilig (mind. 20 pt). */
    val side get() = if (duo) px((DUO_LIB_SIDE * colPt / DUO_LIB_WIDTH).coerceIn(20f, DUO_LIB_SIDE)) else px(DesignTokens.libSide.value)
    /** Duo-Liste/Suchfeld: Spalten-x-Werte relativ zur rechten Spaltenkante (gemessen bei 448 pt). */
    val duoFieldRight get() = colPt - (DUO_LIB_WIDTH - 338f)
    val duoCloseX get() = colPt - (DUO_LIB_WIDTH - 375f)
    val duoSepRight get() = colPt - (DUO_LIB_WIDTH - 400f)
    val gap get() = if (duo) px(DUO_LIB_GAP) else px(DesignTokens.libGap.value)
    val pad get() = px(DesignTokens.libTilePadding.value)
    val iconGap get() = px(DesignTokens.libTileIconGap.value)
    val tile get() = (columnWidth - 2 * side - gap) / 2
    val quadrant get() = (tile - 2 * pad - iconGap) / 2
    val mini get() = px(DesignTokens.libMini.value)
    val miniGap get() = px(DesignTokens.libMiniGap.value)
    private val miniInset get() = (quadrant - 2 * mini - miniGap) / 2

    /** Suchfeld in Ruhe (volle Breite). */
    val search: Rect
        get() {
            if (duo) return Rect(Offset(side, px(DUO_LIB_SEARCH_TOP)), Size(columnWidth - 2 * side, px(DUO_LIB_SEARCH_H)))
            val s = px(DesignTokens.libSearchSide.value)
            return Rect(Offset(s, px(DesignTokens.libSearchTop.value)), Size(width - 2 * s, px(DesignTokens.libSearchHeight.value)))
        }

    fun tileTopLeft(i: Int) = Offset(
        side + (i % 2) * (tile + gap),
        gridTop + (i / 2) * px(DesignTokens.libRowPitch.value),
    )

    private val gridTop get() = if (duo) px(DUO_LIB_GRID_TOP) else px(DesignTokens.libGridTop.value)

    fun quadrantTopLeft(k: Int) = Offset(pad + (k % 2) * (quadrant + iconGap), pad + (k / 2) * (quadrant + iconGap))

    fun miniTopLeft(m: Int) = quadrantTopLeft(3) + Offset(miniInset + (m % 2) * (mini + miniGap), miniInset + (m / 2) * (mini + miniGap))

    /** Rechteck der k-ten App einer Kategorie mit [count] Apps in Kachel [i] (Inhaltskoordinaten). Ab der 8. App: letzter Mini-Platz. */
    fun appRect(i: Int, k: Int, count: Int): Rect {
        val tl = tileTopLeft(i)
        return when {
            count <= 4 || k < 3 -> Rect(tl + quadrantTopLeft(k.coerceAtMost(3)), Size(quadrant, quadrant))
            else -> Rect(tl + miniTopLeft((k - 3).coerceAtMost(3)), Size(mini, mini))
        }
    }

    fun contentHeight(tiles: Int): Float {
        val rows = (tiles + 1) / 2
        return gridTop + rows * px(DesignTokens.libRowPitch.value)
    }

    fun maxScroll(tiles: Int) = (contentHeight(tiles) - height).coerceAtLeast(0f)
}

private const val DUO_LIB_WIDTH = 448f       // Referenz-Spaltenbreite (Duo)
private const val DUO_LIB_LEFT_SHARE = 0.4711f // = HomeGeometry DUO_SIDE_SHARE
private const val DUO_LIB_DOCK_FROM_RIGHT = 47f
private const val DUO_LIB_DOCK_GAP = 8f
private const val DUO_LIB_FOLDED_INSET = 18f
private const val DUO_LIB_SIDE = 65.5f
private const val DUO_LIB_GAP = 19f
private const val DUO_LIB_SEARCH_TOP = 54f
private const val DUO_LIB_SEARCH_H = 47.5f
private const val DUO_LIB_GRID_TOP = 133f

sealed interface LibraryHit {
    data object Search : LibraryHit
    data class App(val app: AppEntry, val screenRect: Rect) : LibraryHit
    data class Category(val index: Int) : LibraryHit
    data object Empty : LibraryHit
}

fun libraryHitTest(model: HomeModel, geo: LibraryGeometry, p: Offset, pageOffsetX: Float): LibraryHit {
    val scroll = model.libraryScroll.value
    if (geo.search.contains(p - Offset(pageOffsetX, 0f))) return LibraryHit.Search // Feld scrollt nicht mit
    val c = p - Offset(pageOffsetX, -scroll) // Inhaltskoordinate
    model.library.forEachIndexed { i, cat ->
        val tl = geo.tileTopLeft(i)
        if (!Rect(tl, Size(geo.tile, geo.tile)).contains(c)) return@forEachIndexed
        if (cat.isHidden) return LibraryHit.Empty
        for (k in 0 until 4) {
            val q = tl + geo.quadrantTopLeft(k)
            if (Rect(q, Size(geo.quadrant, geo.quadrant)).inflate(geo.iconGap / 2).contains(c)) {
                val toScreen = Offset(pageOffsetX, -scroll)
                return if (k == 3 && cat.apps.size > 4) LibraryHit.Category(i)
                else cat.apps.getOrNull(k)?.let { LibraryHit.App(it, Rect(q + toScreen, Size(geo.quadrant, geo.quadrant))) }
                    ?: LibraryHit.Empty
            }
        }
    }
    return LibraryHit.Empty
}

/**
 * Kacheln der Mediathek — liegen in der Glas-Quelle (das Suchfeld bricht sie, recording2 6,0 s).
 * Hinter der A–Z-Liste: unscharf + abgedunkelt (bleiben als Farbflecken sichtbar). Kategorie offen: blendet aus.
 */
@Composable
fun LibraryPage(model: HomeModel, geo: LibraryGeometry, wallpaper: ImageBitmap, density: Float, pageOffsetX: () -> Float) {
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationX = pageOffsetX()
                // Duo: hinter der Liste sind die Kacheln ganz weg (nur verwischtes Wallpaper) [Video 2, 18 s]
                alpha = model.libCategoryAlpha() * (if (geo.duo) model.libTiles.value.coerceIn(0f, 1f) else 1f)
                val s = model.libCategoryScale()
                scaleX = s; scaleY = s
                transformOrigin = TransformOrigin(geo.columnWidth / 2 / size.width, Motion.libCategoryPivotY.value)
                val r = (1f - model.libTiles.value) * DesignTokens.libListBlur.value * density
                renderEffect = if (r > 0.5f) BlurEffect(r, r, TileMode.Decal) else null
            }
    ) {
        Box(Modifier.fillMaxSize().graphicsLayer { translationY = -model.libraryScroll.value }) {
            model.library.forEachIndexed { i, cat ->
                val tl = geo.tileTopLeft(i)
                LibraryTile(cat, geo, wallpaper, density, hideCluster = model.openCategory?.tileIndex == i, modifier = Modifier.offset { IntOffset(tl.x.roundToInt(), tl.y.roundToInt()) })
            }
        }
    }
    // Abdunkeln hinter der Liste (in der Glas-Quelle → das Suchfeld bricht den abgedunkelten Hintergrund)
    if (model.libListOpen) {
        // Duo: ganzer Bildschirm abgedunkelt (sonst harte Kante an der Mediathek) [Video 2, 18 s]
        Canvas(Modifier.fillMaxSize().graphicsLayer { translationX = if (geo.duo) 0f else pageOffsetX() }) {
            drawRect(Color.Black.copy(alpha = model.libListDim.value * DesignTokens.libListDim.value))
        }
    }
}

@Composable
private fun LibraryTile(cat: LibraryCategory, geo: LibraryGeometry, wallpaper: ImageBitmap, density: Float, hideCluster: Boolean, modifier: Modifier) {
    val tileDp = Dp(geo.tile / density)
    val iconShape = ContinuousRoundedShape(DesignTokens.iconCorner.dp * (geo.quadrant / density / DesignTokens.iconSize.value))
    val miniShape = ContinuousRoundedShape(DesignTokens.iconCorner.dp * (geo.mini / density / DesignTokens.iconSize.value))
    Layout(
        modifier = modifier,
        content = {
            // Dasselbe Liquid Glass wie Dock/Ordner — Quelle nur das Wallpaper (Kacheln liegen selbst in der Glas-Quelle)
            io.github.janhurn9400.liquidy.ui.home.WithWallpaperGlass(wallpaper) {
            io.github.janhurn9400.liquidy.glass.LiquidGlass(
                shape = io.github.janhurn9400.liquidy.glass.glassRect(DesignTokens.libTileCorner.dp),
                modifier = Modifier.size(tileDp),
                thickness = DesignTokens.glassThicknessLarge.value,
                whiteTint = DesignTokens.libTileTint.value,
            ) {
                if (cat.isHidden) {
                    // „Hidden“: leere Platzhalter statt Icons (recording2 6,0 s)
                    Canvas(Modifier.fillMaxSize()) {
                        val fill = Color.White.copy(alpha = HIDDEN_PLACEHOLDER_ALPHA)
                        val r = DesignTokens.iconCorner.value * density * geo.quadrant / (DesignTokens.iconSize.value * density)
                        for (k in 0 until 3) drawRoundRect(fill, geo.quadrantTopLeft(k), Size(geo.quadrant, geo.quadrant), CornerRadius(r))
                        for (m in 0 until 4) drawRoundRect(fill, geo.miniTopLeft(m), Size(geo.mini, geo.mini), CornerRadius(r * geo.mini / geo.quadrant))
                    }
                } else {
                    val big = if (cat.apps.size > 4) 3 else 4
                    cat.apps.take(big).forEachIndexed { k, app ->
                        val q = geo.quadrantTopLeft(k)
                        io.github.janhurn9400.liquidy.ui.home.StyledAppIcon(app, Modifier
                            .offset { IntOffset(q.x.roundToInt(), q.y.roundToInt()) }
                            .size(Dp(geo.quadrant / density))
                            .clip(iconShape))
                    }
                    // Offene Kategorie: Cluster ist leer — seine Icons sind ins Raster geflogen
                    if (cat.apps.size > 4 && !hideCluster) {
                        cat.apps.drop(3).take(4).forEachIndexed { m, app ->
                            val o = geo.miniTopLeft(m)
                            io.github.janhurn9400.liquidy.ui.home.StyledAppIcon(app, Modifier
                                .offset { IntOffset(o.x.roundToInt(), o.y.roundToInt()) }
                                .size(Dp(geo.mini / density))
                                .clip(miniShape))
                        }
                    }
                }
            }
            }
            BasicText(
                cat.name,
                style = TextStyle(fontFamily = Inter, fontSize = DesignTokens.labelFontSize.value.sp, color = DesignTokens.labelColor, shadow = DesignTokens.labelShadow),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        },
    ) { m, _ ->
        val t = m[0].measure(Constraints())
        val l = m[1].measure(Constraints(maxWidth = t.width))
        val baseline = t.height + DesignTokens.libLabelGap.dp.roundToPx()
        layout(t.width, baseline + l.height - l[FirstBaseline]) {
            t.place(0, 0)
            l.place((t.width - l.width) / 2, baseline - l[FirstBaseline])
        }
    }
}

private const val HIDDEN_PLACEHOLDER_ALPHA = 0.18f // [GESCHÄTZT] recording2 6,0 s
