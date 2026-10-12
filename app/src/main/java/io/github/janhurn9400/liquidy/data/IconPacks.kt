package io.github.janhurn9400.liquidy.data

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

/**
 * Icon-Pakete im verbreiteten ADW/Nova-Format (praktisch alle Pakete aus dem Play Store oder als APK):
 * Das Paket meldet sich über eine der [ACTIONS] und liefert `appfilter.xml` (res/xml oder assets) mit
 * `<item component="ComponentInfo{paket/klasse}" drawable="name"/>`. Für Apps ohne eigenes Symbol gibt es optional
 * `<iconback>`, `<iconmask>`, `<iconupon>` und `<scale>` — dann wird das Original-Icon so eingepasst (wie Nova).
 * `<calendar component=… prefix="cal_"/>`: Kalender-Icon mit Tageszahl (prefix + 1…31).
 */
object IconPacks {
    data class Pack(val packageName: String, val label: String)

    /** Gewähltes Paket (null = System-Icons). Compose-State → Einstellungen zeichnen neu. */
    var selected by mutableStateOf<String?>(null)
        private set

    /** Von [AppRepository] gesetzt: Icons neu laden, wenn das Paket wechselt. */
    var onChanged: (() -> Unit)? = null

    private val loaded = java.util.concurrent.ConcurrentHashMap<String, Loaded>()

    private class Loaded(
        val pkg: String,
        val res: Resources,
        val items: Map<String, String>,       // "paket/klasse" → Drawable-Name
        val byClass: Map<String, String>,     // voll qualifizierte Klasse → Drawable-Name (Ausweich-Treffer)
        val calendars: Map<String, String>,   // "paket/klasse" → Präfix
        val backs: List<String>, val mask: String?, val upon: String?, val scale: Float,
    )

    fun load(context: Context) {
        selected = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("pack", null)
            ?.takeIf { installed(context, it) }
    }

    fun select(context: Context, pkg: String?) {
        if (pkg == selected) return
        selected = pkg
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("pack", pkg).apply()
        onChanged?.invoke()
    }

    /** Installierte Icon-Pakete, alphabetisch. */
    fun available(context: Context): List<Pack> {
        val pm = context.packageManager
        return ACTIONS.flatMap { a -> runCatching { pm.queryIntentActivities(Intent(a), PackageManager.GET_META_DATA) }.getOrDefault(emptyList()) }
            .map { it.activityInfo.packageName }.distinct()
            .filter { it != context.packageName }
            .map { Pack(it, runCatching { pm.getApplicationLabel(pm.getApplicationInfo(it, 0)).toString() }.getOrDefault(it)) }
            .sortedBy { it.label.lowercase() }
    }

    private fun installed(context: Context, pkg: String) = runCatching { context.packageManager.getApplicationInfo(pkg, 0) }.isSuccess

    /**
     * Symbol aus dem gewählten Paket für [component], sonst null. [fallback] = Original-Icon: wird bei Paketen mit
     * iconback/iconmask in deren Rahmen eingepasst (sonst bleibt es unverändert → null).
     */
    fun icon(context: Context, component: ComponentName, fallback: Drawable?, sizePx: Int): Drawable? =
        selected?.let { iconFrom(context, it, component, fallback, sizePx) }

    /** Wie [icon], aber aus einem bestimmten Paket (Vorschau in den Einstellungen). */
    fun iconFrom(context: Context, pkg: String, component: ComponentName, fallback: Drawable?, sizePx: Int): Drawable? {
        val l = loaded[pkg] ?: parse(context, pkg)?.also { loaded[pkg] = it } ?: return null
        val key = "${component.packageName}/${component.className}"
        l.calendars[key]?.let { prefix -> drawable(l, prefix + java.time.LocalDate.now().dayOfMonth)?.let { return it } }
        val name = l.items[key]
            ?: l.items["${component.packageName}/${component.shortClassName}"] // manche Pakete schreiben die Klasse relativ („.Main“)
            // gleiche Activity unter anderem Paketnamen (z. B. com.google.android.documentsui statt com.android.documentsui)
            ?: l.byClass[component.className]
        name?.let { n -> drawable(l, n)?.let { return it } }
        if (fallback == null || (l.backs.isEmpty() && l.mask == null && l.upon == null)) return null
        return composeUnthemed(context, l, fallback, component, sizePx)
    }

