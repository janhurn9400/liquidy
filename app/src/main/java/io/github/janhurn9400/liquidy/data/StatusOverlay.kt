package io.github.janhurn9400.liquidy.data

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import io.github.janhurn9400.liquidy.design.StatusOverlayLayout as L
import io.github.janhurn9400.liquidy.ui.home.DuoStatusSymbols
import kotlin.math.roundToInt
import io.github.janhurn9400.liquidy.ui.control.ControlCenter
import io.github.janhurn9400.liquidy.ui.control.ControlCenterModel
import kotlinx.coroutines.flow.first

/**
 * Duo style: the status symbols (clock + ring) float over other apps at the same spot as on the home screen, so the
 * look continues outside the launcher. Drawn by the optional accessibility service ([PowerButtonService]) as an
 * accessibility overlay: the app below stays full screen and is not moved (Android can only inset whole screen edges —
 * see docs/RESEARCH.md "Status symbols over apps"). The service learns which app is in front (package name only, to hide
 * the symbols over the launcher, the keyboard and System UI) and takes one screenshot per app switch, only to measure how
 * bright the app is behind the symbols (black on light apps, white on dark ones). The image stays in memory, is reduced to
 * one number and released at once; nothing is stored or sent. No periodic captures.
 */
object StatusOverlay {
    private const val PREFS = "status_overlay"
    private const val UNKNOWN_APP = "?"

    /** User setting (Settings › Appearance › Status Bar, Duo only). */
    var enabled by mutableStateOf(true)
        private set

    fun load(c: Context) { enabled = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("on", true) }
    fun set(c: Context, on: Boolean) {
        enabled = on
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("on", on).apply()
        host?.let { update(it); androidIcons(active()) }
    }

    private var host: AccessibilityService? = null
    private var view: ComposeView? = null
    private var owner: OverlayOwner? = null
    private var foreground: String? = null
    private var color by mutableStateOf(Color.White)
    private val main = Handler(Looper.getMainLooper())
    // one screenshot per app switch, only for the symbol colour — no periodic captures (privacy, battery)
    private val sampler = Runnable { sample() }

    fun attach(s: AccessibilityService) {
        host = s
        load(s); FoldableStyle.load(s); OwnStatusBar.load(s); ShizukuShell.init(s)
        // started (boot, update) while something is open: no window event comes until the next switch — assume an app
        // is in front unless our home activity says otherwise (homeVisible)
        if (foreground == null) foreground = UNKNOWN_APP
        update(s)
    }

    fun detach() {
        hide(); host = null
        androidIcons(false)
    }

    private fun active() = enabled && FoldableStyle.duoStyle && OwnStatusBar.enabled

    private var iconsHidden: Boolean? = null

    /**
     * Android's own status icons (clock, notifications, Wi-Fi, battery) would sit right next to ours: hide them all with
     * StatusBarManager disable flags via `cmd statusbar` — allowed for the shell user, so root or Shizuku (tested in the
     * emulator; an icon_blacklist or tint overlay left the battery / did nothing). Without either they stay.
     */
    private fun androidIcons(hide: Boolean) {
        if (iconsHidden == hide) return
        // remembered only once it worked: right after boot the Shizuku binder / root probe may not be there yet
        Thread {
            val ok = Privileged.run("cmd statusbar send-disable-flag " + if (hide) "clock notification-icons system-icons" else "none")
            if (ok) main.post { iconsHidden = hide }
        }.start()
    }

    private var home = false

    /** MainActivity resumed / paused: the launcher draws its own symbols (more reliable than window events — Liquidy's
     *  own app launch transition also reports window changes under its package). */
    fun homeVisible(on: Boolean) {
        home = on
        host?.let { update(it) }
    }

    /** From TYPE_WINDOW_STATE_CHANGED: the package now in front. */
    fun onForeground(pkg: String?) {
        val s = host ?: return
        if (pkg.isNullOrEmpty() || pkg == foreground) return
        // the keyboard reports its own window, our own windows are decided by homeVisible: keep the app below
        // System UI (shade, volume dialog) neither: closing them sends no event for the app below, so the symbols would stay gone
        if (pkg == defaultIme(s) || pkg == s.packageName || pkg == "com.android.systemui") return
        foreground = pkg
        update(s)
    }

