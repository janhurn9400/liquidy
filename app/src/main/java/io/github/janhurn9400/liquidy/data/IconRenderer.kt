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
     * Removes the background plate so only the motif remains (Dark/Clear/Tinted styles, shortcut glyphs).
     * Plates are often gradients (icon packs: lighter at the top, darker at the bottom), so a single dominant colour
     * isn't enough — that left half the plate standing and the tinted icon became a blob. Instead the plate is
     * flood-filled from its outer edge with a small per-step colour tolerance (follows gradients, stops at the motif's
     * contrast edge); enclosed plate-coloured holes (e.g. inside a ring) are then removed by comparing with the plate
     * colour of the same row.
     */
    private fun knockOutPlate(bmp: Bitmap) {
        val w = bmp.width; val h = bmp.height
        val px = IntArray(w * h).also { bmp.getPixels(it, 0, w, 0, 0, w, h) }
        val opaque = px.count { (it ushr 24) > 200 }
        if (opaque < px.size * PLATE_COVERAGE) return
        fun solid(i: Int) = (px[i] ushr 24) > 200
        fun dist2(a: Int, b: Int): Int {
            val dr = ((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)
            val dg = ((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)
            val db = (a and 0xFF) - (b and 0xFF)
            return dr * dr + dg * dg + db * db
        }
        val inset = (minOf(w, h) * PLATE_SEED_INSET).toInt()
        // Icon-pack bitmaps are full squares: outside their rounded plate the corners have another colour. Clear those
        // corners first (flood from the four corners), so the plate's row samples don't land in them.
        val mid = px[inset * w + w / 2]
        val corners = intArrayOf(0, w - 1, (h - 1) * w, h * w - 1)
        if (corners.all { solid(it) } && corners.all { dist2(px[it], mid) >= PLATE_DISTANCE }) {
            val outside = BooleanArray(px.size)
            val q = ArrayDeque<Int>()
            corners.forEach { outside[it] = true; q.add(it) }
            while (q.isNotEmpty()) {
                val i = q.removeFirst(); val x = i % w; val y = i / w
                fun visit(j: Int) { if (!outside[j] && solid(j) && dist2(px[i], px[j]) < PLATE_STEP && dist2(px[j], mid) >= PLATE_DISTANCE) { outside[j] = true; q.add(j) } }
                if (x > 0) visit(i - 1); if (x < w - 1) visit(i + 1); if (y > 0) visit(i - w); if (y < h - 1) visit(i + w)
            }
            for (i in px.indices) if (outside[i]) px[i] = 0
        }
        // expected plate colour per row: tracked from the middle row up and down, at each row taking the sample (a few
        // columns near both edges) closest to the neighbouring row's colour — follows vertical gradients but ignores a
        // light rim curving through the corners and motif pixels. Rows without an opaque plate get no expected colour.
        val cols = intArrayOf(inset, inset * 2, inset * 3, w / 2, w - 1 - inset * 3, w - 1 - inset * 2, w - 1 - inset)
        val rowPlate = IntArray(h) { NO_PLATE }
        fun track(y: Int, prev: Int): Int {
            var best = NO_PLATE; var bestD = Int.MAX_VALUE
            for (x in cols) { val i = y * w + x; if (!solid(i)) continue; val d = if (prev == NO_PLATE) 0 else dist2(px[i], prev); if (d < bestD) { bestD = d; best = px[i] } }
            return if (prev != NO_PLATE && bestD > PLATE_DISTANCE) prev else best
        }
        val midRow = h / 2
        rowPlate[midRow] = (0 until w).firstOrNull { solid(midRow * w + it) }?.let { px[midRow * w + minOf(it + inset, w - 1)] } ?: NO_PLATE
        for (y in midRow - 1 downTo 0) rowPlate[y] = track(y, rowPlate[y + 1])
        for (y in midRow + 1 until h) rowPlate[y] = track(y, rowPlate[y - 1])
        fun expected(i: Int) = rowPlate[i / w]
        fun platey(i: Int) = solid(i) && expected(i).let { it != NO_PLATE && dist2(px[i], it) < PLATE_DISTANCE }
        val plate = BooleanArray(px.size)
        val queue = IntArray(px.size); var head = 0; var tail = 0
        fun seed(i: Int) { if (i in px.indices && !plate[i] && platey(i)) { plate[i] = true; queue[tail++] = i } }
        // seeds: a few pixels inside the plate's edge on every side (icon packs often draw a light rim at the very edge)
        for (y in 0 until h) { seed(y * w + inset); seed(y * w + w - 1 - inset) }
        for (x in 0 until w) { seed(inset * w + x); seed((h - 1 - inset) * w + x) }
        while (head < tail) {
            val i = queue[head++]; val x = i % w; val y = i / w
            fun visit(j: Int) { if (!plate[j] && platey(j) && dist2(px[i], px[j]) < PLATE_STEP) { plate[j] = true; queue[tail++] = j } }
            if (x > 0) visit(i - 1); if (x < w - 1) visit(i + 1); if (y > 0) visit(i - w); if (y < h - 1) visit(i + w)
        }
        // nearly everything is "plate" → low-contrast motif; leave the icon as it is rather than erase it
        if (tail > opaque * PLATE_MAX_SHARE) return
        // remove the plate, the rim outside the seeds, and enclosed holes of the plate colour (e.g. inside a ring)
        for (i in px.indices) {
            val x = i % w; val y = i / w
            val band = inset * PLATE_RIM_BAND
            val rim = rowPlate[y] != NO_PLATE && (x < band || y < band || x >= w - band || y >= h - band)
            if (plate[i] || (rim && !solidMotif(px, i, w, plate, band))) { px[i] = 0; continue }
            // soft edge: the closer a pixel is to the plate colour, the more transparent it gets, and the plate's share is
            // taken out of its colour (anti-aliased motif edges otherwise became jagged steps with a dark plate-coloured seam)
            val e = expected(i); val a0 = px[i] ushr 24
            if (e == NO_PLATE || a0 == 0) continue
            val d = kotlin.math.sqrt(dist2(px[i], e).toFloat())
            val a = ((d - PLATE_EDGE_IN) / (PLATE_EDGE_OUT - PLATE_EDGE_IN)).coerceIn(0f, 1f)
            if (a >= 1f) continue
            if (a <= 0f) { px[i] = 0; continue }
            fun unmix(sh: Int) = ((((px[i] shr sh) and 0xFF) - ((e shr sh) and 0xFF) * (1 - a)) / a).toInt().coerceIn(0, 255) shl sh
            px[i] = ((a0 * a).toInt() shl 24) or unmix(16) or unmix(8) or unmix(0)
        }
        bmp.setPixels(px, 0, w, 0, 0, w, h)
    }

    /** A rim pixel is kept only if the pixel just inside the rim band at the same row/column belongs to the motif (motif touching the edge). */
    private fun solidMotif(px: IntArray, i: Int, w: Int, plate: BooleanArray, band: Int): Boolean {
        val h = px.size / w; val x = i % w; val y = i / w
        val cx = x.coerceIn(band, w - 1 - band); val cy = y.coerceIn(band, h - 1 - band)
        val j = cy * w + cx
        return (px[j] ushr 24) > 200 && !plate[j]
    }

    private const val NO_PLATE = 1 // marker: no expected plate colour (any value with alpha ≠ 0xFF works)
    private const val PLATE_COVERAGE = 0.4f   // share of opaque pixels from which a disc is assumed
    private const val PLATE_DISTANCE = 40 * 40 // colour distance² to the row's plate colour for enclosed holes
    private const val PLATE_EDGE_IN = 24f      // colour distance to the plate: fully transparent below …
    private const val PLATE_EDGE_OUT = 110f    // … fully opaque above (anti-aliased motif edges in between)
    private const val PLATE_STEP = 22 * 22     // colour distance² between neighbouring plate pixels (gradient steps)
    private const val PLATE_SEED_INSET = 0.04f // second seed this far inside the edge (× size), past a light rim
    private const val PLATE_RIM_BAND = 2       // rim band removed outside the motif (× seed inset; the rim curves into the corners)
    private const val PLATE_MAX_SHARE = 0.995f // more than this share of the icon counted as plate → keep the icon
    private const val GLYPH_OVERSAMPLE = 4 // render the icon larger so the crop stays sharp
    private const val GLYPH_FILL = 0.92f   // the motif fills the box (glyphs in recording4 ≈ 16 pt in a 16.33 pt box)
    private const val ALPHA_MIN = 40
}