    @SuppressLint("DiscouragedApi") // Icon-Pakete sind nur über Namen erreichbar
    private fun drawable(l: Loaded, name: String): Drawable? {
        for (type in arrayOf("drawable", "mipmap")) {
            val id = l.res.getIdentifier(name, type, l.pkg)
            if (id != 0) return runCatching { l.res.getDrawable(id, null) }.getOrNull()
        }
        return null
    }

    /** Fremdes Icon in Paket-Optik: Hintergrund (je App fest gewählt), Icon skaliert + maskiert, Überlagerung. */
    private fun composeUnthemed(context: Context, l: Loaded, icon: Drawable, component: ComponentName, sizePx: Int): Drawable {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val back = l.backs.takeIf { it.isNotEmpty() }?.let { drawable(l, it[Math.floorMod(component.hashCode(), it.size)]) }
        back?.let { it.setBounds(0, 0, sizePx, sizePx); it.draw(c) }
        val inner = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val ic = Canvas(inner)
        val s = (sizePx * l.scale).toInt(); val o = (sizePx - s) / 2
        icon.setBounds(o, o, o + s, o + s); icon.draw(ic)
        l.mask?.let { drawable(l, it) }?.let { m ->
            val mb = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            m.setBounds(0, 0, sizePx, sizePx); m.draw(Canvas(mb))
            ic.drawBitmap(mb, 0f, 0f, Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT) })
        }
        c.drawBitmap(inner, 0f, 0f, null)
        l.upon?.let { drawable(l, it) }?.let { it.setBounds(0, 0, sizePx, sizePx); it.draw(c) }
        return BitmapDrawable(context.resources, bmp)
    }

    @SuppressLint("DiscouragedApi")
    private fun parse(context: Context, pkg: String): Loaded? = runCatching {
        val res = context.packageManager.getResourcesForApplication(pkg)
        val xmlId = res.getIdentifier("appfilter", "xml", pkg)
        val parser: XmlPullParser = if (xmlId != 0) res.getXml(xmlId) else
            XmlPullParserFactory.newInstance().newPullParser().apply { setInput(res.assets.open("appfilter.xml"), "UTF-8") }
        val items = HashMap<String, String>(); val calendars = HashMap<String, String>()
        val backs = ArrayList<String>(); var mask: String? = null; var upon: String? = null; var scale = 1f
        fun comp(raw: String?): String? = raw?.substringAfter("ComponentInfo{", "")?.substringBefore("}")?.takeIf { it.contains('/') }
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            fun attr(n: String) = parser.getAttributeValue(null, n)
            when (parser.name) {
                "item" -> { val k = comp(attr("component")); val d = attr("drawable"); if (k != null && !d.isNullOrBlank()) items.putIfAbsent(k, d) }
                "calendar" -> { val k = comp(attr("component")); val p = attr("prefix"); if (k != null && p != null) calendars[k] = p }
                "iconback" -> for (i in 0 until parser.attributeCount) parser.getAttributeValue(i)?.let { backs += it }
                "iconmask" -> mask = attr("img1") ?: mask
                "iconupon" -> upon = attr("img1") ?: upon
                "scale" -> scale = attr("factor")?.toFloatOrNull() ?: scale
            }
        }
        val byClass = HashMap<String, String>()
        items.forEach { (k, d) -> val cls = k.substringAfter('/'); if (!cls.startsWith(".")) byClass.putIfAbsent(cls, d) }
        Loaded(pkg, res, items, byClass, calendars, backs, mask, upon, scale)
    }.getOrNull()

    private const val PREFS = "iconpack"
    private val ACTIONS = listOf(
        "org.adw.launcher.THEMES", "com.novalauncher.THEME", "com.gau.go.launcherex.theme",
        "com.teslacoilsw.launcher.THEME", "com.anddoes.launcher.THEME", "com.fede.launcher.THEME_ICONPACK",
        "ch.deletescape.lawnchair.ICONPACK", "app.lawnchair.icons.THEMED_ICON",
    )
}
