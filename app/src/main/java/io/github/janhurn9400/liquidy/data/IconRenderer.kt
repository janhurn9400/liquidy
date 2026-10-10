package io.github.janhurn9400.liquidy.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/**
 * Rendert ein App-Icon in die iOS-Squircle-Maske (T-ICON / T-ICON-R).
 * Adaptive Icons: Vorder- und Hintergrund-Layer werden ohne System-Maske gezeichnet
 * (Layer = 108 dp, sichtbarer Bereich = 72 dp → Layer 1,5× so groß wie das Icon, 25 % Überstand je Seite)
 * — quadratisch und unbeschnitten; die Squircle-Maske kommt erst beim Zeichnen (ContinuousRoundedShape),
 * damit T-ICON-R live im Debug-Panel justierbar bleibt.
 * Legacy-Icons: weißer Hintergrund, Icon mittig auf 80 %.
 */
class IconLayers(val glyph: ImageBitmap, val mono: ImageBitmap)

object IconRenderer {
    private const val LEGACY_SCALE = 0.8f // [GESCHÄTZT] iOS kennt keine Legacy-Icons

    fun render(drawable: Drawable, sizePx: Int): ImageBitmap {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        if (drawable is AdaptiveIconDrawable) {
            val inset = (sizePx * 0.25f).toInt()
            listOfNotNull(drawable.background, drawable.foreground).forEach { layer ->
                layer.setBounds(-inset, -inset, sizePx + inset, sizePx + inset)
                layer.draw(canvas)
            }
        } else {
            canvas.drawColor(android.graphics.Color.WHITE)
            val s = (sizePx * LEGACY_SCALE).toInt()
            val o = (sizePx - s) / 2
            drawable.setBounds(o, o, o + s, o + s)
            drawable.draw(canvas)
        }
        return bmp.asImageBitmap()
    }

