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

    /** Adds [id] at the end; false when it would not fit on the page (more pages: not built yet). */
    fun add(id: String, rows: Int): Boolean {
        if (items.any { it.id == id }) return true
        val s = sizesFor(id).first()
        val next = items + Item(id, s.first, s.second)
        if (rowsUsed(next) > rows) return false
        items.add(next.last()); save()
        return true
    }

    /** Resizes [id] unless the new size would push modules off the page. */
    fun resize(id: String, w: Int, h: Int, rows: Int) {
        val i = items.indexOfFirst { it.id == id }
        if (i < 0 || (items[i].w == w && items[i].h == h)) return
        val next = items.toMutableList().also { it[i] = it[i].copy(w = w, h = h) }
        if (rowsUsed(next) > rows) return
        items[i] = next[i]; save()
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
            "focus" -> listOf(2 to 1, 1 to 1, 2 to 2)
            else -> listOf(1 to 1, 2 to 1, 2 to 2)
        }

        /** Rows the packed layout needs. */
        fun rowsUsed(items: List<Item>): Int {
            val p = pack(items)
            return items.maxOfOrNull { (p[it.id]?.second ?: 0) + it.h } ?: 0
        }

        /** Grid position (col, row) of every item: list order, first free spot that fits (row-major). */
        fun pack(items: List<Item>): Map<String, Pair<Int, Int>> {
            val taken = HashSet<Long>()
            fun key(c: Int, r: Int) = r.toLong() * 64 + c
            val out = LinkedHashMap<String, Pair<Int, Int>>()
            for (it in items) {
                val w = it.w.coerceIn(1, COLS)
                var r = 0
                search@ while (r < 200) {
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
