package io.github.janhurn9400.liquidy.glass

import android.content.Context
import android.graphics.Point
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.view.Display
import android.view.WindowManager
import kotlin.math.ceil
import kotlin.math.sqrt

/**
 * Welchen Ausschnitt eines statischen Wallpapers zeigt Android auf dem aktuellen Display?
 *
 * Nachbau der AOSP-Logik (Android 15+, „multi-crop“), damit unser selbst gezeichneter Hintergrund und Androids
 * Wallpaper hinter der Wisch-nach-Hause-Geste pixelgleich sind — auf jedem Gerät, statt mit gemessenen Konstanten:
 *  1. WallpaperCropper.getDefaultCrops/getCrop (services/core/.../wallpaper/WallpaperCropper.java): Zuschnitt je
 *     Bildschirm-Ausrichtung aus den möglichen Displaygrößen (WallpaperDisplayHelper: getPossibleMaximumWindowMetrics,
 *     Foldable-Paare aufgeklappt/zugeklappt). setBitmap(…, sichtbarer Bereich) = ORIENTATION_UNKNOWN → die Datei wird auf
 *     die Vereinigung aller Zuschnitte gekürzt; gezeigt wird getCrop(Display, gekürzte Größe, keine Vorgaben).
 *  2. WallpaperController.updateWallpaperOffset: breiterer Zuschnitt = Parallax-Rand, Lage nach Wallpaper-Offset
 *     (wir setzen 0,5); dazu Zoom zoomOutToScale(0) = config_wallpaperMaxScale (Standard 1,10), mittig.
 * [GEPRÜFT] Emulator Pixel Fold: Duo-Display (2854 × 2007) Versatz Homescreen ↔ Geste ≤ 1 px, Z-Fold-Größe (2160 × 1856) 0 px,
 * Außendisplay (1398 × 2034, mit Parallax-Rand → kein Zoom) ±2 px.
 * Displaygrößen: versteckte WindowManager-Methode, sonst DisplayManager (alle Displays inkl. ausgeschaltetem), sonst gemerkt.
 * Hersteller mit eigener Wallpaper-Logik können abweichen.
 */
object WallpaperCropModel {
    private const val L = 1; private const val SL = 2; private const val SP = 3; private const val P = 0; private const val U = -1
    private const val ADD = 1; private const val REMOVE = 2; private const val BALANCE = 3
    private const val MAX_PARALLAX = 1f

    /** Gezeigter Bereich (in Pixeln des gesetzten Bilds der Größe [bmpW]×[bmpH]) oder null, wenn Android nichts zuschneidet. */
    fun shownRegion(context: Context, screenW: Int, screenH: Int, bmpW: Int, bmpH: Int): RectF? {
        if (Build.VERSION.SDK_INT < 35 || screenW <= 0 || screenH <= 0 || bmpW <= 0 || bmpH <= 0) return null
        val helper = helper(context) ?: return null
        val rtl = context.resources.configuration.layoutDirection == android.view.View.LAYOUT_DIRECTION_RTL
        val full = Point(bmpW, bmpH)
        // 1. gespeicherter Zuschnitt (Vereinigung der Standard-Zuschnitte) und Zuschnitt fürs aktuelle Display
        val hint = total(helper.sizes.map { (_, ds) -> getCrop(helper, ds, full, emptyMap(), rtl) })
        val cropped = Point(hint.width(), hint.height())
        val screen = Point(screenW, screenH)
        val crop = getCrop(helper, screen, cropped, emptyMap(), rtl)
        // 2. Parallax-Rand: sichtbarer Teil nach Offset 0,5 (mittig); dann Zoom um die Mitte
        val sr = screenW.toFloat() / screenH
        val fr = crop.width().toFloat() / crop.height()
        val vis = if (fr >= sr) {
            val w = crop.height() * sr; val l = crop.left + (crop.width() - w) * 0.5f
            RectF(l, crop.top.toFloat(), l + w, crop.bottom.toFloat())
        } else {
            val h = crop.width() / sr; val t = crop.top + (crop.height() - h) * 0.5f
            RectF(crop.left.toFloat(), t, crop.right.toFloat(), t + h)
        }
        val z = zoomFor(context, helper, Point(screenW, screenH))
        val cx = vis.centerX(); val cy = vis.centerY(); val hw = vis.width() / 2 / z; val hh = vis.height() / 2 / z
        val r = RectF(cx - hw + hint.left, cy - hh + hint.top, cx + hw + hint.left, cy + hh + hint.top)
        // nichts zu tun, wenn Android das Bild 1:1 zeigt
        if (kotlin.math.abs(r.left) < 0.5f && kotlin.math.abs(r.top) < 0.5f && kotlin.math.abs(r.right - bmpW) < 0.5f && kotlin.math.abs(r.bottom - bmpH) < 0.5f) return null
        return r
    }

