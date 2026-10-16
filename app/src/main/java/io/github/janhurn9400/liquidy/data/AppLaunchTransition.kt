package io.github.janhurn9400.liquidy.data

import android.app.ActivityOptions
import android.graphics.Rect
import android.graphics.RectF
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Choreographer
import android.view.SurfaceControl
import android.window.IRemoteTransition
import android.window.IRemoteTransitionFinishedCallback
import android.window.TransitionInfo
import android.window.WindowAnimationState
import io.github.janhurn9400.liquidy.motion.Motion
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * iOS-like open animation: the app window grows out of the icon with a spring, the corners round from the
 * icon radius to the screen radius. Like Launcher3 via ActivityOptions.makeRemoteTransition (shell transitions).
 * Only possible as a system app with CONTROL_REMOTE_APP_TRANSITION_ANIMATIONS + hidden API allowlist (Animation Module);
 * otherwise [options] returns null and the caller uses the normal system animation.
 */
object AppLaunchTransition {

    private const val TAG = "AppLaunchTransition"
    private const val TRANSIT_OPEN = 1
    private const val TRANSIT_TO_FRONT = 3
    private const val FLAG_IS_WALLPAPER = 1 shl 1

    /** Package → widget key the app was last opened from (target for swiping back instead of an icon). */
    val widgetOrigins = java.util.concurrent.ConcurrentHashMap<String, String>()
    /** Widget key → image of the widget on tap (surface the window crossfades into when swiping back). */
    val widgetSnapshots = java.util.concurrent.ConcurrentHashMap<String, android.graphics.Bitmap>()

    /** Captures the screen area [rect] of our own window (PixelCopy) and remembers it for [key]. */
    fun snapshotWidget(window: android.view.Window, key: String, rect: RectF) {
        val r = Rect(rect.left.toInt(), rect.top.toInt(), rect.right.toInt(), rect.bottom.toInt())
        if (r.width() <= 0 || r.height() <= 0) return
        val bmp = android.graphics.Bitmap.createBitmap(r.width(), r.height(), android.graphics.Bitmap.Config.ARGB_8888)
        runCatching {
            android.view.PixelCopy.request(window, r, bmp, { res -> if (res == android.view.PixelCopy.SUCCESS) widgetSnapshots[key] = bmp },
                Handler(Looper.getMainLooper()))
        }
    }

    /** Set by MainActivity: Animation Module active + card/display radius (px). */
    @Volatile var enabled = false
    @Volatile var cardCornerPx = 0f
    @Volatile var displayCornerPx = 0f

    /** Icon/widget an app is growing out of right now — hidden in the launcher (iOS: the icon BECOMES the card). */
    val launchingKey = androidx.compose.runtime.mutableStateOf<String?>(null)

    /** For any start rect (icon, widget): ActivityOptions with our animation or null (then the system animation). */
    fun optionsFor(rect: RectF, startCornerPx: Float, key: String? = null): ActivityOptions? {
        if (!enabled || rect.width() <= 0f) return null
        val o = options(rect, startCornerPx, cardCornerPx, null, displayCornerPx) ?: return null
        if (key != null) {
            launchingKey.value = key
            // Safety net in case the animation never starts (the app doesn't launch)
            Handler(Looper.getMainLooper()).postDelayed({ if (launchingKey.value == key) launchingKey.value = null }, LAUNCH_HIDE_MAX_MS)
        }
        return o
    }

