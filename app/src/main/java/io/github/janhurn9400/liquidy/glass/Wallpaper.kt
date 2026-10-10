package io.github.janhurn9400.liquidy.glass

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import io.github.janhurn9400.liquidy.design.DesignTokens

/**
 * Eigener Standard-Wallpaper (RESEARCH §5.1: System-Wallpaper ist für Drittanbieter nicht lesbar).
 * Weicher neutraler Grauverlauf, Farben aus IMG_3123 gesampelt — damit Emulator- und iPhone-Screenshots
 * direkt vergleichbar sind. Kein Apple-Asset; Fotoauswahl folgt später.
 * Positionen der Lichtflecken sind relative Bildkoordinaten (keine Layout-Werte).
 */
object Wallpaper {
    /** Farbiger Test-Wallpaper: kräftige weiche Farbflecken + feine Streifen, damit Linse/Dispersion sichtbar werden. */
    fun generateColorful(widthPx: Int, heightPx: Int): ImageBitmap {
        val bmp = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(0xFF1B2A4A.toInt())
        val w = widthPx.toFloat(); val h = heightPx.toFloat()
        fun blob(cx: Float, cy: Float, r: Float, color: Int) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.shader = RadialGradient(cx * w, cy * h, r * w, color, color and 0x00FFFFFF, Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w, h, p)
        }
        blob(0.2f, 0.15f, 0.8f, 0xFFFF5E62.toInt())
        blob(0.85f, 0.35f, 0.7f, 0xFF4FACFE.toInt())
        blob(0.3f, 0.7f, 0.8f, 0xFF43E97B.toInt())
        blob(0.8f, 0.9f, 0.7f, 0xFFFFC371.toInt())
        // feine Streifen: daran sieht man die Verzerrung am Glasrand
        val line = Paint().apply { color = 0x33FFFFFF; strokeWidth = w / 200f }
        var y = 0f
        while (y < h) { c.drawLine(0f, y, w, y + w * 0.3f, line); y += w / 18f }
        return bmp.asImageBitmap()
    }

    fun generate(widthPx: Int, heightPx: Int): ImageBitmap {
        val bmp = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(DesignTokens.wallpaperBase.toArgb())
        val w = widthPx.toFloat()
        val h = heightPx.toFloat()
        fun blob(cx: Float, cy: Float, r: Float, color: Int) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.shader = RadialGradient(cx * w, cy * h, r * w, color, color and 0x00FFFFFF, Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w, h, p)
        }
        val light = DesignTokens.wallpaperLight.toArgb()
        val dark = DesignTokens.wallpaperDark.toArgb()
        blob(0.15f, 0.0f, 0.9f, light)
        blob(0.85f, 0.45f, 0.8f, light)
        blob(0.2f, 0.65f, 0.7f, light)
        blob(0.9f, 0.15f, 0.6f, dark)
        blob(0.1f, 0.35f, 0.6f, dark)
        blob(0.5f, 1.0f, 0.9f, light)
        return bmp.asImageBitmap()
    }
}