    /** Bereich relativ zur von Android gespeicherten (gekürzten) Datei — für WallpaperManager.getDrawable(). */
    fun shownRegionInCroppedFile(context: Context, screenW: Int, screenH: Int, fileW: Int, fileH: Int): RectF? {
        if (Build.VERSION.SDK_INT < 35) return null
        val helper = helper(context) ?: return null
        val rtl = context.resources.configuration.layoutDirection == android.view.View.LAYOUT_DIRECTION_RTL
        // Die Datei IST schon der gekürzte Bereich → nur noch Zuschnitt fürs Display + Parallax + Zoom
        val crop = getCrop(helper, Point(screenW, screenH), Point(fileW, fileH), emptyMap(), rtl)
        val sr = screenW.toFloat() / screenH
        val fr = crop.width().toFloat() / crop.height()
        val vis = if (fr >= sr) {
            val w = crop.height() * sr; val l = crop.left + (crop.width() - w) * 0.5f
            RectF(l, crop.top.toFloat(), l + w, crop.bottom.toFloat())
        } else {
            val h = crop.width() / sr; val t = crop.top + (crop.height() - h) * 0.5f
            RectF(crop.left.toFloat(), t, crop.right.toFloat(), t + h)
        }
        val z = zoomFor(context, helper, Point(screenW, screenH))
        val cx = vis.centerX(); val cy = vis.centerY(); val hw = vis.width() / 2 / z; val hh = vis.height() / 2 / z
        return RectF(cx - hw, cy - hh, cx + hw, cy + hh)
    }

    /**
     * Zoom, den Android auf den sichtbaren Ausschnitt legt: aufgeklappt bzw. auf normalen Displays config_wallpaperMaxScale,
     * auf dem zugeklappten Display eines Foldables keiner — dort rendert ImageWallpaper breiter als das Display (1831 statt
     * 1398 px, Parallax-Rand) und zeigt ungezoomt die Mitte [GEMESSEN Emulator Pixel Fold 10-14: zugeklappt ohne Zoom ±2 px,
     * aufgeklappt mit Zoom 0,4 Grauwerte Abweichung].
     */
    private fun zoomFor(context: Context, h: Helper, screen: Point): Float =
        if (h.isFoldedDisplay(screen)) 1f else maxScale(context)

    /** Für die Diagnose (Launcher-Einstellungen › Erweitert): was das Modell für dieses Display annimmt. */
    fun describe(context: Context, screenW: Int, screenH: Int): String {
        val h = helper(context) ?: return "crop model: no display metrics"
        val screen = Point(screenW, screenH)
        val folded = h.isFoldedDisplay(screen)
        val displays = metrics(context).joinToString(", ") { "${it.w}x${it.h}@${it.density}" }
        return "displays: $displays\nfoldable pair: ${h.foldedArea > 0} · this display folded: $folded\n" +
            "maxScale (config): ${maxScale(context)} · zoom used: ${zoomFor(context, h, screen)}"
    }

    private fun maxScale(context: Context): Float {
        val id = android.content.res.Resources.getSystem().getIdentifier("config_wallpaperMaxScale", "dimen", "android")
        return if (id != 0) runCatching { android.content.res.Resources.getSystem().getFloat(id) }.getOrDefault(1.1f) else 1.1f
    }

    // ── WallpaperDisplayHelper ────────────────────────────────────────────────
    /** [foldedArea] = Pixelfläche des zugeklappten Displays eines Foldables (0 = kein Foldable-Paar bekannt). */
    private class Helper(val sizes: Map<Int, Point>, val pairs: List<Pair<Int, Int>>, val foldedArea: Long = 0) {
        fun unfolded(o: Int) = pairs.firstOrNull { it.first == o }?.second ?: U
        /** [screen] ist das (kleinere) zugeklappte Display eines Foldables — nach Fläche, nicht Ausrichtung (die Paare gibt es
         *  auch gedreht, dann sähe das große Display im Querformat wie „zugeklappt“ aus). */
        fun isFoldedDisplay(screen: Point) = foldedArea > 0 && screen.x.toLong() * screen.y <= foldedArea
        fun folded(o: Int) = pairs.firstOrNull { it.second == o }?.first ?: U
    }

    /** Displaygröße (px) + Dichte, wie WallpaperDisplayHelper sie sieht. */
    private data class Metric(val w: Int, val h: Int, val density: Float)

