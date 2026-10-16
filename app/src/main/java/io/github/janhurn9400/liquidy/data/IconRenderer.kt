package io.github.janhurn9400.liquidy.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/**
 * Renders an app icon into the iOS squircle mask (T-ICON / T-ICON-R).
 * Adaptive icons: foreground and background layers are drawn without the system mask
 * (layer = 108 dp, visible area = 72 dp → layer 1.5× as large as the icon, 25 % overhang per side)
 * — square and unclipped; the squircle mask is only applied when drawing (ContinuousRoundedShape),
 * so T-ICON-R stays live-adjustable in the debug panel.
 * Legacy icons: white background, icon centred at 80 %.
 */
class IconLayers(val glyph: ImageBitmap, val mono: ImageBitmap)

object IconRenderer {
    private const val LEGACY_SCALE = 0.8f // [ESTIMATED] iOS has no legacy icons

    /** [fullBleed]: glyph from an icon pack — fills the square (the squircle mask clips when drawing). */
    fun render(drawable: Drawable, sizePx: Int, fullBleed: Boolean = false): ImageBitmap {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        if (fullBleed && drawable !is AdaptiveIconDrawable) {
            drawable.setBounds(0, 0, sizePx, sizePx)
            drawable.draw(canvas)
        } else if (drawable is AdaptiveIconDrawable) {
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
     * Layers for the iOS icon styles (Dark/Clear/Tinted, recording7):
     *  - [IconLayers.glyph]: motif in original colours without the background plate (Dark = colourful motif on black),
     *  - [IconLayers.mono]: monochrome mask (monochrome layer from API 33, otherwise from [glyph]) for Clear/Tinted.
     * Same geometry as [render] (layer 1.5× of the icon), so the motifs sit congruently.
     */
    fun layers(drawable: Drawable, sizePx: Int, fullBleed: Boolean = false): IconLayers {
        val glyph = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val c = Canvas(glyph)
        val inset = (sizePx * 0.25f).toInt()
        if (fullBleed && drawable !is AdaptiveIconDrawable) {
            drawable.setBounds(0, 0, sizePx, sizePx); drawable.draw(c)
        } else if (drawable is AdaptiveIconDrawable) {
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
     * Shortcut icon as a monochrome white glyph (iOS menus show outline glyphs, not colourful icons):
     * monochrome layer (API 33), otherwise the whole icon with the background colour subtracted.
     * The motif is then cropped to its bounds and fitted into the box —
     * shortcut icons have very different inner margins.
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
     * Many shortcut icons have no monochrome layer but a filled colour disc with a white motif in the foreground
     * (e.g. Gmail "Compose") — as a silhouette that would just be a circle. If the area is mostly opaque, the
     * dominant colour (= disc) is removed and only the motif that differs from it remains.
     */
    private fun knockOutPlate(bmp: Bitmap) {
        val w = bmp.width; val h = bmp.height
        val px = IntArray(w * h).also { bmp.getPixels(it, 0, w, 0, 0, w, h) }
        // Histogram over coarsely quantised colours (4 bits per channel) instead of boxing lists — runs for every icon
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

    private const val PLATE_COVERAGE = 0.4f   // share of opaque pixels from which a disc is assumed
    private const val PLATE_DISTANCE = 48 * 48 // colour distance² up to which a pixel counts as part of the disc
    private const val GLYPH_OVERSAMPLE = 4 // render the icon larger so the crop stays sharp
    private const val GLYPH_FILL = 0.92f   // the motif fills the box (glyphs in recording4 ≈ 16 pt in a 16.33 pt box)
    private const val ALPHA_MIN = 40
}
