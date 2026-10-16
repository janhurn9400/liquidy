package io.github.janhurn9400.liquidy.ui.widgets

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.SizeF
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import io.github.janhurn9400.liquidy.R
import io.github.janhurn9400.liquidy.data.WidgetSpec

/**
 * Native Android widgets via AppWidgetHost (like Launcher3): list of providers, binding (with a system dialog if needed),
 * configuration activity, display as a View in the iOS widget frame (the launcher decides size/corners, the app the inside).
 */
class AndroidWidgetHost(private val context: Context) {
    val manager: AppWidgetManager = AppWidgetManager.getInstance(context)
    val host = AppWidgetHost(context, HOST_ID)

    fun providers(): List<AppWidgetProviderInfo> = manager.installedProviders.sortedBy { it.loadLabel(context.packageManager).lowercase() }

    fun info(id: Int): AppWidgetProviderInfo? = manager.getAppWidgetInfo(id)

    /** iOS size from the provider's minimum dimensions/target cells: small 2×2, medium 4×2, large 4×4. */
    fun iosSize(p: AppWidgetProviderInfo): Pair<Int, Int> {
        val d = context.resources.displayMetrics.density
        val wide = if (p.targetCellWidth > 0) p.targetCellWidth > 2 else p.minWidth / d > SMALL_MAX_DP
        val tall = if (p.targetCellHeight > 0) p.targetCellHeight > 2 else p.minHeight / d > SMALL_MAX_DP
        return when {
            tall && wide -> 4 to 4
            wide -> 4 to 2
            tall -> 4 to 4 // iOS has no tall narrow widget → large
            else -> 2 to 2
        }
    }

    /**
     * All iOS sizes (small 2×2, medium 4×2, large 4×4) the widget fits into — like iOS offers several sizes per widget.
     * Measure: the smallest size the provider may shrink to (minResize…, otherwise min…), and the largest
     * (maxResize…, for a non-resizable axis not much larger than the minimum). Slightly too large is allowed —
     * [AndroidWidget] then shrinks uniformly down to [MIN_FIT_SCALE].
     */
    fun iosSizes(p: AppWidgetProviderInfo): List<Pair<Int, Int>> {
        val d = context.resources.displayMetrics.density
        val rh = p.resizeMode and AppWidgetProviderInfo.RESIZE_HORIZONTAL != 0
        val rv = p.resizeMode and AppWidgetProviderInfo.RESIZE_VERTICAL != 0
        val minW = (if (rh && p.minResizeWidth > 0) minOf(p.minResizeWidth, p.minWidth) else p.minWidth) / d
        val minH = (if (rv && p.minResizeHeight > 0) minOf(p.minResizeHeight, p.minHeight) else p.minHeight) / d
        val maxW = if (rh) (if (p.maxResizeWidth > 0) p.maxResizeWidth / d else Float.MAX_VALUE) else p.minWidth / d * NON_RESIZABLE_STRETCH
        val maxH = if (rv) (if (p.maxResizeHeight > 0) p.maxResizeHeight / d else Float.MAX_VALUE) else p.minHeight / d * NON_RESIZABLE_STRETCH
        fun fits(w: Float, h: Float) = minW * MIN_FIT_SCALE <= w && minH * MIN_FIT_SCALE <= h && w <= maxW * 1.15f && h <= maxH * 1.15f
        val all = listOf(Triple(2, 2, SLOT_SMALL to SLOT_SMALL), Triple(4, 2, SLOT_WIDE to SLOT_SMALL), Triple(4, 4, SLOT_WIDE to SLOT_LARGE_H))
        val ok = all.filter { (_, _, wh) -> fits(wh.first, wh.second) }.map { it.first to it.second }
        return ok.ifEmpty { listOf(iosSize(p)) }
    }

    // ── Adding: id → bind (dialog) → configure → done ───────────────────────────
    private var pending: Triple<Int, AppWidgetProviderInfo, (WidgetSpec?) -> Unit>? = null
    /** Chosen iOS size, belongs to the appWidgetId in [pending] (not stored separately → no mix-up). */
    private var pendingSize: Pair<Int, Pair<Int, Int>?>? = null