    /**
     * Mögliche Displaygrößen: über die (versteckte) WindowManager-Methode, die auch das System nutzt; ist sie gesperrt,
     * die vom Launcher selbst gesehenen Größen (aufgeklappt + zugeklappt, [rememberDisplay]).
     */
    private fun metrics(context: Context): List<Metric> {
        val wm = context.getSystemService(WindowManager::class.java)
        val viaSystem = runCatching {
            @Suppress("UNCHECKED_CAST")
            val set = WindowManager::class.java.getMethod("getPossibleMaximumWindowMetrics", Int::class.javaPrimitiveType)
                .invoke(wm, Display.DEFAULT_DISPLAY) as Set<android.view.WindowMetrics>
            set.map { Metric(it.bounds.width(), it.bounds.height(), it.density) }
        }.getOrNull()
        if (!viaSystem.isNullOrEmpty()) return viaSystem
        // Alle Displays inkl. ausgeschaltetem (Foldable: das gerade zugeklappte) — öffentliche Methode, Kategorie-Name aus AOSP
        val viaDisplays = runCatching {
            val dm = context.getSystemService(android.hardware.display.DisplayManager::class.java)
            dm.getDisplays("android.hardware.display.category.ALL_INCLUDING_DISABLED")
                .filter { (it.flags and Display.FLAG_PRESENTATION) == 0 && (it.flags and Display.FLAG_PRIVATE) == 0 }
                .map { d -> val m = android.util.DisplayMetrics().also { d.getRealMetrics(it) }; val mode = d.mode
                    Metric(mode.physicalWidth, mode.physicalHeight, m.density) }
                .distinctBy { "${maxOf(it.w, it.h)}x${minOf(it.w, it.h)}" }
        }.onFailure { android.util.Log.i("WallpaperCrop", "getDisplays: $it") }.getOrNull()
        if (viaDisplays != null && viaDisplays.size >= 2) return viaDisplays
        return learned(context)
    }

    private fun learned(context: Context): List<Metric> =
        context.getSharedPreferences("displays", Context.MODE_PRIVATE).getString("seen", null)?.split(';')
            ?.mapNotNull { e -> e.split(',').takeIf { it.size == 3 }?.let { runCatching { Metric(it[0].toInt(), it[1].toInt(), it[2].toFloat()) }.getOrNull() } }
            ?: emptyList()

    /** Vom Launcher bei jeder Fenstergröße gemeldet: merkt sich bis zu zwei verschiedene Displays (Foldable: innen/außen). */
    fun rememberDisplay(context: Context) {
        val wm = context.getSystemService(WindowManager::class.java) ?: return
        val m = wm.maximumWindowMetrics
        val cur = Metric(m.bounds.width(), m.bounds.height(), context.resources.displayMetrics.density)
        fun key(x: Metric) = "${maxOf(x.w, x.h)}x${minOf(x.w, x.h)}"
        val list = (listOf(cur) + learned(context).filter { key(it) != key(cur) }).take(2)
        context.getSharedPreferences("displays", Context.MODE_PRIVATE).edit()
            .putString("seen", list.joinToString(";") { "${it.w},${it.h},${it.density}" }).apply()
    }

    private fun helper(context: Context): Helper? {
        val metrics = metrics(context)
        if (metrics.isEmpty()) return null
        val foldable = run {
            val id = android.content.res.Resources.getSystem().getIdentifier("config_foldedDeviceStates", "array", "android")
            id != 0 && runCatching { android.content.res.Resources.getSystem().getIntArray(id).isNotEmpty() }.getOrDefault(false)
        }
        val sizes = HashMap<Int, Point>(); val pairs = ArrayList<Pair<Int, Int>>()
        val populate = foldable && metrics.size == 2
        var surface = 0f; var first = -1
        for (m in metrics) {
            val ds = Point(m.w, m.h)
            for (pt in listOf(ds, Point(ds.y, ds.x))) {
                val o = orientation(pt); val d = sizes[o]
                if (d == null || d.x.toLong() * d.y < pt.x.toLong() * pt.y) sizes[o] = pt
            }
            if (populate) {
                val o = orientation(ds)
                val ns = ds.x * ds.y / (m.density * m.density)
                if (surface <= 0f) { surface = ns; first = o } else {
                    val pair = if (ns > surface) first to o else o to first
                    pairs += pair; pairs += rotated(pair.first) to rotated(pair.second)
                }
            }
        }
        val folded = if (populate) metrics.minOf { it.w.toLong() * it.h } else 0L
        return Helper(sizes, pairs, folded)
    }

