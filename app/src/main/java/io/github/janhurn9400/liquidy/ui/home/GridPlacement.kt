package io.github.janhurn9400.liquidy.ui.home

import io.github.janhurn9400.liquidy.design.DesignTokens

/**
 * Grid placement like iOS: items in order into the first free spot (row by row) they fit into.
 * Icons occupy 1 cell, widgets 2×2 / 4×2 / 4×4 and snap like on iOS only into column 0 or 2 and into the row pairs
 * (row 1, 3, 5) — no single app row above a widget (user 10-14); can be relaxed via Quirks.freeWidgetRows.
 * cell = row · columns + column.
 */
object GridPlacement {
    private const val COLS = DesignTokens.GRID_COLUMNS
    private const val ROWS = DesignTokens.GRID_ROWS
    /** Multi-row widgets only in even rows (iOS). */
    private val rowPairs get() = !io.github.janhurn9400.liquidy.data.Quirks.freeWidgetRows

    /** Cell per key (order like [keys]); -1 = doesn't fit on the page anymore. */
    fun place(keys: List<String>, size: (String) -> Pair<Int, Int>): IntArray {
        val used = Array(ROWS) { BooleanArray(COLS) }
        return IntArray(keys.size) { i ->
            val (c, r) = size(keys[i])
            var found = -1
            loop@ for (row in 0..ROWS - r) for (col in 0..COLS - c) {
                if (c > 1 && col % 2 != 0) continue
                if (r > 1 && rowPairs && row % 2 != 0) continue
                if ((row until row + r).all { y -> (col until col + c).none { x -> used[y][x] } }) {
                    for (y in row until row + r) for (x in col until col + c) used[y][x] = true
                    found = row * COLS + col
                    break@loop
                }
            }
            found
        }
    }

    fun fits(keys: List<String>, size: (String) -> Pair<Int, Int>) = place(keys, size).none { it < 0 }

    // ── Free placement (current iOS: icons stay where you drop them — even with gaps) ─────────────────
    // Empty cells are invisible placeholders ("gap:…", 1×1) in the page list. Because [place] takes the first free spot
    // in order, a row-sorted list with placeholders pins down every position exactly.
    // Display, hits and animations never see the placeholders (HomeModel.display filters them).

    const val GAP_PREFIX = "gap:"
    fun isGap(key: String) = key.startsWith(GAP_PREFIX)
    fun newGap() = GAP_PREFIX + java.util.UUID.randomUUID().toString().take(12)
    /** Page without real items (only placeholders or empty). */
    fun isBlank(keys: List<String>) = keys.all(::isGap)
    /** Placeholders at the end have no effect → remove. */
    fun trim(keys: List<String>) = keys.dropLastWhile(::isGap)
    fun stripGaps(keys: List<String>) = keys.filterNot(::isGap)

    /** Cells of the real items (same order as [stripGaps]). */
    fun placeReal(keys: List<String>, size: (String) -> Pair<Int, Int>): IntArray {
        val all = place(keys, size)
        return keys.indices.filter { !isGap(keys[it]) }.map { all[it] }.toIntArray()
    }

    /** List from fixed positions (key → top-left cell): row by row, free cells before them as placeholders. */
    fun canonical(items: List<Pair<String, Int>>, size: (String) -> Pair<Int, Int>): List<String> {
        if (items.isEmpty()) return emptyList()
        val covered = BooleanArray(COLS * ROWS)
        val topLeft = HashMap<Int, String>()
        items.forEach { (k, cell) ->
            topLeft[cell] = k
            val (c, r) = size(k)
            for (y in 0 until r) for (x in 0 until c) (cell + y * COLS + x).takeIf { it < covered.size }?.let { covered[it] = true }
        }
        val last = items.maxOf { it.second }
        val out = ArrayList<String>()
        for (cell in 0..last) {
            topLeft[cell]?.let { out += it } ?: run { if (!covered[cell]) out += newGap() }
        }
        return out
    }

    /**
     * Put [key] at cell [cell] (widgets snap into column 0/2 and stay in the grid):
     *  - target area free → there, all others stay put,
     *  - occupied → insert before the item at this spot; the following ones only move up to the next gap
     *    (the gap is used up), otherwise onto the next page as before (caller: [flow]).
     */
    fun placeAt(keys: List<String>, key: String, cell: Int, size: (String) -> Pair<Int, Int>): List<String> {
        val (c, r) = size(key)
        var col = (cell % COLS).coerceIn(0, COLS - 1)
        var row = (cell / COLS).coerceIn(0, ROWS - r)
        if (c > 1) col = (col / 2 * 2).coerceAtMost(COLS - c)
        if (r > 1 && rowPairs) row = (row / 2 * 2).coerceAtMost(ROWS - r)
        val target = row * COLS + col
        val cells = place(keys, size)
        val occ = arrayOfNulls<String>(COLS * ROWS)
        val items = ArrayList<Pair<String, Int>>()
        val overflow = ArrayList<String>()
        keys.forEachIndexed { i, k ->
            if (isGap(k)) return@forEachIndexed
            val at = cells[i]
            if (at < 0) { overflow += k; return@forEachIndexed }
            items += k to at
            val (kc, kr) = size(k)
            for (y in 0 until kr) for (x in 0 until kc) (at + y * COLS + x).takeIf { it < occ.size }?.let { occ[it] = k }
        }
        val region = (0 until r).flatMap { y -> (0 until c).map { x -> target + y * COLS + x } }
        if (region.all { occ[it] == null }) return canonical(items + (key to target), size) + overflow
        // occupied: before the first item (in list order) of the target area
        val hit = region.mapNotNull { occ[it] }.toSet()
        val idx = keys.indexOfFirst { it in hit }.coerceAtLeast(0)
        val out = keys.toMutableList()
        out.add(idx, key)
        val gap = (idx + 1 until out.size).firstOrNull { isGap(out[it]) }
        if (gap != null) out.removeAt(gap)
        return out
    }

    /** Pushes what doesn't fit anymore onto the following pages (like iOS when inserting); placeholders at the end drop out. */
    fun flow(pages: List<List<String>>, size: (String) -> Pair<Int, Int>): List<List<String>> {
        val p = pages.map { trim(it).toMutableList() }.toMutableList()
        var i = 0
        while (i < p.size) {
            while (p[i].size > 1 && !fits(p[i], size)) {
                val overflow = p[i].removeAt(p[i].lastIndex)
                if (isGap(overflow)) continue
                if (i + 1 >= p.size) p += mutableListOf<String>()
                p[i + 1].add(0, overflow)
            }
            p[i] = trim(p[i]).toMutableList()
            i++
        }
        return p
    }
}
