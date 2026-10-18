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
import androidx.compose.ui.draw.drawWithContent
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
 * Geometry of the App Library (content coordinates = before scroll, page-local), px. [MEASURED] recording2.
 * Tile = 2×2 quadrants of 64 pt: 3 large icons + mini cluster (2×2) bottom right; ≤ 4 apps → 4 large icons.
 */
/**
 * [duo] = iPhone Duo style: the App Library is one page wide (448 pt) — unfolded it stands in the right half next to
 * the last home page, folded it fills the page left of the dock column. Dimensions [MEASURED] recording 10-08 21:28,
 * 35.5 s: tiles 158.5 pt, gap 19, margin 65.5 pt; search field 336 × 47.5 pt at 54 pt; first tile row at 133 pt.
 */
class LibraryGeometry(private val density: Float, val width: Float, val height: Float, val duo: Boolean = false) {
    private fun px(v: Float) = v * density
    val densityValue: Float get() = density
    private val wPt get() = width / density
    private val unfolded get() = duo && width > height
    /**
     * Width of the App Library column in px, from the display size (Duo values as anchors, applies to every foldable):
     * unfolded from the App Library edge (47.1 % of the width) to 8 pt before the dock column centre (Duo 448 pt);
     * folded screen width − 18 pt (Duo 448 pt).
     */
    val columnWidth get() = when {
        !duo -> width
        unfolded -> px((wPt - DUO_LIB_DOCK_FROM_RIGHT - DUO_LIB_DOCK_GAP) - wPt * DUO_LIB_LEFT_SHARE)
        else -> px(wPt - DUO_LIB_FOLDED_INSET)
    }
    private val colPt get() = columnWidth / density
    /** Side margin of the tiles: 65.5 pt for a 448 pt column, proportionally on narrower columns (min. 20 pt). */
    val side get() = if (duo) px((DUO_LIB_SIDE * colPt / DUO_LIB_WIDTH).coerceIn(20f, DUO_LIB_SIDE)) else px(DesignTokens.libSide.value)
    /** Duo list/search field: column x values relative to the right column edge (measured at 448 pt). */
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

    /** Search field at rest (full width). */
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

    /** Rect of the k-th app of a category with [count] apps in tile [i] (content coordinates). From the 8th app: last mini slot. */
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

private const val DUO_LIB_WIDTH = 448f       // reference column width (Duo)
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
    if (geo.search.contains(p - Offset(pageOffsetX, 0f))) return LibraryHit.Search // the field doesn't scroll along
    val c = p - Offset(pageOffsetX, -scroll) // content coordinate
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
 * Tiles of the App Library — they lie in the glass source (the search field refracts them, recording2 6.0 s).
 * Behind the A–Z list: blurred + dimmed (stay visible as colour patches). Category open: fades out.
 */
@Composable
fun LibraryPage(model: HomeModel, geo: LibraryGeometry, wallpaper: ImageBitmap, density: Float, pageOffsetX: () -> Float) {
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationX = pageOffsetX()
                // Duo: behind the list the tiles are completely gone (only blurred wallpaper) [video 2, 18 s]
                alpha = model.libCategoryAlpha() * (if (geo.duo) model.libTiles.value.coerceIn(0f, 1f) else 1f)
                val s = model.libCategoryScale()
                scaleX = s; scaleY = s
                transformOrigin = TransformOrigin(geo.columnWidth / 2 / size.width, Motion.libCategoryPivotY.value)
                val r = if (io.github.janhurn9400.liquidy.tuning.DebugOptions.blurOff) 0f else (1f - model.libTiles.value) * DesignTokens.libListBlur.value * density
                renderEffect = if (r > 0.5f) BlurEffect(r, r, TileMode.Decal) else null
            }
    ) {
        Box(Modifier.fillMaxSize().graphicsLayer { translationY = -model.libraryScroll.value }) {
            model.library.forEachIndexed { i, cat ->
                val tl = geo.tileTopLeft(i)
                LibraryTile(model, cat, geo, wallpaper, density, hideCluster = model.openCategory?.tileIndex == i, modifier = Modifier.offset { IntOffset(tl.x.roundToInt(), tl.y.roundToInt()) })
            }
        }
    }
    // Dimming behind the list (in the glass source → the search field refracts the dimmed background)
    if (model.libListOpen) {
        // Duo: whole screen dimmed (otherwise a hard edge at the App Library) [video 2, 18 s]
        Canvas(Modifier.fillMaxSize().graphicsLayer { translationX = if (geo.duo) 0f else pageOffsetX() }) {
            drawRect(Color.Black.copy(alpha = model.libListDim.value * DesignTokens.libListDim.value))
        }
    }
}

@Composable
private fun LibraryTile(model: io.github.janhurn9400.liquidy.ui.home.HomeModel, cat: LibraryCategory, geo: LibraryGeometry, wallpaper: ImageBitmap, density: Float, hideCluster: Boolean, modifier: Modifier) {
    val tileDp = Dp(geo.tile / density)
    val iconShape = ContinuousRoundedShape(DesignTokens.iconCorner.dp * (geo.quadrant / density / DesignTokens.iconSize.value))
    val miniShape = ContinuousRoundedShape(DesignTokens.iconCorner.dp * (geo.mini / density / DesignTokens.iconSize.value))
    Layout(
        modifier = modifier,
        content = {
            // The same Liquid Glass as dock/folder — source is only the wallpaper (the tiles themselves lie in the glass source)
            // Fast variant (no per-frame backdrop layers) — swiping into the App Library stuttered with ~10 Kyant tiles
            io.github.janhurn9400.liquidy.glass.WallpaperGlass(
                wallpaper = wallpaper,
                shape = io.github.janhurn9400.liquidy.glass.glassRect(DesignTokens.libTileCorner.dp),
                modifier = Modifier.size(tileDp),
                thickness = DesignTokens.glassThicknessLarge.value,
                whiteTint = DesignTokens.libTileTint.value,
            ) {
                if (cat.isHidden) {
                    // "Hidden": empty placeholders instead of icons (recording2 6.0 s)
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
                            .graphicsLayer {
                                val lk = io.github.janhurn9400.liquidy.ui.home.libraryMenuKey(app.key)
                                // like a home screen slot: hidden while flying back or while its context menu shows the lifted copy
                                alpha = if (io.github.janhurn9400.liquidy.ui.home.LibraryFlying.key == app.key || model.contextMenu?.key == lk) 0f else 1f
                                // grows while held (M-CTXMENU), the lifted menu icon takes over from this size
                                val sc = if (model.pressScaleKey == lk) model.pressScale.value else 1f
                                scaleX = sc; scaleY = sc
                            }
                            .size(Dp(geo.quadrant / density))
                            .clip(iconShape)
                            // held: darkens like a home screen icon (pressedKey set by the gesture arena)
                            .drawWithContent {
                                drawContent()
                                if (model.pressedKey == io.github.janhurn9400.liquidy.ui.home.libraryMenuKey(app.key))
                                    drawRect(androidx.compose.ui.graphics.Color.Black.copy(alpha = model.pressAlpha.value))
                            })
                    }
                    // Open category: the cluster is empty — its icons have flown into the grid
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

private const val HIDDEN_PLACEHOLDER_ALPHA = 0.18f // [ESTIMATED] recording2 6.0 s