    /**
     * Ebenen für die iOS-Icon-Stile (Dark/Clear/Tinted, recording7):
     *  - [IconLayers.glyph]: Motiv in Originalfarben ohne Hintergrundplatte (Dark = farbiges Motiv auf Schwarz),
     *  - [IconLayers.mono]: einfarbige Maske (Monochrom-Layer ab API 33, sonst aus [glyph]) für Clear/Tinted.
     * Gleiche Geometrie wie [render] (Layer 1,5× des Icons), damit die Motive deckungsgleich sitzen.
     */
    fun layers(drawable: Drawable, sizePx: Int): IconLayers {
        val glyph = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val c = Canvas(glyph)
        val inset = (sizePx * 0.25f).toInt()
        if (drawable is AdaptiveIconDrawable) {
            listOfNotNull(drawable.background, drawable.foreground).forEach { it.setBounds(-inset, -inset, sizePx + inset, sizePx + inset); it.draw(c) }
        } else {
            val sz = (sizePx * LEGACY_SCALE).toInt(); val o = (sizePx - sz) / 2
            drawable.setBounds(o, o, o + sz, o + sz); drawable.draw(c)
        }
        knockOutPlate(glyph)
        val monoLayer = (drawable as? AdaptiveIconDrawable)?.monochrome
        val mono = if (monoLayer != null) {
            Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888).also { b ->
                monoLayer.setBounds(-inset, -inset, sizePx + inset, sizePx + inset); monoLayer.draw(Canvas(b))
            }
        } else glyph.copy(Bitmap.Config.ARGB_8888, true)
        Canvas(mono).drawColor(android.graphics.Color.WHITE, android.graphics.PorterDuff.Mode.SRC_IN)
        return IconLayers(glyph.asImageBitmap(), mono.asImageBitmap())
    }

    /**
     * Shortcut-Symbol als einfarbig weiße Glyphe (iOS-Menüs zeigen Umriss-Symbole, keine bunten Icons):
     * Monochrom-Layer (API 33), sonst das ganze Icon mit herausgerechneter Hintergrundfarbe.
     * Das Motiv wird anschließend auf seine Begrenzung zugeschnitten und in die Box eingepasst —
     * Shortcut-Icons haben sehr unterschiedliche Innenränder.
     */
    fun glyph(drawable: Drawable, sizePx: Int): ImageBitmap? {
        val big = sizePx * GLYPH_OVERSAMPLE
        val bmp = Bitmap.createBitmap(big, big, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val mono = (drawable as? AdaptiveIconDrawable)?.monochrome
        val layers = when {
            mono != null -> listOf(mono)
            drawable is AdaptiveIconDrawable -> listOfNotNull(drawable.background, drawable.foreground)
            else -> listOf(drawable)
        }
        layers.forEach { it.setBounds(0, 0, big, big); it.draw(canvas) }
        if (mono == null) knockOutPlate(bmp)
        val box = alphaBounds(bmp) ?: return null
        val out = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val oc = Canvas(out)
        val scale = sizePx * GLYPH_FILL / maxOf(box.width(), box.height())
        val dw = box.width() * scale; val dh = box.height() * scale
        val dst = android.graphics.RectF((sizePx - dw) / 2, (sizePx - dh) / 2, (sizePx + dw) / 2, (sizePx + dh) / 2)
        oc.drawBitmap(bmp, box, dst, android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
        oc.drawColor(android.graphics.Color.WHITE, android.graphics.PorterDuff.Mode.SRC_IN)
        return out.asImageBitmap()
    }

    private fun alphaBounds(bmp: Bitmap): android.graphics.Rect? {
        val w = bmp.width; val h = bmp.height
        val px = IntArray(w * h).also { bmp.getPixels(it, 0, w, 0, 0, w, h) }
        var l = w; var t = h; var r = -1; var b = -1
        for (y in 0 until h) for (x in 0 until w) if ((px[y * w + x] ushr 24) > ALPHA_MIN) {
            if (x < l) l = x; if (x > r) r = x; if (y < t) t = y; if (y > b) b = y
        }
        return if (r < l) null else android.graphics.Rect(l, t, r + 1, b + 1)
    }

    /**
     * Viele Shortcut-Icons haben keinen Monochrom-Layer, sondern eine gefüllte Farbscheibe mit weißem Motiv im Vordergrund
     * (z. B. Gmail „Compose“) — als Silhouette wäre das nur ein Kreis. Ist die Fläche überwiegend deckend, wird die
     * dominante Farbe (= Scheibe) entfernt und nur das davon abweichende Motiv bleibt.
     */
    private fun knockOutPlate(bmp: Bitmap) {
        val w = bmp.width; val h = bmp.height
        val px = IntArray(w * h).also { bmp.getPixels(it, 0, w, 0, 0, w, h) }
        // Histogramm über grob quantisierte Farben (4 Bit je Kanal) statt Boxing-Listen — läuft für jedes Icon
        val hist = IntArray(4096)
        var opaque = 0
        for (c in px) if ((c ushr 24) > 200) { opaque++; hist[((c shr 12) and 0xF00) or ((c shr 8) and 0xF0) or ((c shr 4) and 0xF)]++ }
        if (opaque < px.size * PLATE_COVERAGE) return
        val q = hist.indices.maxBy { hist[it] }
        val plate = ((q and 0xF00) shl 12) or ((q and 0xF0) shl 8) or ((q and 0xF) shl 4)
        for (i in px.indices) {
            val c = px[i]
            if ((c ushr 24) == 0) continue
            val dr = ((c shr 16) and 0xF0) - ((plate shr 16) and 0xF0)
            val dg = ((c shr 8) and 0xF0) - ((plate shr 8) and 0xF0)
            val db = (c and 0xF0) - (plate and 0xF0)
            if (dr * dr + dg * dg + db * db < PLATE_DISTANCE) px[i] = 0
        }
        bmp.setPixels(px, 0, w, 0, 0, w, h)
    }

    private const val PLATE_COVERAGE = 0.4f   // Anteil deckender Pixel, ab dem eine Scheibe angenommen wird
    private const val PLATE_DISTANCE = 48 * 48 // Farbabstand², bis zu dem ein Pixel zur Scheibe zählt
    private const val GLYPH_OVERSAMPLE = 4 // Icon größer rendern, damit der Zuschnitt scharf bleibt
    private const val GLYPH_FILL = 0.92f   // Motiv füllt die Box (Symbole in recording4 ≈ 16 pt bei 16,33-pt-Box)
    private const val ALPHA_MIN = 40
}
