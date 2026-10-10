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

/** Wallpaper-Arten wie im iOS-Wallpaper-Editor (recording6): Pair, Colour, Gradient, Photo (+ Blur). */
enum class WallpaperKind { Pair, Colour, Gradient, Photo }

/**
 * Gewählter Wallpaper. [tone] = Regler im Farbblatt (0 = hell … 1 = satt/dunkel).
 * Foto: [photoZoom] ≥ 1 relativ zum bildschirmfüllenden Zuschnitt, [photoX]/[photoY] = Bildmitte (0…1).
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
    /** Im Editor bewusst gewählt → wird auch als System-Wallpaper gesetzt (sonst lassen wir das Android-Wallpaper in Ruhe). */
    val userChosen: Boolean = false,
) {
    /** Blur gibt es nur für Fotos (im Farb-/Verlaufsmodus ist der Schalter ausgegraut, recording6 25,2 s). */
    val blurAvailable get() = kind == WallpaperKind.Photo

    companion object {
        const val DEFAULT_TONE = 0.5f
    }
}

object WallpaperStore {
    private const val PREFS = "wallpaper"

    /**
     * Unser Wallpaper auch als Android-Systemhintergrund (nur Startbildschirm) setzen: Während der Wisch-nach-Hause-Geste
     * zeigt das System hinter dem fliegenden App-Fenster den System-Wallpaper — ohne Abgleich sähe man dort ein anderes Bild.
     * Nur bei Änderung (Schlüssel = Einstellung + Größe).
     */
    suspend fun syncSystemWallpaper(context: Context, s: WallpaperSettings, bmp: ImageBitmap) = withContext(Dispatchers.IO) {
        if (!s.userChosen) return@withContext // Android-Wallpaper des Nutzers nie ungefragt überschreiben
        val key = "${s.hashCode()}@${bmp.width}x${bmp.height}"
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val wm = android.app.WallpaperManager.getInstance(context)
        if (p.getString("systemSynced", null) == key && p.getInt("systemSyncedId", -1) == wm.getWallpaperId(android.app.WallpaperManager.FLAG_SYSTEM)) return@withContext
        runCatching {
            val b = bmp.asAndroidBitmap()
            val id = wm.setBitmap(b, android.graphics.Rect(0, 0, b.width, b.height), true, android.app.WallpaperManager.FLAG_SYSTEM)
            p.edit().putString("systemSynced", key).putInt("systemSyncedId", id).apply()
        }.onFailure { android.util.Log.w("WallpaperStore", "System-Wallpaper nicht gesetzt", it) }
    }