    /** ActivityOptions with our animation, or null (no permission / interface missing). */
    fun options(iconRect: RectF, iconCornerPx: Float, screenCornerPx: Float, icon: android.graphics.Bitmap? = null, displayCornerPx: Float = 0f): ActivityOptions? = runCatching {
        displayCorner = displayCornerPx
        val remote = object : IRemoteTransition.Stub() {
            override fun startAnimation(token: IBinder, info: TransitionInfo, t: SurfaceControl.Transaction, finish: IRemoteTransitionFinishedCallback) {
                Handler(Looper.getMainLooper()).post { animate(info, t, finish, iconRect, iconCornerPx, screenCornerPx) }
            }
            override fun mergeAnimation(transition: IBinder, info: TransitionInfo, t: SurfaceControl.Transaction, mergeTarget: IBinder, finish: IRemoteTransitionFinishedCallback) = Unit
            override fun takeOverAnimation(transition: IBinder, info: TransitionInfo, t: SurfaceControl.Transaction, finish: IRemoteTransitionFinishedCallback, states: Array<WindowAnimationState>) = Unit
            override fun onTransitionConsumed(transition: IBinder, aborted: Boolean) = Unit
        }
        val rtClass = Class.forName("android.window.RemoteTransition")
        val rt = rtClass.getConstructor(IRemoteTransition::class.java, String::class.java).newInstance(remote, "IosHomeAppLaunch")
        ActivityOptions::class.java.getMethod("makeRemoteTransition", rtClass).invoke(null, rt) as ActivityOptions
    }.onFailure { Log.w(TAG, "Remote transition not available", it) }.getOrNull()

    private class Target(val leash: SurfaceControl, val bounds: Rect)

