package io.github.janhurn9400.liquidy.ui.home

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import io.github.janhurn9400.liquidy.design.DesignTokens
import kotlin.math.roundToInt

/**
 * All pixel positions of the home screen from DesignTokens (pt = dp). The single source for drawing
 * AND hit testing, so the two never drift apart. Reads tokens on every access → live tunable.
 */
/** [navBarPx] = height of the Android gesture bar at the bottom (px); the dock stays above it with a gap. */
class HomeGeometry(
    private val density: Float, val width: Float, val height: Float, private val navBarPx: Float = 0f,
    /** iPhone Duo style (foldable): pages on the left, on the right a vertical column with dock, magnifier and edit buttons. */
    val duo: Boolean = false,
    /** Display cutouts (px, window = screen coordinates); the edit buttons move beside a punch-hole camera. */
    private val cutouts: List<Rect> = emptyList(),
) {

    private fun px(dp: Float) = dp * density

    /** Unfolded = landscape → two pages side by side (Duo). */
    val unfolded: Boolean get() = duo && width > height

    /** Width of a home page (paging step): unfolded 448 pt (two pages visible), otherwise screen width. */
    // ── Unfolded: everything derived from the display size (applies to every foldable) ─────────────────────────────
    // [MEASURED Duo, recording 19:18, real display edges, 0.729 pt/px]: the two grids lie symmetrically around the
    // centre of the area left of the dock (434 pt = 48 % of the dock column centre; the page dots sit there too), their
    // centres 397 pt apart (41.8 % of the width). The paths out of view are computed from width + grid extent,
    // so a page disappears completely on EVERY display before it's dropped.
    private val wPt get() = width / density
    /** Centre between the two grids (pt). */
    private val openCenter get() = (wPt - DUO_COLUMN_FROM_RIGHT) * DUO_CONTENT_CENTER
    /** Distance of the grid centres = shift per page (pt); at least large enough that the grids don't touch. */
    private val openStride get() = maxOf(wPt * DUO_STRIDE_SHARE, 3 * DUO_COL_PITCH + DesignTokens.iconSize.value + DUO_MIN_GRID_GAP)
    /**
     * Column pitch unfolded: 88 pt like on the Duo; reduced on narrow displays so far that both grids including
     * labels fit between the left edge and the dock.
     */
    private val openColPitch: Float get() {
        val dockLeft = wPt - DUO_COLUMN_FROM_RIGHT - DUO_DOCK_W / 2
        val byRight = (dockLeft - DUO_OUT_MARGIN * 2 - DUO_LABEL_HALF - (openCenter + openStride / 2)) / 1.5f
        val byLeft = (openCenter - openStride / 2 - DUO_LABEL_HALF - DUO_OUT_MARGIN) / 1.5f
        return minOf(DUO_COL_PITCH, byRight, byLeft).coerceAtLeast(DesignTokens.iconSize.value + 8f)
    }
    /** Half grid width incl. labels (pt) — a page has to go this far beyond the edge. */
    private val gridExtent get() = 1.5f * colPitch + DUO_LABEL_HALF
    private val leftGridCenter get() = openCenter - openStride / 2
    private val rightGridCenter get() = openCenter + openStride / 2
    /** Out to the left: the right edge of the left grid must go behind x = 0 (Duo ≈ 416 pt). */
    private val outLeft get() = leftGridCenter + gridExtent + DUO_OUT_MARGIN
    /** Out to the right: the left edge of the right grid far beyond the edge (Duo ≈ 666 pt, whooshes behind the dock). */
    private val outRight get() = (wPt - (rightGridCenter - gridExtent)) * DUO_OUT_RIGHT_FACTOR

    val pageWidth: Float get() = if (unfolded) px(openStride) else width

    /** How many pages are visible at the same time. */
    val pagesVisible: Int get() = if (unfolded) 2 else 1
    /**
     * Horizontal shift of a page that is [rel] positions right of the current one (px). Unfolded piecewise:
     * left ↔ right = grid distance, out to the left [outLeft], out to the right [outRight] (faster).
     * Folded: screen width per page.
     */
    fun pageShift(rel: Float): Float {
        if (!unfolded) return rel * width
        return when {
            rel < 0f -> rel * px(outLeft)
            rel <= 1f -> rel * px(openStride)
            else -> px(openStride) + (rel - 1f) * px(outRight)
        }
    }
    /** Shift per page for the Today page and App Library (unfolded 47 % of the width, Duo 448 pt). */
    val sideStride: Float get() = if (unfolded) width * DUO_SIDE_SHARE else width
    fun libraryOffset(pagesFromPos: Float): Float = pagesFromPos * sideStride
    /** Folder unfolded: centred above the grid of the half the folder icon is in (dock → right), px. */
    // Half by icon CENTRE and the centre between both grids — not width/2 (includes the dock column; the left column
    // of the right page was otherwise still "left")
    fun duoFolderCenterX(iconX: Float): Float =
        px(if (iconX + icon / 2 < px((leftGridCenter + rightGridCenter) / 2)) leftGridCenter else rightGridCenter)
    val duoColPitchPt: Float get() = colPitch
    /** Left edge of the dock glass bar (pt). */
    val duoDockLeftPt: Float get() = wPt - DUO_COLUMN_FROM_RIGHT - DUO_DOCK_W / 2
    val duoRowPitchPt: Float get() = rowPitch
    /** Page dots unfolded: x centre normally or with the Today page open (below the right grid), px. */
    val duoDotsHomeX: Float get() = px(openCenter)
    val duoDotsTodayX: Float get() = px(rightGridCenter)

    private val colPitch get() = if (unfolded) openColPitch else if (duo) DUO_COL_PITCH else DesignTokens.gridColPitch.value
    private val rowPitch get() = if (duo) DUO_ROW_PITCH else DesignTokens.gridRowPitch.value
    // Taller than the Duo (unfolded or folded) (e.g. Z Fold after matching the width, design/UiScale): grid vertically
    // centred in the extra space instead of sticking to the top
    private val row0 get() = DUO_ROW0 + ((height / density - if (unfolded) DUO_HEIGHT_PT else DUO_FOLDED_HEIGHT_PT) / 2f).coerceAtLeast(0f)
    private val small get() = colPitch + DesignTokens.iconSize.value + 2 * DUO_WIDGET_INSET
    /** Centre of the first column / row (pt). */
    private val firstColCenter get() = if (unfolded) leftGridCenter - 1.5f * colPitch else DUO_COL0_FOLDED
    private val duoColumnX get() = width - px(DUO_COLUMN_FROM_RIGHT) // centre of the right column

    val icon: Float get() = px(DesignTokens.iconSize.value)
    val perPage: Int get() = DesignTokens.GRID_COLUMNS * DesignTokens.GRID_ROWS

    /** Icon top-left of a slot, page-local (page 0 at pager position 0). */
    fun slotPosition(slot: Int): Offset {
        val col = slot % DesignTokens.GRID_COLUMNS
        val row = slot / DesignTokens.GRID_COLUMNS
        if (duo) return Offset(px(firstColCenter + colPitch * col) - icon / 2, px(row0 + rowPitch * row) - icon / 2)
        return Offset(
            px(DesignTokens.gridSide.value + DesignTokens.gridColPitch.value * col),
            px(DesignTokens.gridTop.value + DesignTokens.gridRowPitch.value * row),
        )
    }

    /** Nearest slot to the icon centre [center] (page-local); limited to [count] (= behind the last icon). */
    fun slotAt(center: Offset, count: Int): Int {
        if (duo) {
            val c = ((center.x - px(firstColCenter)) / px(colPitch)).roundToInt().coerceIn(0, DesignTokens.GRID_COLUMNS - 1)
            val r = ((center.y - px(row0)) / px(rowPitch)).roundToInt().coerceIn(0, DesignTokens.GRID_ROWS - 1)
            return (r * DesignTokens.GRID_COLUMNS + c).coerceAtMost(count)
        }
        val col = ((center.x - px(DesignTokens.gridSide.value) - icon / 2) / px(DesignTokens.gridColPitch.value))
            .roundToInt().coerceIn(0, DesignTokens.GRID_COLUMNS - 1)
        val row = ((center.y - px(DesignTokens.gridTop.value) - icon / 2) / px(DesignTokens.gridRowPitch.value))
            .roundToInt().coerceIn(0, DesignTokens.GRID_ROWS - 1)
        return (row * DesignTokens.GRID_COLUMNS + col).coerceAtMost(count)
    }

    /** Hit area of a cell: icon plus label area below it. */
    fun cellHitRect(iconTopLeft: Offset): Rect {
        val padX = (px(colPitch) - icon) / 2
        val bottom = iconTopLeft.y + px(rowPitch) - padX
        return Rect(iconTopLeft.x - padX, iconTopLeft.y, iconTopLeft.x + icon + padX, bottom)
    }

    // ── Widgets ───────────────────────────────────────────────────────────────
    /** Edge length of a widget with [cols]×[rows] cells (small 164, wide 349.33; height 4 rows = 2·164 + row gap). */
    fun widgetSize(cols: Int, rows: Int): Size {
        if (duo) { // Duo: small = gap + icon + 2·4.5 pt (measured 157 pt), height 4 rows = small + 2 row pitches
            val w = if (cols >= 4) 2 * colPitch + small else small
            val h = if (rows >= 4) small + 2 * rowPitch else small
            return Size(px(w), px(h))
        }
        val w = if (cols >= 4) DesignTokens.widgetWide.value else DesignTokens.widgetSmall.value
        val h = if (rows >= 4) 2 * DesignTokens.widgetSmall.value + (2 * DesignTokens.gridRowPitch.value - DesignTokens.widgetSmall.value)
            else DesignTokens.widgetSmall.value
        return Size(px(w), px(h))
    }

    /** Widget top-left relative to the icon top-left of its first cell. */
    val widgetOffset: Offset get() = if (duo) Offset(-(px(small - colPitch) - icon) / 2, -(px(small - rowPitch) - icon) / 2 + px(DUO_WIDGET_DY))
        else Offset(-px(DesignTokens.widgetOverhang.value), px(DesignTokens.widgetTopOffset.value))

    fun widgetRect(cellTopLeft: Offset, cols: Int, rows: Int) = Rect(cellTopLeft + widgetOffset, widgetSize(cols, rows))

    // ── Dock ──────────────────────────────────────────────────────────────────
    /** Number of dock icons (for the vertical Duo dock, whose height depends on it) — set by HomeScreen. */
    var dockCount: Int = 4

    val dockRect: Rect get() = dockRectFor(dockCount.toFloat())

    /** Dock glass for [slots] slots — Duo: fractional, so the bar grows/shrinks smoothly when adding/removing. */
    fun dockRectFor(slots: Float): Rect {
        if (duo) { // vertical glass bar in the right column, centre at 56.7 % of the height
            // 4 slots → 263 pt, 1 slot → 71 pt [MEASURED]: h = pitch·(n−1) + 71
            val n = slots.coerceAtLeast(1f)
            val h = px(DUO_DOCK_PITCH * (n - 1) + DUO_DOCK_SINGLE)
            val w = px(DUO_DOCK_W); val cy = height * DUO_DOCK_CY
            return Rect(duoColumnX - w / 2, cy - h / 2, duoColumnX + w / 2, cy + h / 2)
        }
        val side = px(DesignTokens.dockSideInset.value)
        val h = px(DesignTokens.dockHeight.value)
        // iPhone: 17 pt gap (there without a home bar on the home screen). Android always shows the gesture bar →
        // dock at least DOCK_ABOVE_NAV pt above it, otherwise the bar sticks to the dock
        val bottom = height - maxOf(px(DesignTokens.dockBottomInset.value), navBarPx + px(DOCK_ABOVE_NAV))
        return Rect(side, bottom - h, width - side, bottom)
    }

    /** Duo: how many dock slots fit between the brush button (top) and the search button (bottom), with gap [DUO_DOCK_CLEAR]. */
    val duoDockCapacity: Int
        get() {
            val cy = height * DUO_DOCK_CY
            val half = minOf(cy - editButtonRect(left = true).bottom, pillRect.top - cy) - px(DUO_DOCK_CLEAR)
            return (((2 * half - px(DUO_DOCK_SINGLE)) / px(DUO_DOCK_PITCH)).toInt() + 1).coerceIn(DesignTokens.DOCK_SLOTS, DUO_DOCK_SLOTS_LIMIT)
        }

    /**
     * FIXED RULE: size of an icon/folder in the dock relative to the grid icon — Duo ≈ 48 instead of 60 pt [MEASURED 21:29, 44 s],
     * otherwise equal. Everything that shows a dock item or starts from it (drawing, folder open/close, context menu,
     * app launch/return, dragging) uses this value.
     */
    val dockIconScale: Float get() = if (duo) DUO_DOCK_ICON / DesignTokens.iconSize.value else 1f

    /** Visible icon area of an item with cell top-left [topLeft]; shrunk around the icon centre in the dock. */
    fun iconRect(topLeft: Offset, inDock: Boolean): Rect {
        val s = if (inDock) dockIconScale else 1f
        val c = topLeft + Offset(icon / 2, icon / 2)
        return Rect(c - Offset(icon * s / 2, icon * s / 2), Size(icon * s, icon * s))
    }

    /** Dock icons are centred as a group (iOS: 3 icons → centred). */
    fun dockPosition(index: Int, count: Int): Offset {
        if (duo) {
            val cy = height * DUO_DOCK_CY + px(DUO_DOCK_PITCH) * (index - (count - 1) / 2f)
            return Offset(duoColumnX - icon / 2, cy - icon / 2)
        }
        val pitch = px(DesignTokens.dockIconPitch.value)
        val cx = width / 2 + pitch * (index - (count - 1) / 2f)
        val d = dockRect
        return Offset(cx - icon / 2, d.top + (d.height - icon) / 2)
    }

    /** Is [p] in the dock area (drop)? iPhone: below the dock top; Duo: right column. */
    fun inDockZone(p: Offset): Boolean = if (duo) dockRect.inflate(px(DUO_DOCK_HIT)).contains(p) else p.y >= dockRect.top

    /** Insertion position in the dock for the icon centre [c]. */
    fun dockIndexAt(c: Offset, count: Int): Int {
        if (!duo) return dockIndexAt(c.x, count)
        val pitch = px(DUO_DOCK_PITCH)
        val first = height * DUO_DOCK_CY - pitch * count / 2f
        return ((c.y - first) / pitch).toInt().coerceIn(0, count)
    }

    fun dockIndexAt(x: Float, count: Int): Int {
        val pitch = px(DesignTokens.dockIconPitch.value)
        val first = width / 2 - pitch * count / 2f // count+1 slots when inserting
        return ((x - first) / pitch).toInt().coerceIn(0, count)
    }

    // ── Pill ──────────────────────────────────────────────────────────────────
    val pillTop: Float get() = dockRect.top - px(DesignTokens.pillGapToDock.value + DesignTokens.pillHeight.value)
    val pillRect: Rect
        get() {
            if (duo) { // round search button at the bottom of the right column
                val d = px(DUO_SEARCH_D); val cy = height - px(DUO_SEARCH_FROM_BOTTOM)
                return Rect(Offset(duoColumnX - d / 2, cy - d / 2), Size(d, d))
            }
            val w = px(DesignTokens.pillWidth.value)
            return Rect(Offset((width - w) / 2, pillTop), Size(w, px(DesignTokens.pillHeight.value)))
        }

    // ── Edit mode ──────────────────────────────────────────────────────
    fun badgeCenter(iconTopLeft: Offset): Offset =
        iconTopLeft + Offset(px(DesignTokens.badgeOffsetX.value), px(DesignTokens.badgeOffsetY.value))

    val badgeHitRadius: Float get() = px(DesignTokens.badgeSize.value / 2 + DesignTokens.badgeHitSlop.value)

    /** Duo search open: field/suggestions as a centred column (unfolded 428 pt, centre = screen centre) [MEASURED] 42 s. */
    val duoSearchWidth: Float get() = if (unfolded) px(DUO_SEARCH_FIELD_W) else (duoColumnX - px(DUO_DOCK_W) / 2 - px(16f)) - px(16f)
    /** Centre y of the Duo search field without keyboard (unfolded 636 pt of 669 = bottom centre). */
    val duoSearchRestY: Float get() = height - px(DUO_SEARCH_FIELD_FROM_BOTTOM)
    val duoSearchCenterX: Float get() = if (unfolded) width / 2 else px(16f) + duoSearchWidth / 2

    /** Duo: page dots below the grid (folded centred below the page, unfolded below the right page). */
    val duoDotsCenter: Offset get() = Offset(if (unfolded) px(rightGridCenter) else px(DUO_COL0_FOLDED + 1.5f * DUO_COL_PITCH), height - maxOf(px(DUO_DOTS_FROM_BOTTOM), navBarPx + px(DUO_DOTS_ABOVE_NAV)))

    fun editButtonRect(left: Boolean): Rect {
        if (duo) { // vertically in the right column: ✓ on top, brush below
            val d = px(DUO_EDIT_D); val cy = px(if (left) DUO_EDIT_BRUSH_Y else DUO_EDIT_DONE_Y)
            return Rect(Offset(duoColumnX - d / 2, cy - d / 2), Size(d, d))
        }
        val w = px(DesignTokens.editButtonWidth.value)
        val h = px(DesignTokens.editButtonHeight.value)
        val top = px(DesignTokens.editButtonTop.value)
        val side = px(DesignTokens.editButtonSide.value)
        var x = if (left) side else width - side - w
        // Punch-hole camera under the capsule (e.g. Mi 11 Lite: top left) → move it beside the camera, towards the centre
        val gap = px(DesignTokens.editButtonCutoutGap.value)
        cutouts.firstOrNull { it.overlaps(Rect(Offset(x, top), Size(w, h)).inflate(gap)) }?.let { c ->
            x = if (left) c.right + gap else c.left - gap - w
        }
        return Rect(Offset(x, top), Size(w, h))
    }

    /** Touch zone of an edit button (non-Duo): the capsule grown to at least T-EDIT-BTN.hit tall, plus the same slop sideways. */
    fun editButtonHitRect(left: Boolean): Rect {
        val r = editButtonRect(left)
        if (duo) return r
        val slop = maxOf(0f, (px(DesignTokens.editButtonHitHeight.value) - r.height) / 2)
        return Rect(r.left - slop, 0f, r.right + slop, r.bottom + slop)
    }

    val edgeZone: Float get() = px(DesignTokens.edgeZone.value)
    /** Right edge of the page area (px): Duo = left of the dock column (dragging changes the page there), otherwise screen edge. */
    val pagesRightEdge: Float get() = if (duo) duoColumnX - px(DUO_DOCK_W) else width
}

