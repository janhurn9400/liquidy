package io.github.janhurn9400.liquidy.ui.home

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.ViewGroup
import android.widget.FrameLayout
import io.github.janhurn9400.liquidy.data.GestureNavContract
import io.github.janhurn9400.liquidy.design.DesignTokens

/**
 * Icon surface for the swipe-home gesture (like Launcher3 FloatingSurfaceView): a SurfaceView exactly above the icon,
 * into which the icon is drawn. Its SurfaceControl goes with the position to the system, which crossfades the app window
 * into it at the end. Afterwards (finish callback or timeout) it's removed and the real icon is shown again.
 */
object FlyingIconSurface {
    private var current: SurfaceView? = null

    fun show(activity: Activity, contract: GestureNavContract, rect: RectF, icon: Bitmap?, onDone: () -> Unit, cornerPx: Float? = null) {
        remove()
        val root = activity.window.decorView as? ViewGroup ?: run { contract.sendEndPosition(rect); onDone(); return }
        if (icon == null) { contract.sendEndPosition(rect, null, onDone); return }
        val view = SurfaceView(activity).apply {
            setZOrderOnTop(true)
            holder.setFormat(PixelFormat.TRANSLUCENT)
            layoutParams = FrameLayout.LayoutParams(rect.width().toInt(), rect.height().toInt()).apply {
                leftMargin = rect.left.toInt(); topMargin = rect.top.toInt()
            }
        }
        var sent = false
        val finish = {
            remove()
            onDone()
        }
        view.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(h: SurfaceHolder) {
                val c = h.lockHardwareCanvas()
                c.drawColor(0, android.graphics.PorterDuff.Mode.CLEAR)
                val w = rect.width(); val hh = rect.height()
                // Squircle approximation (rounded square with the icon corner radius)
                val r = cornerPx ?: (DesignTokens.iconCorner.value / DesignTokens.iconSize.value * w)
                c.clipPath(Path().apply { addRoundRect(RectF(0f, 0f, w, hh), r, r, Path.Direction.CW) })
                c.drawBitmap(icon, null, RectF(0f, 0f, w, hh), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
                h.unlockCanvasAndPost(c)
                if (!sent) { sent = true; contract.sendEndPosition(rect, view.surfaceControl) { view.post { finish() } } }
            }
            override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, hh: Int) = Unit
            override fun surfaceDestroyed(h: SurfaceHolder) = Unit
        })
        root.addView(view)
        current = view
        // Safety net: in case no finish callback comes (system bug, see Android issue 194427611)
        view.postDelayed({ if (current === view) finish() }, SAFETY_MS)
    }

    fun remove() {
        val v = current ?: return
        current = null
        (v.parent as? ViewGroup)?.removeView(v)
    }

    private const val SAFETY_MS = 1500L
}
