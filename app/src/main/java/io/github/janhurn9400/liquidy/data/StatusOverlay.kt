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

/**
 * Duo style: the status symbols (clock + ring) float over other apps at the same spot as on the home screen, so the
 * look continues outside the launcher. Drawn by the optional accessibility service ([PowerButtonService]) as an
 * accessibility overlay: the app below stays full screen and is not moved (Android can only inset whole screen edges —
 * see docs/RESEARCH.md "Status symbols over apps"). The service learns which app is in front (package name only, to hide
 * the symbols over the launcher, the keyboard and System UI) and takes a screenshot only to measure how bright the app is
 * behind the symbols (black on light apps, white on dark ones); nothing is stored or sent.
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
    private val sampler = object : Runnable {
        override fun run() { sample(); main.postDelayed(this, L.SAMPLE_MS) }
    }

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
    }

    private fun hide() {
        main.removeCallbacks(sampler)
        val v = view ?: return
        runCatching { host?.getSystemService(WindowManager::class.java)?.removeView(v) }
        owner?.stop(); owner = null; view = null
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
