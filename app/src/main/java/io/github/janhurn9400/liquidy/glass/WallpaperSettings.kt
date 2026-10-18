package io.github.janhurn9400.liquidy.glass

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import io.github.janhurn9400.liquidy.design.DesignTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

/** Wallpaper kinds like in the iOS wallpaper editor (recording6): Pair, Colour, Gradient, Photo (+ Blur). */
enum class WallpaperKind { Pair, Colour, Gradient, Photo }

/**
 * Chosen wallpaper. [tone] = slider in the colour sheet (0 = light … 1 = rich/dark).
 * Photo: [photoZoom] ≥ 1 relative to the screen-filling crop, [photoX]/[photoY] = image centre (0…1).
 */
data class WallpaperSettings(
    val kind: WallpaperKind = WallpaperKind.Pair,
    val color: Int = DesignTokens.wallpaperSwatches[0],
    val tone: Float = DEFAULT_TONE,
    val photoUri: String? = null,
    val photoZoom: Float = 1f,
    val photoX: Float = 0.5f,
    val photoY: Float = 0.5f,
    val blur: Boolean = false,
    /** Deliberately chosen in the editor → is also set as the system wallpaper (otherwise we leave the Android wallpaper alone). */
    val userChosen: Boolean = false,
) {
    /** Blur exists only for photos (in colour/gradient mode the switch is greyed out, recording6 25.2 s). */
    val blurAvailable get() = kind == WallpaperKind.Photo || kind == WallpaperKind.Pair

    companion object {
        const val DEFAULT_TONE = 0.5f
    }
}

object WallpaperStore {
    /** Debug: test wallpaper set via adb → don't overwrite it with ours. */
    @Volatile var testSuppressSync = false
    private const val PREFS = "wallpaper"

    /**
     * Also set our wallpaper as the Android system background (home screen only): during the swipe-home gesture
     * the system shows the system wallpaper behind the flying app window — without matching you'd see a different image there.
     * Only on change (key = setting + size).
     */
    suspend fun syncSystemWallpaper(context: Context, s: WallpaperSettings, bmp: ImageBitmap) = withContext(Dispatchers.IO) {
        if (!s.userChosen) return@withContext // never overwrite the user's Android wallpaper without asking
        // "Pair": Android's image without blur stays untouched; with blur we set the blurred image prepared so that
        // Android displays it exactly as shown (otherwise it would be cropped/zoomed again)
        val pair = s.kind == WallpaperKind.Pair
        if (pair && !s.blur && !systemIsOurs(context)) return@withContext
        if (testSuppressSync) return@withContext
        val key = "${s.hashCode()}@${bmp.width}x${bmp.height}:plain"
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val wm = android.app.WallpaperManager.getInstance(context)
        if (p.getString("systemSynced", null) == key && p.getInt("systemSyncedId", -1) == wm.getWallpaperId(android.app.WallpaperManager.FLAG_SYSTEM)) return@withContext
        runCatching {
            val b = if (pair) shownAs(context, bmp.asAndroidBitmap()) else bmp.asAndroidBitmap()
            val id = wm.setBitmap(b, android.graphics.Rect(0, 0, b.width, b.height), true, android.app.WallpaperManager.FLAG_SYSTEM)
            p.edit().putString("systemSynced", key).putInt("systemSyncedId", id).apply()
        }.onFailure { android.util.Log.w("WallpaperStore", "System wallpaper not set", it) }
    }