    // ── WallpaperCropper ──────────────────────────────────────────────────────
    private fun orientation(p: Point): Int {
        val r = p.x.toFloat() / p.y
        return if (r >= 4 / 3f) L else if (r > 1f) SL else if (r > 3 / 4f) SP else P
    }
    private fun rotated(o: Int) = when (o) { P -> L; L -> P; SP -> SL; SL -> SP; else -> U }

    private fun getCrop(h: Helper, ds: Point, bmp: Point, sug: Map<Int, Rect>, rtl: Boolean): Rect {
        val o = orientation(ds)
        if (sug.isEmpty()) {
            val c = Rect(0, 0, bmp.x, bmp.y)
            val u = h.unfolded(o)
            if (u != U) return getCrop(h, ds, bmp, mapOf(u to c), rtl)
            // (Tablet im Hochformat: Sonderfall in AOSP; hier wie Querformat-Zuschnitt ohne Rand behandelt)
            return adjusted(c, bmp, ds, true, rtl, ADD)
        }
        sug[o]?.let { return adjusted(it, bmp, ds, true, rtl, ADD) }
        val ro = rotated(o)
        sug[ro]?.let { return adjusted(noParallax(it, h.sizes[ro], bmp, rtl), bmp, ds, false, rtl, BALANCE) }
        val u = h.unfolded(o)
        sug[u]?.let { s ->
            val a = noParallax(s, h.sizes[u], bmp, rtl)
            var res = adjusted(a, bmp, ds, false, rtl, REMOVE)
            if (res.width() < a.width()) {
                res = Rect(res); if (rtl) res.left = minOf(res.left, a.left) else res.right = maxOf(res.right, a.right)
                res = adjusted(res, bmp, ds, true, rtl, ADD)
            }
            return res
        }
        val f = h.folded(o)
        sug[f]?.let { return adjusted(noParallax(it, h.sizes[f], bmp, rtl), bmp, ds, false, rtl, ADD) }
        return getCrop(h, ds, bmp, emptyMap(), rtl)
    }

    private fun noParallax(crop: Rect, ds: Point?, bmp: Point, rtl: Boolean): Rect {
        if (ds == null) return crop
        val a = Rect(adjusted(crop, bmp, ds, true, rtl, ADD))
        val sr = ds.x.toFloat() / ds.y
        val remove = (a.width() - a.height() * sr + 0.5f).toInt()
        if (rtl) a.left += remove else a.right -= remove
        return a
    }

    private fun adjusted(crop: Rect, bmp: Point, screen: Point, parallax: Boolean, rtl: Boolean, mode: Int): Rect {
        val a = Rect(crop)
        val cr = crop.width().toFloat() / crop.height(); val sr = screen.x.toFloat() / screen.y
        if (cr == sr) return crop
        if (cr > sr) {
            if (!parallax) {
                val nl = bmp.y - crop.bottom; val rc = Rect(nl, crop.left, nl + crop.height(), crop.left + crop.width())
                val rb = Point(bmp.y, bmp.x); val r = adjusted(rc, rb, Point(screen.y, screen.x), false, rtl, mode)
                val rl = r.top; val rt = rb.x - r.right
                return Rect(rl, rt, rl + r.height(), rt + r.width())
            }
            val extra = cr / sr - 1f
            if (extra > MAX_PARALLAX) {
                val remove = ceil((extra - MAX_PARALLAX) * sr * crop.height()).toInt()
                if (rtl) a.left += remove else a.right -= remove
            }
        } else {
            val add = when (mode) {
                REMOVE -> 0
                ADD -> (crop.height() * sr - crop.width()).toInt()
                else -> (-crop.width() + sqrt(crop.width().toDouble() * crop.height() * sr)).toInt()
            }
            val avail = bmp.x - crop.width()
            if (avail >= add) {
                var al = add / 2; var ar = add / 2 + add % 2
                if (crop.left < al) { ar += al - crop.left; al = crop.left }
                else if (bmp.x - crop.right < ar) { al += ar - (bmp.x - crop.right); ar = bmp.x - crop.right }
                a.left -= al; a.right += ar
            } else { a.left = 0; a.right = bmp.x }
            val hr = (crop.height() - a.width() / sr).toInt()
            a.top += hr / 2 + hr % 2; a.bottom -= hr / 2
        }
        return a
    }

    private fun total(crops: List<Rect>) = Rect(crops.minOf { it.left }, crops.minOf { it.top }, crops.maxOf { it.right }, crops.maxOf { it.bottom })
}
