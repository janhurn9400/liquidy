package io.github.janhurn9400.liquidy.data

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import java.io.File

/**
 * The launcher's font (Settings › Appearance › Font): Inter (default), a font installed on the device, or a font file
 * the user added. Every text in the app reads [family] (through `ui.Inter`), so a change applies everywhere at once.
 * Choice key: "inter" · "sys:<Family>" · "file:<name>".
 */
object FontChoice {
    /** One selectable font: [key] as stored, [name] as shown, [family] to render it (also used for its own preview). */
    data class Option(val key: String, val name: String, val family: FontFamily, val removable: Boolean = false)

    private const val PREFS = "font"
    private const val KEY = "choice"
    private const val INTER = "inter"
    /** Weights the UI uses (regular … bold). */
    private val WEIGHTS = listOf(400, 500, 600, 700)

    var key by mutableStateOf(INTER)
        private set
    var family: FontFamily by mutableStateOf(interFamily())
        private set

    @Volatile private var systemCache: List<Option>? = null

    fun load(context: Context) {
        val k = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, INTER) ?: INTER
        apply(context, k, save = false)
    }

    fun select(context: Context, option: Option) = apply(context, option.key, save = true)

    private fun apply(context: Context, k: String, save: Boolean) {
        // only scan the group the key belongs to (the device font list costs a little; not at every start)
        val pool = when {
            // at start only this one family (no full scan / Latin check of every device font on the main thread)
            k.startsWith("sys:") -> systemCache ?: loadSystem(only = k.removePrefix("sys:"))
            k.startsWith("file:") -> own(context)
            else -> emptyList()
        }
        val opt = pool.firstOrNull { it.key == k } ?: default()
        key = opt.key; family = opt.family
        if (save) context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, opt.key).apply()
    }

    fun default(): Option = Option(INTER, "Inter", interFamily())

    fun all(context: Context): List<Option> = listOf(default()) + system(context) + own(context)

    // ── fonts installed on the device ────────────────────────────────────────────────

    /**
     * Latin text families from Android's font list (the many script-only Noto fonts, emoji and the clock font are
     * left out), one entry per family; variable fonts keep their real weights through the wght axis.
     */
    fun system(context: Context): List<Option> = systemCache ?: loadSystem().also { systemCache = it }

    /** Display name for a stored key without loading anything (settings row). */
    fun nameOf(context: Context, k: String): String = when {
        k.startsWith("sys:") -> pretty(k.removePrefix("sys:"))
        k.startsWith("file:") -> k.removePrefix("file:").substringBeforeLast('.').replace('_', ' ').replace('-', ' ')
        else -> "Inter"
    }

    @OptIn(ExperimentalTextApi::class)
    private fun loadSystem(only: String? = null): List<Option> {
        val fonts = runCatching { android.graphics.fonts.SystemFonts.getAvailableFonts().toList() }.getOrDefault(emptyList())
        val byFamily = LinkedHashMap<String, MutableList<android.graphics.fonts.Font>>()
        for (f in fonts) {
            val file = f.file ?: continue
            if (f.ttcIndex != 0 || !(file.name.endsWith(".ttf") || file.name.endsWith(".otf"))) continue
            if (f.style.slant != android.graphics.fonts.FontStyle.FONT_SLANT_UPRIGHT) continue
            val fam = familyName(file.name) ?: continue
            if (only != null && fam != only) continue
            byFamily.getOrPut(fam) { mutableListOf() }.add(f)
        }
        return byFamily.mapNotNull { (fam, list) ->
            if (only == null && !latin(list.first().file!!)) return@mapNotNull null
            val faces = WEIGHTS.map { w ->
                val best = list.minBy { kotlin.math.abs(it.style.weight - w) }
                val variable = best.axes?.any { it.tag == "wght" } == true || list.count { it.file == best.file } > 1
                Font(best.file!!, FontWeight(w),
                    variationSettings = if (variable) FontVariation.Settings(FontVariation.weight(w)) else FontVariation.Settings())
            }
            Option("sys:$fam", pretty(fam), FontFamily(faces))
        }.sortedBy { it.name.lowercase() }
    }

    /** "SourceSansPro-Regular.ttf" → "SourceSansPro"; script-only / symbol families → null. */
    private fun familyName(file: String): String? {
        var base = file.substringBeforeLast('.').substringBefore('-')
        base = base.removeSuffix("Static").removeSuffix("UI")
        if (base.isEmpty()) return null
        val skip = listOf("NotoColorEmoji", "NotoSansSymbols", "AndroidClock", "NotoNaskh", "NotoSansCJK", "NotoSerifCJK")
        if (skip.any { base.startsWith(it) }) return null
        // Noto: only the plain Latin families
        if (base.startsWith("Noto") && base != "NotoSans" && base != "NotoSerif" && base != "NotoSansMono") return null
        return base
    }

    /** "SourceSansPro" → "Source Sans Pro". */
    private fun pretty(s: String) = s.replace(Regex("(?<=[a-z])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])"), " ")

    private fun latin(file: File): Boolean = runCatching {
        val tf = android.graphics.Typeface.createFromFile(file)
        val p = android.graphics.Paint().apply { typeface = tf }
        p.hasGlyph("A") && p.hasGlyph("g") && p.hasGlyph("5")
    }.getOrDefault(false)

    // ── fonts the user added ─────────────────────────────────────────────────────────

    private fun dir(context: Context) = File(context.filesDir, "fonts").apply { mkdirs() }

    @OptIn(ExperimentalTextApi::class)
    fun own(context: Context): List<Option> = (dir(context).listFiles()?.toList() ?: emptyList())
        .filter { it.extension.lowercase() in listOf("ttf", "otf") }
        .sortedBy { it.lastModified() }
        .map { f ->
            Option("file:${f.name}", f.nameWithoutExtension.replace('_', ' ').replace('-', ' '),
                FontFamily(WEIGHTS.map { w -> Font(f, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w))) }),
                removable = true)
        }

    /**
     * Copies a picked font file into the app and selects it. Returns false when it isn't a usable font (no Latin
     * glyphs, unreadable, or not a TrueType/OpenType file).
     */
    fun add(context: Context, uri: Uri): Boolean = runCatching {
        val cr = context.contentResolver
        val display = cr.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: "Font.ttf"
        val ext = display.substringAfterLast('.', "ttf").lowercase().takeIf { it == "ttf" || it == "otf" } ?: "ttf"
        val name = display.substringBeforeLast('.').replace(Regex("[^A-Za-z0-9 _-]"), "").trim().ifEmpty { "Font" }
        var target = File(dir(context), "$name.$ext")
        var n = 2
        while (target.exists()) target = File(dir(context), "$name $n.$ext").also { n++ }
        cr.openInputStream(uri)!!.use { input -> target.outputStream().use { input.copyTo(it) } }
        if (!latin(target)) { target.delete(); return false }
        apply(context, "file:${target.name}", save = true)
        true
    }.getOrDefault(false)

    /** Removes an added font; if it was in use, Inter comes back. */
    fun remove(context: Context, option: Option) {
        if (!option.removable) return
        File(dir(context), option.key.removePrefix("file:")).delete()
        if (key == option.key) apply(context, INTER, save = true)
    }

    /** Inter (SIL OFL, docs/Inter-OFL.txt): variable font, axis wght. */
    @OptIn(ExperimentalTextApi::class)
    private fun interFamily() = FontFamily(
        WEIGHTS.map { w ->
            Font(io.github.janhurn9400.liquidy.R.font.inter_variable, weight = FontWeight(w),
                variationSettings = FontVariation.Settings(FontVariation.weight(w)))
        }
    )
}