    fun add(activity: Activity, p: AppWidgetProviderInfo, size: Pair<Int, Int>? = null, done: (WidgetSpec?) -> Unit) {
        val id = host.allocateAppWidgetId()
        pendingSize = id to size
        pending = Triple(id, p, done)
        if (manager.bindAppWidgetIdIfAllowed(id, p.profile, p.provider, null)) configure(activity)
        else activity.startActivityForResult(
            Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, p.provider)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, p.profile),
            REQ_BIND,
        )
    }

    private fun configure(activity: Activity) {
        val (id, p, _) = pending ?: return
        if (p.configure != null && (p.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL) == 0) {
            runCatching { host.startAppWidgetConfigureActivityForResult(activity, id, 0, REQ_CONFIGURE, null) }
                .onFailure { finish(true) }
        } else finish(true)
    }

    private fun finish(ok: Boolean) {
        val (id, p, done) = pending ?: return
        pending = null
        if (!ok) { host.deleteAppWidgetId(id); done(null); return }
        val (c, r) = pendingSize?.takeIf { it.first == id }?.second ?: iosSize(p)
        pendingSize = null
        done(WidgetSpec("android", c, r, id, p.provider.flattenToString()))
    }

    /** Passed on from Activity.onActivityResult. */
    fun onActivityResult(activity: Activity, requestCode: Int, resultCode: Int) {
        when (requestCode) {
            REQ_BIND -> if (resultCode == Activity.RESULT_OK) configure(activity) else finish(false)
            REQ_CONFIGURE -> finish(resultCode == Activity.RESULT_OK)
        }
    }

    fun delete(id: Int) = host.deleteAppWidgetId(id)

    /**
     * With the Animation Module: intercept clicks in Android widgets (AppWidgetHost.setInteractionHandler, internal — like Launcher3)
     * and start the PendingIntent with our open animation, start = widget rect. Without the module: system default.
     */
    fun applyLaunchAnimation() {
        if (!io.github.janhurn9400.liquidy.data.AppLaunchTransition.enabled || interactionSet) return
        runCatching {
            val handlerClass = Class.forName("android.widget.RemoteViews\$InteractionHandler")
            val handler = java.lang.reflect.Proxy.newProxyInstance(handlerClass.classLoader, arrayOf(handlerClass)) { _, method, args ->
                if (method.name != "onInteraction") return@newProxyInstance false
                val view = args[0] as android.view.View
                val pi = args[1] as android.app.PendingIntent
                val response = args[2]
                // the whole widget as the start rect (not just the tapped element)
                var host: android.view.View = view
                while (host !is android.appwidget.AppWidgetHostView && host.parent is android.view.View) host = host.parent as android.view.View
                val loc = IntArray(2).also { host.getLocationOnScreen(it) }
                val rect = android.graphics.RectF(loc[0].toFloat(), loc[1].toFloat(), (loc[0] + host.width).toFloat(), (loc[1] + host.height).toFloat())
                val fill = runCatching {
                    @Suppress("UNCHECKED_CAST")
                    (response.javaClass.getMethod("getLaunchOptions", android.view.View::class.java).invoke(response, view) as android.util.Pair<Intent, *>).first
                }.getOrNull()
                (host as? android.appwidget.AppWidgetHostView)?.appWidgetId?.let { id -> widgetKeyForId(id)?.let { k ->
                    pi.creatorPackage?.let { io.github.janhurn9400.liquidy.data.AppLaunchTransition.widgetOrigins[it] = k } } }
                val opts = io.github.janhurn9400.liquidy.data.AppLaunchTransition.optionsFor(rect,
                    io.github.janhurn9400.liquidy.design.DesignTokens.widgetCorner.value * io.github.janhurn9400.liquidy.design.UiScale.density(context))
                    ?: return@newProxyInstance false // let the system handle it normally
                runCatching {
                    opts.setPendingIntentBackgroundActivityStartMode(android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                    pi.send(context, 0, fill, null, null, null, opts.toBundle()); true
                }.getOrDefault(false)
            }
            AppWidgetHost::class.java.getMethod("setInteractionHandler", handlerClass).invoke(host, handler)
            interactionSet = true
        }.onFailure { android.util.Log.w("AndroidWidgetHost", "Click interception not available", it) }
    }

    private var interactionSet = false
    /** Set by MainActivity: appWidgetId → widget key in the layout. */
    var widgetKeyForId: (Int) -> String? = { null }

    companion object {
        const val HOST_ID = 1024
        const val REQ_BIND = 7101
        const val REQ_CONFIGURE = 7102
        private const val SMALL_MAX_DP = 180f
        /** Approximate iOS widget areas in dp (small, medium/large wide, large tall) — only for the size selection. */
        private const val SLOT_SMALL = 158f
        private const val SLOT_WIDE = 338f
        private const val SLOT_LARGE_H = 354f
        /** A too-large widget isn't shrunk below 72 % (unreadable otherwise) → don't offer the size. */
        const val MIN_FIT_SCALE = 0.72f
        /** Non-resizable axis: offer at most this much larger than the minimum (the rest would stay empty). */
        private const val NON_RESIZABLE_STRETCH = 1.6f
    }
}