    /** Ist das aktuelle System-Wallpaper genau das, das wir zuletzt gesetzt haben? */
    fun systemIsOurs(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt("systemSyncedId", -2) ==
            android.app.WallpaperManager.getInstance(context).getWallpaperId(android.app.WallpaperManager.FLAG_SYSTEM)
    private const val PHOTO_PREFIX = "wallpaper_"

    /**
     * Kopiert ein gewähltes Foto in den App-Speicher: URIs aus dem Android-Picker gelten nur bis zum Neustart,
     * und das Original kann gelöscht werden. Alte Kopien werden entfernt.
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
            userChosen = p.getBoolean("userChosen", p.contains("systemSynced")), // alte Versionen: schon einmal synchronisiert = gewählt
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

    /** Farbe aus Grundfarbe + Regler: links hell (Richtung Weiß), Mitte = Farbe, rechts satter + dunkler (recording6 19,6–22,4 s). */
    fun toned(color: Int, tone: Float): Int {
        val hsv = FloatArray(3).also { android.graphics.Color.colorToHSV(color, it) }
        val t = tone.coerceIn(0f, 1f)
        if (hsv[1] < GRAY_SATURATION) { // Weiß/Grau/Schwarz: nur Helligkeit
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
            WallpaperKind.Pair -> Wallpaper.generate(w, h)
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

    /** Verlauf: oben heller, unten dunkler/satter [GESCHÄTZT] (iOS „Gradient“). */
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

    /** Lädt das Foto bildschirmfüllend (center-crop) mit Zoom/Position; optional stark verwischt (Blur-Schalter). */
    private fun photo(context: Context, s: WallpaperSettings, w: Int, h: Int): ImageBitmap? = runCatching {
        val src = ImageDecoder.createSource(context.contentResolver, Uri.parse(s.photoUri ?: return null))
        val full = ImageDecoder.decodeBitmap(src) { d, info, _ ->
            d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            // nicht größer dekodieren als nötig (max. 2× Bildschirm bei Zoom)
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
     * Starker, weicher Blur fürs Foto (iOS „Blur“, recording6 39,2 s): auf ¼ verkleinern, 3× Box-Blur (≈ Gauß), hochskalieren.
     * Zusätzlich leicht abgedunkelt — iOS dunkelt den verwischten Hintergrund für die Lesbarkeit ab [GESCHÄTZT].
     */
    private fun blurred(src: Bitmap): Bitmap {
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
        pass(tmp, px, h, w, w, 1)   // vertikal
    }

    /**
     * Weich verwischte Kopie des Wallpapers für Glas-Quellen (Heute-Ansicht/Mediathek): Glas soll denselben unscharfen
     * Hintergrund brechen, den man sieht. Verkleinern, 3× Box-Blur, wieder hochskalieren.
     */
    suspend fun softBlur(src: ImageBitmap, radiusPx: Float): ImageBitmap = withContext(Dispatchers.Default) {
        val b = src.asAndroidBitmap()
        val sw = (b.width / SOFT_DOWNSCALE).coerceAtLeast(1); val sh = (b.height / SOFT_DOWNSCALE).coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(b, sw, sh, true)
        val px = IntArray(sw * sh).also { small.getPixels(it, 0, sw, 0, 0, sw, sh) }
        val r = (radiusPx / SOFT_DOWNSCALE / SOFT_PASS_FACTOR).roundToInt().coerceAtLeast(1)
        repeat(3) { boxBlur(px, sw, sh, r) }
        small.setPixels(px, 0, sw, 0, 0, sw, sh)
        Bitmap.createScaledBitmap(small, b.width, b.height, true).asImageBitmap()
    }

    private const val SOFT_DOWNSCALE = 8
    private const val SOFT_PASS_FACTOR = 1.7f // 3 Box-Durchgänge ≈ Gauß; Radius so, dass es dem RenderEffect-Blur ähnelt [GESCHÄTZT]

    /** Mittlere relative Helligkeit (0…1) auf einem groben Raster — für helle/dunkle Labels. */
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
    private const val LIGHT_SAT = 0.25f        // links: Sättigung ×0,25, Helligkeit → 1
    private const val DARK_VALUE = 0.45f       // rechts: Helligkeit ×0,55
    private const val GRADIENT_TOP_SAT = 0.7f
    private const val GRADIENT_TOP_LIFT = 0.15f
    private const val GRADIENT_BOTTOM_SAT = 1.15f
    private const val GRADIENT_BOTTOM_DARK = 0.6f
    private const val BLUR_DOWNSCALE = 4
    private const val BLUR_RADIUS = 10         // bei ¼ Auflösung ≈ 40 px ≈ 13 pt je Durchgang
    private const val BLUR_DIM = 0.12f
}

/**
 * Lesezugriff auf das Android-Wallpaper (WallpaperManager.getDrawable): ab Android 14 nur mit „Alle Dateien verwalten“
 * (MANAGE_EXTERNAL_STORAGE, laut SDK-Quelle die einzige Ausnahme für Drittanbieter). Auf Bildschirmgröße zugeschnitten
 * wie das System (füllend, mittig), damit unser Bild für Unschärfe-Effekte deckungsgleich darüber liegt.
 */
object SystemWallpaper {
    fun canRead(): Boolean = android.os.Environment.isExternalStorageManager()

    @android.annotation.SuppressLint("MissingPermission")
    suspend fun read(context: Context, w: Int, h: Int): ImageBitmap? = withContext(Dispatchers.IO) {
        if (!canRead()) return@withContext null
        runCatching {
            val d = android.app.WallpaperManager.getInstance(context).getDrawable(android.app.WallpaperManager.FLAG_SYSTEM) ?: return@runCatching null
            val iw = d.intrinsicWidth.coerceAtLeast(1); val ih = d.intrinsicHeight.coerceAtLeast(1)
            val scale = max(w.toFloat() / iw, h.toFloat() / ih)
            val dw = iw * scale; val dh = ih * scale
            val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(out)
            d.setBounds(((w - dw) / 2).roundToInt(), ((h - dh) / 2).roundToInt(), ((w + dw) / 2).roundToInt(), ((h + dh) / 2).roundToInt())
            d.draw(c)
            out.asImageBitmap()
        }.onFailure { android.util.Log.w("SystemWallpaper", "nicht lesbar", it) }.getOrNull()
    }
}