    /**
     * Our image as Android shows it as the system wallpaper on this display (crop + zoom, WallpaperCropModel) —
     * source for the glass and for the self-drawn blur, so both lie congruently over Android's image.
     */
    fun asShownBySystem(context: Context, bmp: ImageBitmap): ImageBitmap {
        val r = WallpaperCropModel.shownRegion(context, bmp.width, bmp.height, bmp.width, bmp.height) ?: return bmp
        val out = android.graphics.Bitmap.createBitmap(bmp.width, bmp.height, android.graphics.Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(out).drawBitmap(bmp.asAndroidBitmap(),
            android.graphics.Rect(kotlin.math.round(r.left).toInt(), kotlin.math.round(r.top).toInt(), kotlin.math.round(r.right).toInt(), kotlin.math.round(r.bottom).toInt()),
            android.graphics.Rect(0, 0, bmp.width, bmp.height), android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
        return out.asImageBitmap()
    }

    /**
     * Base image for "Pair": Android's image as it's shown — saved once while it doesn't come from us yet
     * (otherwise the original would be lost as soon as we set the blurred version).
     */
    fun pairBase(context: Context, w: Int, h: Int): android.graphics.Bitmap? {
        val f = java.io.File(context.filesDir, "pair_base.png")
        if (!systemIsOurs(context)) {
            val cur = kotlinx.coroutines.runBlocking { SystemWallpaper.read(context, w, h) }?.asAndroidBitmap()
            if (cur != null) { runCatching { f.outputStream().use { cur.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) } }; return cur }
        }
        return runCatching { android.graphics.BitmapFactory.decodeFile(f.path) }.getOrNull()
            ?.let { if (it.width == w && it.height == h) it else android.graphics.Bitmap.createScaledBitmap(it, w, h, true) }
    }

    /** Inverse of [asShownBySystem]: prepare the image so that Android displays exactly [shown]. */
    private fun shownAs(context: Context, shown: android.graphics.Bitmap): android.graphics.Bitmap {
        val r = WallpaperCropModel.shownRegion(context, shown.width, shown.height, shown.width, shown.height) ?: return shown
        val out = android.graphics.Bitmap.createBitmap(shown.width, shown.height, android.graphics.Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(out); val paint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
        c.drawBitmap(shown, null, android.graphics.Rect(0, 0, shown.width, shown.height), paint) // border (only visible during transitions)
        c.drawBitmap(shown, null, android.graphics.RectF(r.left, r.top, r.right, r.bottom), paint)
        return out
    }

    /** Is the current system wallpaper exactly the one we last set? */
    fun systemIsOurs(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt("systemSyncedId", -2) ==
            android.app.WallpaperManager.getInstance(context).getWallpaperId(android.app.WallpaperManager.FLAG_SYSTEM)
    private const val PHOTO_PREFIX = "wallpaper_"

    /**
     * Copies a chosen photo into app storage: URIs from the Android picker are only valid until restart,
     * and the original can be deleted. Old copies are removed.
     */
    suspend fun keepPhoto(context: Context, s: WallpaperSettings): WallpaperSettings = withContext(Dispatchers.IO) {
        val uri = s.photoUri ?: return@withContext s
        if (s.kind != WallpaperKind.Photo) return@withContext s
        val dir = context.filesDir
        if (uri.startsWith(Uri.fromFile(dir).toString())) return@withContext s
        runCatching {
            val file = java.io.File(dir, PHOTO_PREFIX + System.currentTimeMillis() + ".img")
            context.contentResolver.openInputStream(Uri.parse(uri))!!.use { input -> file.outputStream().use { input.copyTo(it) } }
            dir.listFiles { f -> f.name.startsWith(PHOTO_PREFIX) && f != file }?.forEach { it.delete() }
            s.copy(photoUri = Uri.fromFile(file).toString())
        }.getOrDefault(s)
    }

    fun load(context: Context): WallpaperSettings {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return WallpaperSettings(
            kind = runCatching { WallpaperKind.valueOf(p.getString("kind", null) ?: "") }.getOrDefault(WallpaperKind.Pair),
            color = p.getInt("color", DesignTokens.wallpaperSwatches[0]),
            tone = p.getFloat("tone", WallpaperSettings.DEFAULT_TONE),
            photoUri = p.getString("photo", null),
            photoZoom = p.getFloat("zoom", 1f),
            photoX = p.getFloat("x", 0.5f),
            photoY = p.getFloat("y", 0.5f),
            blur = p.getBoolean("blur", false),
            userChosen = p.getBoolean("userChosen", p.contains("systemSynced")), // old versions: synced once = chosen
        )
    }

    fun save(context: Context, s: WallpaperSettings) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("kind", s.kind.name).putInt("color", s.color).putFloat("tone", s.tone)
            .putString("photo", s.photoUri).putFloat("zoom", s.photoZoom).putFloat("x", s.photoX).putFloat("y", s.photoY)
            .putBoolean("blur", s.blur).putBoolean("userChosen", s.userChosen)
            .apply()
    }
}

object WallpaperRenderer {

    /** Colour from base colour + slider: left light (towards white), middle = colour, right richer + darker (recording6 19.6–22.4 s). */
    fun toned(color: Int, tone: Float): Int {
        val hsv = FloatArray(3).also { android.graphics.Color.colorToHSV(color, it) }
        val t = tone.coerceIn(0f, 1f)
        if (hsv[1] < GRAY_SATURATION) { // white/grey/black: brightness only
            hsv[2] = (hsv[2] + (0.5f - t) * GRAY_RANGE).coerceIn(0f, 1f)
            return android.graphics.Color.HSVToColor(hsv)
        }
        if (t < 0.5f) {
            val k = t / 0.5f
            hsv[1] *= LIGHT_SAT + (1f - LIGHT_SAT) * k
            hsv[2] = hsv[2] + (1f - hsv[2]) * (1f - k)
        } else {
            val k = (t - 0.5f) / 0.5f
            hsv[1] = hsv[1] + (1f - hsv[1]) * k
            hsv[2] *= 1f - DARK_VALUE * k
        }
        return android.graphics.Color.HSVToColor(hsv)
    }

    suspend fun render(context: Context, s: WallpaperSettings, w: Int, h: Int): ImageBitmap = withContext(Dispatchers.Default) {
        when (s.kind) {
            // "Pair" = the image set in Android (as Android shows it); without read access/with a live wallpaper our own
            WallpaperKind.Pair -> (WallpaperStore.pairBase(context, w, h) ?: Wallpaper.generate(w, h).asAndroidBitmap()).let { b ->
                (if (s.blur) blurred(b) else b).asImageBitmap()
            }
            WallpaperKind.Colour -> solid(toned(s.color, s.tone), w, h)
            WallpaperKind.Gradient -> gradient(toned(s.color, s.tone), w, h)
            WallpaperKind.Photo -> photo(context, s, w, h) ?: Wallpaper.generate(w, h)
        }
    }

    private fun solid(c: Int, w: Int, h: Int): ImageBitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(c)
        return bmp.asImageBitmap()
    }

    /** Gradient: lighter at the top, darker/richer at the bottom [ESTIMATED] (iOS "Gradient"). */
    private fun gradient(c: Int, w: Int, h: Int): ImageBitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val hsv = FloatArray(3).also { android.graphics.Color.colorToHSV(c, it) }
        val top = android.graphics.Color.HSVToColor(floatArrayOf(hsv[0], hsv[1] * GRADIENT_TOP_SAT, (hsv[2] + GRADIENT_TOP_LIFT).coerceAtMost(1f)))
        val bottom = android.graphics.Color.HSVToColor(floatArrayOf(hsv[0], (hsv[1] * GRADIENT_BOTTOM_SAT).coerceAtMost(1f), hsv[2] * GRADIENT_BOTTOM_DARK))
        Canvas(bmp).drawRect(0f, 0f, w.toFloat(), h.toFloat(), Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, h.toFloat(), top, bottom, Shader.TileMode.CLAMP)
        })
        return bmp.asImageBitmap()
    }

    /** Loads the photo screen-filling (center-crop) with zoom/position; optionally strongly blurred (Blur switch). */
    private fun photo(context: Context, s: WallpaperSettings, w: Int, h: Int): ImageBitmap? = runCatching {
        val src = ImageDecoder.createSource(context.contentResolver, Uri.parse(s.photoUri ?: return null))
        val full = ImageDecoder.decodeBitmap(src) { d, info, _ ->
            d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            // don't decode larger than needed (max. 2× screen when zoomed)
            val cover = max(w.toFloat() / info.size.width, h.toFloat() / info.size.height) * s.photoZoom.coerceAtLeast(1f)
            if (cover < 1f) d.setTargetSize((info.size.width * cover).roundToInt().coerceAtLeast(w), (info.size.height * cover).roundToInt().coerceAtLeast(h))
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val scale = max(w.toFloat() / full.width, h.toFloat() / full.height) * s.photoZoom.coerceAtLeast(1f)
        val dw = full.width * scale; val dh = full.height * scale
        val left = (w / 2f - s.photoX * dw).coerceIn(w - dw, 0f)
        val top = (h / 2f - s.photoY * dh).coerceIn(h - dh, 0f)
        Canvas(out).drawBitmap(full, null, android.graphics.RectF(left, top, left + dw, top + dh), Paint(Paint.FILTER_BITMAP_FLAG))
        (if (s.blur) blurred(out) else out).asImageBitmap()
    }.getOrNull()

    /**
     * Strong, soft blur for the photo (iOS "Blur", recording6 39.2 s): shrink to ¼, 3× box blur (≈ Gaussian), scale up.
     * Additionally slightly darkened — iOS darkens the blurred background for legibility [ESTIMATED].
     */
    /** Baked-in blur (like in the preview animation at the end). */
    fun blurredImage(src: ImageBitmap): ImageBitmap = blurred(src.asAndroidBitmap()).asImageBitmap()

    /** Preview: corresponding blur radius (px, RenderEffect) and dimming for [blurred]. */
    const val PREVIEW_BLUR_RADIUS_PX = 72f // 3× box r=10 at ¼ resolution ≈ Gaussian σ 42 px ≈ radius 72
    const val PREVIEW_DIM = 0.12f // = BLUR_DIM

    internal fun blurred(src: Bitmap): Bitmap {
        val sw = src.width / BLUR_DOWNSCALE; val sh = src.height / BLUR_DOWNSCALE
        val small = Bitmap.createScaledBitmap(src, sw, sh, true)
        val px = IntArray(sw * sh).also { small.getPixels(it, 0, sw, 0, 0, sw, sh) }
        repeat(3) { boxBlur(px, sw, sh, BLUR_RADIUS) }
        small.setPixels(px, 0, sw, 0, 0, sw, sh)
        val out = Bitmap.createScaledBitmap(small, src.width, src.height, true).copy(Bitmap.Config.ARGB_8888, true)
        Canvas(out).drawColor(android.graphics.Color.argb((BLUR_DIM * 255).roundToInt(), 0, 0, 0))
        return out
    }

    private fun boxBlur(px: IntArray, w: Int, h: Int, r: Int) {
        val tmp = IntArray(px.size)
        fun pass(src: IntArray, dst: IntArray, len: Int, lines: Int, step: Int, lineStep: Int) {
            for (l in 0 until lines) {
                var ra = 0; var ga = 0; var ba = 0
                val base = l * lineStep
                fun at(i: Int) = src[base + i.coerceIn(0, len - 1) * step]
                for (i in -r..r) { val c = at(i); ra += (c shr 16) and 0xFF; ga += (c shr 8) and 0xFF; ba += c and 0xFF }
                val n = 2 * r + 1
                for (i in 0 until len) {
                    dst[base + i * step] = (0xFF shl 24) or ((ra / n) shl 16) or ((ga / n) shl 8) or (ba / n)
                    val add = at(i + r + 1); val sub = at(i - r)
                    ra += ((add shr 16) and 0xFF) - ((sub shr 16) and 0xFF)
                    ga += ((add shr 8) and 0xFF) - ((sub shr 8) and 0xFF)
                    ba += (add and 0xFF) - (sub and 0xFF)
                }
            }
        }
        pass(px, tmp, w, h, 1, w)   // horizontal
        pass(tmp, px, h, w, w, 1)   // vertical
    }

    /**
     * Softly blurred copy of the wallpaper for glass sources (Today view/App Library): the glass should refract the same blurred
     * background you see. Shrink, 3× box blur, scale up again.
     */
    suspend fun softBlur(src: ImageBitmap, radiusPx: Float): ImageBitmap = withContext(Dispatchers.Default) {
        val b = src.asAndroidBitmap()
        val sw = (b.width / SOFT_DOWNSCALE).coerceAtLeast(1); val sh = (b.height / SOFT_DOWNSCALE).coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(b, sw, sh, true)
        val px = IntArray(sw * sh).also { small.getPixels(it, 0, sw, 0, 0, sw, sh) }
        val r = (radiusPx / SOFT_DOWNSCALE / SOFT_PASS_FACTOR).roundToInt().coerceAtLeast(1)
        repeat(3) { boxBlur(px, sw, sh, r) }
        small.setPixels(px, 0, sw, 0, 0, sw, sh)
        // immutable → cached on the GPU instead of uploaded on every draw
        Bitmap.createScaledBitmap(small, b.width, b.height, true).let { it.copy(it.config ?: Bitmap.Config.ARGB_8888, false) }.asImageBitmap()
    }

    private const val SOFT_DOWNSCALE = 8
    private const val SOFT_PASS_FACTOR = 1.7f // 3 box passes ≈ Gaussian; radius such that it resembles the RenderEffect blur [ESTIMATED]

    /** Mean relative brightness (0…1) on a coarse grid — for light/dark labels. */
    fun luminance(bmp: ImageBitmap): Float {
        val b = bmp.asAndroidBitmap()
        val n = LUMA_SAMPLES
        var sum = 0.0
        for (j in 0 until n) for (i in 0 until n) {
            val c = b.getPixel((i + 0.5f).times(b.width / n.toFloat()).toInt(), (j + 0.5f).times(b.height / n.toFloat()).toInt())
            val r = ((c shr 16) and 0xFF) / 255.0; val g = ((c shr 8) and 0xFF) / 255.0; val bl = (c and 0xFF) / 255.0
            sum += 0.2126 * r + 0.7152 * g + 0.0722 * bl
        }
        return (sum / (n * n)).toFloat()
    }

    private const val LUMA_SAMPLES = 24
    private const val GRAY_SATURATION = 0.08f
    private const val GRAY_RANGE = 0.5f
    private const val LIGHT_SAT = 0.25f        // left: saturation ×0.25, brightness → 1
    private const val DARK_VALUE = 0.45f       // right: brightness ×0.55
    private const val GRADIENT_TOP_SAT = 0.7f
    private const val GRADIENT_TOP_LIFT = 0.15f
    private const val GRADIENT_BOTTOM_SAT = 1.15f
    private const val GRADIENT_BOTTOM_DARK = 0.6f
    private const val BLUR_DOWNSCALE = 4
    private const val BLUR_RADIUS = 10         // at ¼ resolution ≈ 40 px ≈ 13 pt per pass
    private const val BLUR_DIM = 0.12f
}

