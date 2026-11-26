package io.github.janhurn9400.liquidy.data

import android.content.Context
import androidx.compose.runtime.mutableStateListOf

/**
 * Control Center module layout (edit mode via "+"): an ordered list of controls with their size in grid cells.
 * Like the original, the modules flow into the 4-column grid in list order, each at the first free spot it fits.
 * Built-in ids are fixed names ("connectivity", "torch", …); Android Quick Settings tiles are "tile:<component>".
 */
class CcLayoutStore(context: Context) {
    data class Item(val id: String, val w: Int, val h: Int)

    private val prefs = context.applicationContext.getSharedPreferences("cc_layout", Context.MODE_PRIVATE)
    val items = mutableStateListOf<Item>().apply { addAll(load() ?: DEFAULT) }

    private fun load(): List<Item>? = prefs.getString(KEY, null)?.let { s ->
        s.split(',').mapNotNull { e -> e.split('|').takeIf { it.size == 3 }?.let { Item(it[0], it[1].toIntOrNull() ?: 1, it[2].toIntOrNull() ?: 1) } }
    }

    private fun save() { prefs.edit().putString(KEY, items.joinToString(",") { "${it.id}|${it.w}|${it.h}" }).apply() }

    fun remove(id: String) { items.removeAll { it.id == id }; save() }

    /** Adds [id] at the end (what doesn't fit flows onto the next page, like the original). */
    fun add(id: String) {
        if (items.any { it.id == id }) return
        val s = sizesFor(id).first()
        items.add(Item(id, s.first, s.second)); save()
    }

    /** Resizes [id]; the others reflow, onto further pages if needed. */
    fun resize(id: String, w: Int, h: Int) {
        val i = items.indexOfFirst { it.id == id }
        if (i < 0 || (items[i].w == w && items[i].h == h)) return
        items[i] = items[i].copy(w = w, h = h); save()
    }

    companion object {
        private const val KEY = "items"
        const val COLS = 4

        val DEFAULT = listOf(
            Item("connectivity", 2, 2), Item("media", 2, 2), Item("rotation", 1, 1), Item("silent", 1, 1),
            Item("volume", 1, 2), Item("brightness", 1, 2), Item("focus", 2, 1),
            Item("torch", 1, 1), Item("mirror", 1, 1), Item("darkmode", 1, 1), Item("camera", 1, 1),
            Item("saver", 1, 1), Item("timer", 1, 1), Item("calculator", 1, 1), Item("alarm", 1, 1),
        )

        /** Built-in controls that can be added from the gallery (in gallery order). */
        val BUILT_IN = listOf("connectivity", "media", "volume", "brightness", "focus", "rotation", "silent", "torch",
            "mirror", "darkmode", "camera", "saver", "timer", "calculator", "alarm")

        /** Sizes a control can take (w × h cells); the first is its size when added. */
        fun sizesFor(id: String): List<Pair<Int, Int>> = when (id) {
            "connectivity", "media" -> listOf(2 to 2)
            "volume", "brightness" -> listOf(1 to 2)
            "focus" -> listOf(2 to 1, 1 to 1, 1 to 2, 2 to 2)
            else -> listOf(1 to 1, 2 to 1, 1 to 2, 2 to 2) // round, wide, tall, large [MEASURED] user's iPhone recording
        }

        /** Rows the packed layout needs. */
        fun rowsUsed(items: List<Item>, rowsPerPage: Int): Int {
            val p = pack(items, rowsPerPage)
            return items.maxOfOrNull { (p[it.id]?.second ?: 0) + it.h } ?: 0
        }

        /** Pages the layout fills. */
        fun pages(items: List<Item>, rowsPerPage: Int): Int = maxOf(1, (rowsUsed(items, rowsPerPage) + rowsPerPage - 1) / rowsPerPage)

        /**
         * Grid position (col, row) of every item: list order, first free spot that fits (row-major). Rows run on across
         * pages of [rowsPerPage]; an item never straddles a page boundary.
         */
        fun pack(items: List<Item>, rowsPerPage: Int): Map<String, Pair<Int, Int>> {
            val taken = HashSet<Long>()
            fun key(c: Int, r: Int) = r.toLong() * 64 + c
            val out = LinkedHashMap<String, Pair<Int, Int>>()
            for (it in items) {
                val w = it.w.coerceIn(1, COLS)
                var r = 0
                search@ while (r < 200) {
                    if (r % rowsPerPage + it.h > rowsPerPage) { r++; continue } // would cross into the next page
                    for (c in 0..COLS - w) {
                        val fits = (0 until w).all { dx -> (0 until it.h).all { dy -> key(c + dx, r + dy) !in taken } }
                        if (fits) {
                            for (dx in 0 until w) for (dy in 0 until it.h) taken.add(key(c + dx, r + dy))
                            out[it.id] = c to r
                            break@search
                        }
                    }
                    r++
                }
            }
            return out
        }
    }
}