private const val DOCK_ABOVE_NAV = 8f // pt gap between dock and gesture bar area [ESTIMATED]

// iPhone Duo dimensions [MEASURED] Device Hub recordings 10-08 21:28/21:29 (simulator iPhone Duo, iOS 27.1; 0.634 pt/px)
private const val DUO_CONTENT_CENTER = 0.4803f // grid centre ÷ dock column centre (434 / 904 pt)
private const val DUO_STRIDE_SHARE = 0.4176f   // grid distance ÷ width (397 / 951 pt)
private const val DUO_SIDE_SHARE = 0.4711f     // Today/App Library per page ÷ width (448 / 951 pt)
private const val DUO_MIN_GRID_GAP = 40f
private const val DUO_LABEL_HALF = 44f         // half label width beyond the column centre
private const val DUO_OUT_MARGIN = 4f
private const val DUO_OUT_RIGHT_FACTOR = 1.345f // Duo: 666 pt instead of the required 495 pt
private const val DUO_WIDGET_INSET = 4.5f
private const val DUO_COL_PITCH = 88f
private const val DUO_ROW_PITCH = 98.7f
private const val DUO_HEIGHT_PT = 667f // Duo inner display unfolded
private const val DUO_FOLDED_HEIGHT_PT = 678f // Duo outer display
private const val DUO_COL0_FOLDED = 57.2f      // centre of column 1 folded
private const val DUO_ROW0 = 83.6f             // centre of row 1
private const val DUO_WIDGET_DY = 0f
private const val DUO_COLUMN_FROM_RIGHT = 47f  // centre of the right column (dock, magnifier, buttons)
private const val DUO_DOCK_W = 73f
private const val DUO_DOCK_HIT = 14f          // pt hit margin around the dock glass (dropping into the dock) [ESTIMATED]
private const val DUO_DOCK_ICON = 48f        // pt icon size in the Duo dock (grid 60 pt)
private const val DUO_DOCK_SINGLE = 71f       // height of the glass area with one icon
private const val DUO_DOCK_PITCH = 64f
private const val DUO_DOCK_CY = 0.567f         // dock centre as a share of the height
private const val DUO_DOCK_CLEAR = 16f        // pt minimum gap of the extended dock to brush/search button [ESTIMATED]
/** Upper limit for "More Apps in Dock" (own extension, the Duo has at most 4). */
const val DUO_DOCK_SLOTS_LIMIT = 8
private const val DUO_SEARCH_D = 48f
private const val DUO_SEARCH_FIELD_W = 428f
private const val DUO_SEARCH_FIELD_FROM_BOTTOM = 33f
private const val DUO_SEARCH_FROM_BOTTOM = 49f
private const val DUO_DOTS_FROM_BOTTOM = 26f
private const val DUO_DOTS_ABOVE_NAV = 8f   // keep the Android gesture bar free (doesn't exist on the Duo)
private const val DUO_EDIT_D = 48f
private const val DUO_EDIT_DONE_Y = 51.7f   // [MEASURED] 21:28, 53.3 s (unfolded)
private const val DUO_EDIT_BRUSH_Y = 115.1f
