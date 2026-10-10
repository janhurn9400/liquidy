package io.github.janhurn9400.liquidy.ui.home

import io.github.janhurn9400.liquidy.design.DesignTokens

/**
 * Raster-Platzierung wie iOS: Items der Reihe nach in die erste freie Stelle (zeilenweise), in die sie passen.
 * Icons belegen 1 Zelle, Widgets 2×2 / 4×2 / 4×4 und rasten wie bei iOS nur in Spalte 0 oder 2 ein.
 * Zelle = Zeile · Spalten + Spalte.
 */
object GridPlacement {
    private const val COLS = DesignTokens.GRID_COLUMNS
    private const val ROWS = DesignTokens.GRID_ROWS

    /** Zelle je Key (Reihenfolge wie [keys]); -1 = passt nicht mehr auf die Seite. */
    fun place(keys: List<String>, size: (String) -> Pair<Int, Int>): IntArray {
        val used = Array(ROWS) { BooleanArray(COLS) }
        return IntArray(keys.size) { i ->
            val (c, r) = size(keys[i])
            var found = -1
            loop@ for (row in 0..ROWS - r) for (col in 0..COLS - c) {
                if (c > 1 && col % 2 != 0) continue
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

    /** Schiebt, was nicht mehr passt, auf die Folgeseiten (wie iOS beim Einfügen). */
    fun flow(pages: List<List<String>>, size: (String) -> Pair<Int, Int>): List<List<String>> {
        val p = pages.map { it.toMutableList() }.toMutableList()
        var i = 0
        while (i < p.size) {
            while (p[i].size > 1 && !fits(p[i], size)) {
                val overflow = p[i].removeAt(p[i].lastIndex)
                if (i + 1 >= p.size) p += mutableListOf<String>()
                p[i + 1].add(0, overflow)
            }
            i++
        }
        return p
    }
}