    private fun animate(
        info: TransitionInfo, start: SurfaceControl.Transaction, finish: IRemoteTransitionFinishedCallback,
        icon: RectF, iconCorner: Float, screenCorner: Float,
    ) {
        val opening = mutableListOf<Target>()
        try {
            @Suppress("UNCHECKED_CAST")
            val changes = info.javaClass.getMethod("getChanges").invoke(info) as List<Any>
            for (c in changes) {
                val mode = c.javaClass.getMethod("getMode").invoke(c) as Int
                val flags = c.javaClass.getMethod("getFlags").invoke(c) as Int
                if ((flags and FLAG_IS_WALLPAPER) != 0) continue
                if (mode == TRANSIT_OPEN || mode == TRANSIT_TO_FRONT) {
                    val leash = c.javaClass.getMethod("getLeash").invoke(c) as SurfaceControl
                    val b = c.javaClass.getMethod("getEndAbsBounds").invoke(c) as Rect
                    opening += Target(leash, Rect(b))
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "TransitionInfo not readable", e)
        }
        // Start state: window already in the icon before it becomes visible
        opening.forEach { apply(start, it, icon, iconCorner, screenCorner, 0f, 0f, 0f, 0f) }
        start.apply()
        if (opening.isEmpty()) { launchingKey.value = null; done(finish); return }

        // Spring like iOS (response/damping from Motion), analytical — no Compose needed
        val springs = listOf(Motion.appLaunch, Motion.appLaunchY, Motion.appLaunchH)
        // Respect the developer option "Animator duration scale" (0 = off, 10 = slow motion)
        val slow = android.animation.ValueAnimator.getDurationScale().toDouble().let { if (it <= 0.0) 0.001 else it }
        val response = springs.maxOf { it.response.value } * slow
        val startNs = System.nanoTime()
        val choreographer = Choreographer.getInstance()
        val frame = object : Choreographer.FrameCallback {
            override fun doFrame(now: Long) {
                val tt = (now - startNs) / 1e9 / slow
                val pr = springs.map { sp -> spring(tt, 2 * Math.PI / sp.response.value, sp.dampingFraction.value.toDouble()).toFloat() }
                val t = SurfaceControl.Transaction()
                opening.forEach { apply(t, it, icon, iconCorner, screenCorner, pr[0], pr[1], pr[2], (tt / ALPHA_IN_S).toFloat()) }
                t.apply()
                if (tt * slow < response * SETTLE_RESPONSES) choreographer.postFrameCallback(this)
                else {
                    val end = SurfaceControl.Transaction()
                    opening.forEach { apply(end, it, icon, iconCorner, screenCorner, 1f, 1f, 1f, 1f) } // full screen: radius = display (no jump)
                    end.apply()
                    launchingKey.value = null // the app now covers everything → icon invisibly back in its place
                    done(finish)
                }
            }
        }
        choreographer.postFrameCallback(frame)
    }

    /** Progress 0 → 1 of a spring without initial velocity (under-/critically damped). */
    private fun spring(t: Double, w0: Double, zeta: Double): Double = if (zeta < 1.0) {
        val wd = w0 * sqrt(1 - zeta * zeta)
        1 - exp(-zeta * w0 * t) * (Math.cos(wd * t) + zeta * w0 / wd * Math.sin(wd * t))
    } else 1 - exp(-w0 * t) * (1 + w0 * t)

    /**
     * Card between icon and full screen like iOS (recording9): [px] width + horizontal centre, [py] vertical centre,
     * [ph] height — each its own spring. Content: scaled to the card width, anchored at the top, cut off at the bottom.
     */
    private fun apply(t: SurfaceControl.Transaction, target: Target, icon: RectF, iconCorner: Float, screenCorner: Float,
                      px: Float, py: Float, ph: Float, alpha: Float) {
        val b = target.bounds
        val w = b.width().toFloat(); val h = b.height().toFloat()
        val cw = lerp(icon.width(), w, px)
        val ch = lerp(icon.height(), h, ph)
        val cx = lerp(icon.centerX(), b.exactCenterX(), px)
        val cy = lerp(icon.centerY(), b.exactCenterY(), py)
        val scale = cw / w
        val cropH = (ch / scale).coerceIn(1f, h)
        val crop = Rect(0, 0, w.toInt(), cropH.toInt())
        // like Launcher3: setWindowCrop (internal) — the corner radius refers to this crop
        windowCrop?.let { runCatching { it.invoke(t, target.leash, crop) }.getOrNull() } ?: t.setCrop(target.leash, crop)
        t.setScale(target.leash, scale, scale)
        t.setPosition(target.leash, cx - cw / 2f, cy - ch / 2f)
        // Corners (recording9): quickly from the icon radius to the card radius (62 pt), at the end smoothly to the real
        // display radius of this phone — so the card ends exactly in the screen shape, without a jump to square.
        val toCard = lerp(iconCorner, screenCorner, (px * CORNER_SPEED).coerceAtMost(1f))
        val e = ((minOf(px, ph) - CORNER_SETTLE_FROM) / (1f - CORNER_SETTLE_FROM)).coerceIn(0f, 1f)
        val corner = lerp(toCard, displayCorner, e * e * (3f - 2f * e))
        runCatching { cornerRadius?.invoke(t, target.leash, corner / scale) } // internal (hidden API allowlist)
            .onFailure { if (!loggedCorner) { loggedCorner = true; Log.w(TAG, "Corner radius", it) } }
        if (cornerRadius == null && !loggedCorner) { loggedCorner = true; Log.w(TAG, "setCornerRadius not found") }
        t.setAlpha(target.leash, alpha.coerceIn(0f, 1f))
        t.setVisibility(target.leash, true)
    }

    private val windowCrop = runCatching {
        SurfaceControl.Transaction::class.java.getMethod("setWindowCrop", SurfaceControl::class.java, Rect::class.java)
    }.getOrNull()
    private var loggedCorner = false
    private val cornerRadius = runCatching {
        SurfaceControl.Transaction::class.java.getMethod("setCornerRadius", SurfaceControl::class.java, Float::class.javaPrimitiveType)
    }.getOrNull()

    private fun done(finish: IRemoteTransitionFinishedCallback) {
        runCatching { finish.onTransitionFinished(null, null) }.onFailure { Log.w(TAG, "finish", it) }
    }

    private const val SETTLE_RESPONSES = 1.4 // the spring is practically at rest after this
    private const val ALPHA_IN_S = 0.035 // the card is opaque practically immediately (recording9: app visible from the 1st frame)
    private const val CORNER_SPEED = 2f
    private const val LAUNCH_HIDE_MAX_MS = 3000L
    private const val CORNER_SETTLE_FROM = 0.6f
    /** Real display corner radius (px) of the device; 0 for a square display. */
    @Volatile private var displayCorner = 0f
    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
}
