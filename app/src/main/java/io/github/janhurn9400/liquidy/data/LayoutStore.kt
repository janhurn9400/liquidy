package io.github.janhurn9400.liquidy.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persistent home layout. Pages and dock contain item IDs:
 *  - app key (see AppEntry.key) or
 *  - folder ID ("folder:…"), whose content is in [folders] (phase 3).
 * [hidden] = apps removed from the home screen (end up in the App Library, phase 4).
 */
data class SavedLayout(
    val pages: List<List<String>>,
    val dock: List<String>,
    val folders: Map<String, SavedFolder>,
    val hidden: Set<String>,
    val widgets: Map<String, WidgetSpec> = emptyMap(),
    /** Widgets of the Today view (order); null = never saved → default widgets. */
    val today: List<String>? = null,
    /** Pages hidden in the page overview: position in the overall order + content. */
    val hiddenPages: List<HiddenPage> = emptyList(),
)

/** Hidden home page (iOS "Edit Pages"): [pos] = slot in the overview (visible + hidden). */
data class HiddenPage(val pos: Int, val items: List<String>)

/**
 * Widget on a page: [type] = "ios:clock" / "ios:calendar" / "ios:battery" or "android" ([appWidgetId] + [provider]).
 * Size in grid cells like iOS: small 2×2, medium 4×2, large 4×4.
 */
data class WidgetSpec(
    val type: String, val cols: Int, val rows: Int, val appWidgetId: Int = -1, val provider: String? = null,
    /** Widget stack (type = "stack"): contained widgets, same size, from top to bottom. */
    val stack: List<WidgetSpec> = emptyList(),
    /** Stack options (iOS "Smart Rotate" / "Widget Suggestions"). */
    val smartRotate: Boolean = true,
    val suggestions: Boolean = true,
    /** Widget setting (e.g. weather: fixed location "lat;lon;name"); null = default. */
    val config: String? = null,
)

data class SavedFolder(val name: String, val apps: List<String>)

class LayoutStore(context: Context) {
    private val prefs = context.getSharedPreferences("layout", Context.MODE_PRIVATE)

    fun load(): SavedLayout? {
        val json = prefs.getString(KEY, null) ?: return null
        return runCatching {
            val o = JSONObject(json)
            SavedLayout(
                pages = o.getJSONArray("pages").let { arr -> List(arr.length()) { arr.getJSONArray(it).toList() } },
                dock = o.getJSONArray("dock").toList(),
                folders = o.optJSONObject("folders")?.let { f ->
                    f.keys().asSequence().associateWith { id ->
                        val fo = f.getJSONObject(id)
                        SavedFolder(fo.getString("name"), fo.getJSONArray("apps").toList())
                    }
                } ?: emptyMap(),
                hidden = o.optJSONArray("hidden")?.toList()?.toSet() ?: emptySet(),
                widgets = o.optJSONObject("widgets")?.let { w ->
                    w.keys().asSequence().associateWith { id ->
                        specFrom(w.getJSONObject(id))
                    }
                } ?: emptyMap(),
                today = o.optJSONArray("today")?.toList(),
                hiddenPages = o.optJSONArray("hiddenPages")?.let { arr ->
                    List(arr.length()) { i -> arr.getJSONObject(i).let { h -> HiddenPage(h.getInt("pos"), h.getJSONArray("items").toList()) } }
                } ?: emptyList(),
            )
        }.getOrNull()
    }

    fun save(layout: SavedLayout) {
        val o = JSONObject()
        o.put("pages", JSONArray(layout.pages.map { JSONArray(it) }))
        o.put("dock", JSONArray(layout.dock))
        layout.today?.let { o.put("today", JSONArray(it)) }
        o.put("widgets", JSONObject().apply {
            layout.widgets.forEach { (id, w) ->
                put(id, specJson(w))
            }
        })
        o.put("folders", JSONObject().apply {
            layout.folders.forEach { (id, f) ->
                put(id, JSONObject().put("name", f.name).put("apps", JSONArray(f.apps)))
            }
        })
        o.put("hidden", JSONArray(layout.hidden.toList()))
        o.put("hiddenPages", JSONArray(layout.hiddenPages.map { JSONObject().put("pos", it.pos).put("items", JSONArray(it.items)) }))
        prefs.edit().putString(KEY, o.toString()).apply()
    }

    fun clear() = prefs.edit().remove(KEY).apply()

    fun loadRecents(): List<String> = prefs.getString(RECENTS, null)?.let { runCatching { JSONArray(it).toList() }.getOrNull() } ?: emptyList()

    fun saveRecents(keys: List<String>) = prefs.edit().putString(RECENTS, JSONArray(keys).toString()).apply()

    private fun JSONArray.toList(): List<String> = List(length()) { getString(it) }

    private fun specFrom(wo: JSONObject): WidgetSpec = WidgetSpec(
        wo.getString("type"), wo.getInt("cols"),
        // old states could save tall-narrow (2×4) — doesn't exist on iOS → small
        wo.getInt("rows").let { r -> if (wo.getInt("cols") < 4 && r > 2) 2 else r }, wo.optInt("id", -1), wo.optString("provider").ifEmpty { null },
        stack = wo.optJSONArray("stack")?.let { a -> List(a.length()) { specFrom(a.getJSONObject(it)) } } ?: emptyList(),
        smartRotate = wo.optBoolean("rotate", true), suggestions = wo.optBoolean("suggest", true),
        config = wo.optString("cfg").ifEmpty { null },
    )

    private fun specJson(w: WidgetSpec): JSONObject = JSONObject().put("type", w.type).put("cols", w.cols).put("rows", w.rows)
        .put("id", w.appWidgetId).put("provider", w.provider ?: "").apply { if (w.config != null) put("cfg", w.config) }
        .apply { if (w.stack.isNotEmpty()) put("stack", JSONArray(w.stack.map(::specJson))).put("rotate", w.smartRotate).put("suggest", w.suggestions) }

    private companion object {
        const val KEY = "layout_v1"
        const val RECENTS = "recents_v1"
    }
}