/**
 * Read access to the Android wallpaper (WallpaperManager.getDrawable): from Android 14 only with "All files access"
 * (MANAGE_EXTERNAL_STORAGE, according to the SDK source the only exception for third parties). Cropped to screen size
 * like the system (filling, centred), so our image lies congruently over it for blur effects.
 */
object SystemWallpaper {
    fun canRead(): Boolean = android.os.Environment.isExternalStorageManager()

    @android.annotation.SuppressLint("MissingPermission")
    suspend fun read(context: Context, w: Int, h: Int): ImageBitmap? = withContext(Dispatchers.IO) {
        if (!canRead()) return@withContext null
        runCatching {
            val d = android.app.WallpaperManager.getInstance(context).getDrawable(android.app.WallpaperManager.FLAG_SYSTEM) ?: return@runCatching null
            val iw = d.intrinsicWidth.coerceAtLeast(1); val ih = d.intrinsicHeight.coerceAtLeast(1)
            val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(out)
            // Android shows only a crop (+ zoom) of its saved file → take exactly that, screen-filling,
            // otherwise we'd draw a differently zoomed image than Android (visible as a frame/offset during the gesture)
            val r = WallpaperCropModel.shownRegionInCroppedFile(context, w, h, iw, ih)
            if (r != null) {
                val sc = w / r.width()
                d.setBounds((-r.left * sc).roundToInt(), (-r.top * sc).roundToInt(), ((iw - r.left) * sc).roundToInt(), ((ih - r.top) * sc).roundToInt())
            } else {
                val scale = max(w.toFloat() / iw, h.toFloat() / ih)
                val dw = iw * scale; val dh = ih * scale
                d.setBounds(((w - dw) / 2).roundToInt(), ((h - dh) / 2).roundToInt(), ((w + dw) / 2).roundToInt(), ((h + dh) / 2).roundToInt())
            }
            d.draw(c)
            out.asImageBitmap()
        }.onFailure { android.util.Log.w("SystemWallpaper", "not readable", it) }.getOrNull()
    }
}
