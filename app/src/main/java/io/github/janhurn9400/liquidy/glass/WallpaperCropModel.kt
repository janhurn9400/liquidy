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
 * Which crop of a static wallpaper does Android show on the current display?
 *
 * Rebuild of the AOSP logic (Android 15+, "multi-crop"), so our self-drawn background and Android's
 * wallpaper behind the swipe-home gesture are pixel-identical — on every device, instead of with measured constants:
 *  1. WallpaperCropper.getDefaultCrops/getCrop (services/core/.../wallpaper/WallpaperCropper.java): crop per
 *     screen orientation from the possible display sizes (WallpaperDisplayHelper: getPossibleMaximumWindowMetrics,
 *     foldable pairs unfolded/folded). setBitmap(…, visible area) = ORIENTATION_UNKNOWN → the file is trimmed to
 *     the union of all crops; what's shown is getCrop(display, trimmed size, no hints).
 *  2. WallpaperController.updateWallpaperOffset: wider crop = parallax margin, position by wallpaper offset
 *     (we set 0.5); plus zoom zoomOutToScale(0) = config_wallpaperMaxScale (default 1.10), centred.
 * [CHECKED] emulator Pixel Fold: Duo display (2854 × 2007) offset home screen ↔ gesture ≤ 1 px, Z Fold size (2160 × 1856) 0 px,
 * outer display (1398 × 2034, with parallax margin → no zoom) ±2 px.
 * Display sizes: hidden WindowManager method, otherwise DisplayManager (all displays incl. the turned-off one), otherwise remembered.
 * Vendors with their own wallpaper logic may differ.
 */
object WallpaperCropModel {
    private const val L = 1; private const val SL = 2; private const val SP = 3; private const val P = 0; private const val U = -1
    private const val ADD = 1; private const val REMOVE = 2; private const val BALANCE = 3
    private const val MAX_PARALLAX = 1f

    /** Displayed area (in pixels of the set image of size [bmpW]×[bmpH]) or null if Android doesn't crop anything. */
    fun shownRegion(context: Context, screenW: Int, screenH: Int, bmpW: Int, bmpH: Int): RectF? {
        if (Build.VERSION.SDK_INT < 35 || screenW <= 0 || screenH <= 0 || bmpW <= 0 || bmpH <= 0) return null
        val helper = helper(context) ?: return null
        val rtl = context.resources.configuration.layoutDirection == android.view.View.LAYOUT_DIRECTION_RTL
        val full = Point(bmpW, bmpH)
        // 1. saved crop (union of the default crops) and crop for the current display
        val hint = total(helper.sizes.map { (_, ds) -> getCrop(helper, ds, full, emptyMap(), rtl) })
        val cropped = Point(hint.width(), hint.height())
        val screen = Point(screenW, screenH)
        val crop = getCrop(helper, screen, cropped, emptyMap(), rtl)
        // 2. parallax margin: visible part at offset 0.5 (centred); then zoom around the centre
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
        // nothing to do if Android shows the image 1:1
        if (kotlin.math.abs(r.left) < 0.5f && kotlin.math.abs(r.top) < 0.5f && kotlin.math.abs(r.right - bmpW) < 0.5f && kotlin.math.abs(r.bottom - bmpH) < 0.5f) return null
        return r
    }

    /** Area relative to the (trimmed) file saved by Android — for WallpaperManager.getDrawable(). */
    fun shownRegionInCroppedFile(context: Context, screenW: Int, screenH: Int, fileW: Int, fileH: Int): RectF? {
        if (Build.VERSION.SDK_INT < 35) return null
        val helper = helper(context) ?: return null
        val rtl = context.resources.configuration.layoutDirection == android.view.View.LAYOUT_DIRECTION_RTL
        // The file ALREADY IS the trimmed area → only the crop for the display + parallax + zoom remain
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
     * Zoom Android puts on the visible crop: unfolded or on normal displays config_wallpaperMaxScale,
     * on the folded display of a foldable none — there ImageWallpaper renders wider than the display (1831 instead of
     * 1398 px, parallax margin) and shows the centre unzoomed [MEASURED emulator Pixel Fold 10-14: folded without zoom ±2 px,
     * unfolded with zoom 0.4 grey levels deviation].
     */
    private fun zoomFor(context: Context, h: Helper, screen: Point): Float =
        if (h.isFoldedDisplay(screen)) 1f else maxScale(context)

    /** For diagnostics (Launcher settings › Advanced): what the model assumes for this display. */
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
    /** [foldedArea] = pixel area of a foldable's folded display (0 = no foldable pair known). */
    private class Helper(val sizes: Map<Int, Point>, val pairs: List<Pair<Int, Int>>, val foldedArea: Long = 0) {
        fun unfolded(o: Int) = pairs.firstOrNull { it.first == o }?.second ?: U
        /** [screen] is the (smaller) folded display of a foldable — by area, not orientation (the pairs also exist
         *  rotated, then the large display in landscape would look "folded"). */
        fun isFoldedDisplay(screen: Point) = foldedArea > 0 && screen.x.toLong() * screen.y <= foldedArea
        fun folded(o: Int) = pairs.firstOrNull { it.second == o }?.first ?: U
    }

    /** Display size (px) + density, as WallpaperDisplayHelper sees them. */
    private data class Metric(val w: Int, val h: Int, val density: Float)

    /**
     * Possible display sizes: via the (hidden) WindowManager method the system uses too; if it's blocked,
     * the sizes seen by the launcher itself (unfolded + folded, [rememberDisplay]).
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
        // All displays incl. the turned-off one (foldable: the one currently folded away) — public method, category name from AOSP
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

    /** Reported by the launcher on every window size: remembers up to two different displays (foldable: inner/outer). */
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
            // (Tablet in portrait: special case in AOSP; treated here like a landscape crop without margin)
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