    private fun update(s: AccessibilityService) {
        androidIcons(active())
        val pkg = foreground
        val show = active() && !home && pkg != null && pkg != "android"
        if (show) { show(s); main.removeCallbacks(sampler); main.postDelayed(sampler, L.SAMPLE_DELAY_MS) } else hide()
    }

    private fun show(s: AccessibilityService) {
        if (view != null) return
        val wm = s.getSystemService(WindowManager::class.java)
        val d = s.resources.displayMetrics.density
        val o = OverlayOwner().also { owner = it; it.start() }
        val v = ComposeView(s).apply {
            setViewTreeLifecycleOwner(o); setViewTreeSavedStateRegistryOwner(o)
            setContent {
                val info by rememberStatusInfo()
                Box(Modifier.fillMaxSize().graphicsLayer {
                    scaleX = L.SCALE; scaleY = L.SCALE
                    transformOrigin = TransformOrigin((L.WINDOW_W - L.COLUMN_FROM_RIGHT) / L.WINDOW_W, 0f)
                    translationY = L.SHIFT_Y * d
                }) {
                    DuoStatusSymbols(info, (L.WINDOW_W - L.COLUMN_FROM_RIGHT) * d, d, color)
                }
            }
        }
        val lp = WindowManager.LayoutParams(
            (L.WINDOW_W * d).roundToInt(), (L.WINDOW_H * d).roundToInt(),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            title = "Liquidy status symbols"
        }
        runCatching { wm.addView(v, lp); view = v }.onFailure { android.util.Log.w("StatusOverlay", "addView", it); owner?.stop(); owner = null }
        showTouch(s)
    }

    private fun hide() {
        main.removeCallbacks(sampler)
        val wm = host?.getSystemService(WindowManager::class.java)
        touch?.let { t -> runCatching { wm?.removeView(t) } }; touch = null
        val v = view ?: return
        runCatching { wm?.removeView(v) }
        owner?.stop(); owner = null; view = null
    }

    // ── Control Center over apps (prototype) ─────────────────────────────────────────────────────────────
    // A tap on the symbols takes ONE screenshot (started at touch-down, so it is usually there before the panel shows)
    // and opens the Control Center in a full-screen overlay; the screenshot is its backdrop (blurred like on the home
    // screen) and is released as soon as the Control Center has closed. Live refraction of the app is impossible for
    // an app (see docs/RESEARCH.md "Status symbols over apps").
    private var touch: android.view.View? = null
    private var ccView: ComposeView? = null
    private var ccOwner: OverlayOwner? = null
    private var ccModel: ControlCenterModel? = null
    private var shot by mutableStateOf<ImageBitmap?>(null)
    private var shotBitmap: Bitmap? = null
    private var downAt = 0L

    private fun showTouch(s: AccessibilityService) {
        if (touch != null) return
        val wm = s.getSystemService(WindowManager::class.java)
        val d = s.resources.displayMetrics.density
        val t = android.view.View(s)
        t.setOnTouchListener { _, ev ->
            when (ev.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> capture(s)
                android.view.MotionEvent.ACTION_UP -> openCc(s)
            }
            true
        }
        val lp = WindowManager.LayoutParams(
            (L.TOUCH_W * d).roundToInt(), (L.TOUCH_H * d).roundToInt(),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = ((L.COLUMN_FROM_RIGHT - L.TOUCH_W / 2) * d).roundToInt()
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            title = "Liquidy status symbols touch"
        }
        runCatching { wm.addView(t, lp); touch = t }
    }