val LocalAndroidWidgetHost = staticCompositionLocalOf<AndroidWidgetHost?> { null }

/**
 * Android widget in the iOS frame. The provider is told the real area (AppWidgetHostView.updateAppWidgetSize:
 * sets OPTION_APPWIDGET_SIZES and the old MIN/MAX values → responsive layouts choose accordingly). If the area is smaller
 * than the provider's minimum, it's laid out at minimum size and shrunk uniformly to the area
 * (at most down to [AndroidWidgetHost.MIN_FIT_SCALE]) instead of being clipped.
 */
@Composable
fun AndroidWidget(spec: WidgetSpec, modifier: Modifier) {
    val host = LocalAndroidWidgetHost.current
    val info = host?.info(spec.appWidgetId)
    if (host == null || info == null) {
        Box(modifier.background(Color(0xFF1C1C1E)))
        return
    }
    val density = LocalDensity.current.density
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val slotW = constraints.maxWidth / density
        val slotH = constraints.maxHeight / density
        val rh = info.resizeMode and AppWidgetProviderInfo.RESIZE_HORIZONTAL != 0
        val rv = info.resizeMode and AppWidgetProviderInfo.RESIZE_VERTICAL != 0
        val needW = (if (rh && info.minResizeWidth > 0) minOf(info.minResizeWidth, info.minWidth) else info.minWidth) / density
        val needH = (if (rv && info.minResizeHeight > 0) minOf(info.minResizeHeight, info.minHeight) else info.minHeight) / density
        val scale = minOf(1f, slotW / needW.coerceAtLeast(1f), slotH / needH.coerceAtLeast(1f)).coerceAtLeast(AndroidWidgetHost.MIN_FIT_SCALE)
        val layoutW = slotW / scale
        val layoutH = slotH / scale
        // Many widgets leave a margin around their area (android:id/background, Android 12 convention) for launcher cell gaps
        // — iOS widgets fill the frame. So measure the area and scale it up to the frame + centre it.
        var fit by remember { mutableStateOf(Triple(1f, 0f, 0f)) }
        var view by remember { mutableStateOf<android.appwidget.AppWidgetHostView?>(null) }
        AndroidView(
            factory = { ctx ->
                host.host.createView(ctx, spec.appWidgetId, info).also { view = it }.apply {
                    setPadding(0, 0, 0, 0)
                    viewTreeObserver.addOnGlobalLayoutListener {
                        val bg = findViewById<android.view.View>(android.R.id.background)
                        fit = if (bg == null || bg === this || width == 0 || bg.width == 0 || drawsOutside(this, bg)) Triple(1f, 0f, 0f) else {
                            val r = android.graphics.Rect(0, 0, bg.width, bg.height)
                            runCatching { offsetDescendantRectToMyCoords(bg, r) }
                            val k = minOf(width / r.width().toFloat(), height / r.height().toFloat()).coerceIn(1f, MAX_FILL_SCALE)
                            Triple(k, width / 2f - r.exactCenterX(), height / 2f - r.exactCenterY())
                        }
                    }
                }
            },
            update = { v ->
                val size = SizeF(layoutW, layoutH)
                if (v.tag != size) {
                    v.tag = size
                    runCatching { v.updateAppWidgetSize(Bundle(), listOf(size)) }
                }
            },
            modifier = Modifier.requiredSize(layoutW.dp, layoutH.dp).graphicsLayer {
                val k = scale * fit.first
                scaleX = k; scaleY = k
                translationX = k * fit.second; translationY = k * fit.third
            }.onGloballyPositioned { c -> view?.let { AndroidWidgetTaps.register(spec.appWidgetId, it, c) } },
        )
    }
}

