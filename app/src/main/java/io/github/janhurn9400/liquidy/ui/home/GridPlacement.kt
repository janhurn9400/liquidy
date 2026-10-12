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

    // ── Freie Platzierung (aktuelles iOS: Icons bleiben, wo man sie ablegt — auch mit Lücken) ─────────────────
    // Leere Zellen stehen als unsichtbare Platzhalter („gap:…“, 1×1) in der Seitenliste. Weil [place] der Reihe nach
    // die erste freie Stelle nimmt, legt eine zeilenweise sortierte Liste mit Platzhaltern jede Position genau fest.
    // Anzeige, Treffer und Animationen bekommen die Platzhalter nie zu sehen (HomeModel.display filtert sie).

    const val GAP_PREFIX = "gap:"
    fun isGap(key: String) = key.startsWith(GAP_PREFIX)
    fun newGap() = GAP_PREFIX + java.util.UUID.randomUUID().toString().take(12)
    /** Seite ohne echte Items (nur Platzhalter oder leer). */
    fun isBlank(keys: List<String>) = keys.all(::isGap)
    /** Platzhalter am Ende haben keine Wirkung → weg. */
    fun trim(keys: List<String>) = keys.dropLastWhile(::isGap)
    fun stripGaps(keys: List<String>) = keys.filterNot(::isGap)

    /** Zellen der echten Items (gleiche Reihenfolge wie [stripGaps]). */
    fun placeReal(keys: List<String>, size: (String) -> Pair<Int, Int>): IntArray {
        val all = place(keys, size)
        return keys.indices.filter { !isGap(keys[it]) }.map { all[it] }.toIntArray()
    }

    /** Liste aus festen Positionen (Key → Zelle oben links): zeilenweise, freie Zellen davor als Platzhalter. */
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
     * [key] an Zelle [cell] legen (Widgets rasten in Spalte 0/2 ein und bleiben im Raster):
     *  - Zielfläche frei → dort hin, alle anderen bleiben stehen,
     *  - belegt → vor das Item an dieser Stelle einfügen; die Folgenden rücken nur bis zur nächsten Lücke auf
     *    (die Lücke wird verbraucht), sonst wie bisher auf die nächste Seite (Aufrufer: [flow]).
     */
    fun placeAt(keys: List<String>, key: String, cell: Int, size: (String) -> Pair<Int, Int>): List<String> {
        val (c, r) = size(key)
        var col = (cell % COLS).coerceIn(0, COLS - 1)
        val row = (cell / COLS).coerceIn(0, ROWS - r)
        if (c > 1) col = (col / 2 * 2).coerceAtMost(COLS - c)
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
        // belegt: vor das (in Listenreihenfolge) erste Item der Zielfläche
        val hit = region.mapNotNull { occ[it] }.toSet()
        val idx = keys.indexOfFirst { it in hit }.coerceAtLeast(0)
        val out = keys.toMutableList()
        out.add(idx, key)
        val gap = (idx + 1 until out.size).firstOrNull { isGap(out[it]) }
        if (gap != null) out.removeAt(gap)
        return out
    }

    /** Schiebt, was nicht mehr passt, auf die Folgeseiten (wie iOS beim Einfügen); Platzhalter am Ende fallen weg. */
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