    private fun capture(s: AccessibilityService) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        downAt = android.os.SystemClock.uptimeMillis()
        s.takeScreenshot(Display.DEFAULT_DISPLAY, s.mainExecutor, object : AccessibilityService.TakeScreenshotCallback {
            override fun onSuccess(r: AccessibilityService.ScreenshotResult) {
                val b = Bitmap.wrapHardwareBuffer(r.hardwareBuffer, r.colorSpace); r.hardwareBuffer.close()
                b ?: return
                shotBitmap?.recycle(); shotBitmap = b; shot = b.asImageBitmap()
                android.util.Log.d("CcOverlay", "screenshot after ${android.os.SystemClock.uptimeMillis() - downAt} ms, " +
                    "panel progress ${ccModel?.progress?.value ?: 0f}")
            }
            override fun onFailure(errorCode: Int) { android.util.Log.d("CcOverlay", "screenshot failed $errorCode") }
        })
    }

    private fun openCc(s: AccessibilityService) {
        if (ccView != null) return
        val wm = s.getSystemService(WindowManager::class.java)
        val bounds = wm.currentWindowMetrics.bounds
        val d = s.resources.displayMetrics.density
        val w = bounds.width().toFloat(); val h = bounds.height().toFloat()
        val o = OverlayOwner().also { ccOwner = it; it.start() }
        val v = ComposeView(s).apply {
            setViewTreeLifecycleOwner(o); setViewTreeSavedStateRegistryOwner(o)
            setContent {
                // the model's springs need the composition's frame clock
                val scope = androidx.compose.runtime.rememberCoroutineScope()
                val m = androidx.compose.runtime.remember { ControlCenterModel(scope).also { it.duo = true; ccModel = it } }
                val b = shot
                ControlCenter(m, d, w, h, duo = true, unfolded = w > h, statusColumnX = w - L.COLUMN_FROM_RIGHT * d,
                    drawBackdrop = b?.let { img -> { drawImage(img) } }, statusBase = color)
                androidx.compose.runtime.LaunchedEffect(m) {
                    m.show()
                    androidx.compose.runtime.snapshotFlow { m.visible }.first { !it }
                    main.post { closeCc() }
                }
            }
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            title = "Liquidy Control Center"
        }
        runCatching { wm.addView(v, lp); ccView = v }.onFailure { o.stop(); ccOwner = null; return }
        // the Control Center draws its own status column: hide the floating one meanwhile
        view?.visibility = android.view.View.INVISIBLE
    }

    private fun closeCc() {
        val v = ccView ?: return
        runCatching { host?.getSystemService(WindowManager::class.java)?.removeView(v) }
        ccOwner?.stop(); ccOwner = null; ccView = null; ccModel = null
        shot = null; shotBitmap?.recycle(); shotBitmap = null // the screenshot lives only while the panel is open
        view?.visibility = android.view.View.VISIBLE
    }

    /** Brightness of the app behind the symbols → black or white symbols (like iOS over light / dark apps). */
    private fun sample() {
        val s = host ?: return
        if (view == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        s.takeScreenshot(Display.DEFAULT_DISPLAY, s.mainExecutor, object : AccessibilityService.TakeScreenshotCallback {
            override fun onSuccess(shot: AccessibilityService.ScreenshotResult) {
                val hw = Bitmap.wrapHardwareBuffer(shot.hardwareBuffer, shot.colorSpace)
                shot.hardwareBuffer.close()
                val bmp = hw?.copy(Bitmap.Config.ARGB_8888, false); hw?.recycle()
                bmp ?: return
                val d = s.resources.displayMetrics.density
                val w = (L.WINDOW_W * d).roundToInt().coerceAtMost(bmp.width)
                val h = (L.WINDOW_H * d).roundToInt().coerceAtMost(bmp.height)
                var sum = 0.0; var n = 0
                // our own symbols are in the shot too: sample a coarse grid, the thin strokes barely move the mean
                for (y in 0 until h step 6) for (x in bmp.width - w until bmp.width step 6) {
                    val p = bmp.getPixel(x, y)
                    sum += (0.2126 * ((p shr 16) and 255) + 0.7152 * ((p shr 8) and 255) + 0.0722 * (p and 255)) / 255.0; n++
                }
                bmp.recycle()
                if (n > 0) color = if (sum / n > L.LIGHT_BACKDROP) Color.Black else Color.White
            }
            override fun onFailure(errorCode: Int) {}
        })
    }

    private fun defaultIme(c: Context): String? = android.provider.Settings.Secure.getString(c.contentResolver,
        android.provider.Settings.Secure.DEFAULT_INPUT_METHOD)?.substringBefore('/')

    /** Lifecycle for a ComposeView living in a service window (Compose needs a lifecycle + saved state owner). */
    private class OverlayOwner : LifecycleOwner, SavedStateRegistryOwner {
        private val registry = LifecycleRegistry(this)
        private val saved = SavedStateRegistryController.create(this)
        override val lifecycle: Lifecycle get() = registry
        override val savedStateRegistry: SavedStateRegistry get() = saved.savedStateRegistry
        fun start() { saved.performRestore(null); registry.currentState = Lifecycle.State.RESUMED }
        fun stop() { registry.currentState = Lifecycle.State.DESTROYED }
    }
}