/**
 * The home screen's gesture layer lies above all widgets and intercepts every touch — Android widgets would
 * otherwise never get a tap. The home screen reports taps here; we convert the window position through all layers
 * (scale, offset) into widget coordinates and inject DOWN/UP into the AppWidgetHostView.
 */
object AndroidWidgetTaps {
    private val views = HashMap<Int, Pair<java.lang.ref.WeakReference<android.view.View>, androidx.compose.ui.layout.LayoutCoordinates>>()

    fun register(id: Int, v: android.view.View, c: androidx.compose.ui.layout.LayoutCoordinates) { views[id] = java.lang.ref.WeakReference(v) to c }

    /** @return true if a widget view got the tap. */
    fun tap(id: Int, window: androidx.compose.ui.geometry.Offset): Boolean {
        val (ref, c) = views[id] ?: return false
        val v = ref.get() ?: return false
        if (!c.isAttached || !v.isAttachedToWindow) return false
        val p = runCatching { c.windowToLocal(window) }.getOrNull() ?: return false
        if (p.x < 0 || p.y < 0 || p.x > v.width || p.y > v.height) return false
        val t = android.os.SystemClock.uptimeMillis()
        val down = android.view.MotionEvent.obtain(t, t, android.view.MotionEvent.ACTION_DOWN, p.x, p.y, 0)
        val up = android.view.MotionEvent.obtain(t, t + 50, android.view.MotionEvent.ACTION_UP, p.x, p.y, 0)
        v.dispatchTouchEvent(down); v.dispatchTouchEvent(up)
        down.recycle(); up.recycle()
        return true
    }

    // ── Forwarding an ongoing touch (scrolling lists, swiping stacks, buttons with pressed state) ────────────
    private var active: android.view.View? = null
    private var activeCoords: androidx.compose.ui.layout.LayoutCoordinates? = null
    private var downTime = 0L
    private var downLocal = androidx.compose.ui.geometry.Offset.Zero

    private fun send(action: Int, window: androidx.compose.ui.geometry.Offset) {
        val v = active ?: return
        val c = activeCoords?.takeIf { it.isAttached } ?: return
        val p = runCatching { c.windowToLocal(window) }.getOrNull() ?: return
        val e = android.view.MotionEvent.obtain(downTime, android.os.SystemClock.uptimeMillis(), action, p.x, p.y, 0)
        v.dispatchTouchEvent(e); e.recycle()
    }

    /** Finger touches down on the widget: send DOWN (pressed state in the widget). @return false if there's no widget there. */
    fun begin(id: Int, window: androidx.compose.ui.geometry.Offset): Boolean {
        cancel()
        val (ref, c) = views[id] ?: return false
        val v = ref.get() ?: return false
        if (!c.isAttached || !v.isAttachedToWindow) return false
        val p = runCatching { c.windowToLocal(window) }.getOrNull() ?: return false
        if (p.x < 0 || p.y < 0 || p.x > v.width || p.y > v.height) return false
        active = v; activeCoords = c; downTime = android.os.SystemClock.uptimeMillis(); downLocal = p
        send(android.view.MotionEvent.ACTION_DOWN, window)
        return true
    }

    val isActive get() = active != null
    fun move(window: androidx.compose.ui.geometry.Offset) = send(android.view.MotionEvent.ACTION_MOVE, window)
    fun end(window: androidx.compose.ui.geometry.Offset) { send(android.view.MotionEvent.ACTION_UP, window); active = null; activeCoords = null }
    /** Launcher takes over (paging, menu, dragging): the widget gets CANCEL, no click. */
    fun cancel() {
        if (active == null) return
        val v = active!!
        val e = android.view.MotionEvent.obtain(downTime, android.os.SystemClock.uptimeMillis(), android.view.MotionEvent.ACTION_CANCEL, downLocal.x, downLocal.y, 0)
        v.dispatchTouchEvent(e); e.recycle()
        active = null; activeCoords = null
    }

    /**
     * Can the widget scroll/swipe by itself at the touch point in the finger direction ([dx], [dy] px)? Then the gesture
     * belongs to it (like Launcher3: scrollable widget content wins), otherwise the home screen pages or opens search.
     */
    fun canHandle(dx: Float, dy: Float): Boolean {
        val root = active ?: return false
        val vertical = kotlin.math.abs(dy) > kotlin.math.abs(dx)
        fun at(v: android.view.View, x: Float, y: Float): Boolean {
            if (v.visibility != android.view.View.VISIBLE) return false
            if (v is android.widget.StackView || v is android.widget.AdapterViewFlipper) return true // swipeable in both directions
            if (vertical && v.canScrollVertically(if (dy < 0) 1 else -1)) return true
            if (!vertical && v.canScrollHorizontally(if (dx < 0) 1 else -1)) return true
            if (v is android.view.ViewGroup) for (i in v.childCount - 1 downTo 0) {
                val ch = v.getChildAt(i)
                val lx = x + v.scrollX - ch.left - ch.translationX; val ly = y + v.scrollY - ch.top - ch.translationY
                if (lx >= 0 && ly >= 0 && lx < ch.width && ly < ch.height && at(ch, lx, ly)) return true
            }
            return false
        }
        return at(root, downLocal.x, downLocal.y)
    }
}

/**
 * Does anything other than the area [bg] (and its parents) draw a visible view? Then [bg] isn't the
 * visible widget area (e.g. analogue clock: invisible inner frame, dial next to it over the full height) —
 * scaling up would clip.
 */
private fun drawsOutside(root: android.view.View, bg: android.view.View): Boolean {
    val ancestors = HashSet<android.view.View>()
    var p = bg.parent
    while (p is android.view.View) { ancestors += p; if (p === root) break; p = p.parent }
    fun walk(v: android.view.View): Boolean {
        if (v === bg) return false
        if (v in ancestors) return v is android.view.ViewGroup && (0 until v.childCount).any { walk(v.getChildAt(it)) }
        return v.visibility == android.view.View.VISIBLE && v.width > 0 && v.height > 0
    }
    return walk(root)
}

/** The widget area is scaled up at most this far to compensate for the launcher margin. */
private const val MAX_FILL_SCALE = 1.3f

/** Label below the widget (iOS: app/widget name). */
@Composable
fun widgetLabel(spec: WidgetSpec): String {
    val context = LocalContext.current
    return when (spec.type) {
        IosWidgetTypes.CLOCK -> stringResource(R.string.widget_clock)
        IosWidgetTypes.CALENDAR -> stringResource(R.string.widget_calendar)
        IosWidgetTypes.BATTERY -> stringResource(R.string.widget_batteries)
        IosWidgetTypes.WEATHER -> stringResource(R.string.widget_weather)
        IosWidgetTypes.PHOTOS -> stringResource(R.string.widget_photos)
        IosWidgetTypes.REMINDERS -> stringResource(R.string.widget_reminders)
        else -> spec.provider?.let { ComponentName.unflattenFromString(it) }?.let { cn ->
            runCatching { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(cn.packageName, 0)).toString() }.getOrNull()
        } ?: ""
    }
}
