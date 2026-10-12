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
 * Native Android-Widgets über AppWidgetHost (wie Launcher3): Liste der Anbieter, Binden (ggf. mit System-Dialog),
 * Konfigurations-Activity, Anzeige als View im iOS-Widget-Rahmen (Größe/Ecken bestimmt der Launcher, das Innere die App).
 */
class AndroidWidgetHost(private val context: Context) {
    val manager: AppWidgetManager = AppWidgetManager.getInstance(context)
    val host = AppWidgetHost(context, HOST_ID)

    fun providers(): List<AppWidgetProviderInfo> = manager.installedProviders.sortedBy { it.loadLabel(context.packageManager).lowercase() }

    fun info(id: Int): AppWidgetProviderInfo? = manager.getAppWidgetInfo(id)

    /** iOS-Größe aus den Mindestmaßen/Zielzellen des Anbieters: klein 2×2, mittel 4×2, groß 4×4. */
    fun iosSize(p: AppWidgetProviderInfo): Pair<Int, Int> {
        val d = context.resources.displayMetrics.density
        val wide = if (p.targetCellWidth > 0) p.targetCellWidth > 2 else p.minWidth / d > SMALL_MAX_DP
        val tall = if (p.targetCellHeight > 0) p.targetCellHeight > 2 else p.minHeight / d > SMALL_MAX_DP
        return when {
            tall && wide -> 4 to 4
            wide -> 4 to 2
            tall -> 4 to 4 // iOS hat kein hohes Schmal-Widget → groß
            else -> 2 to 2
        }
    }

    /**
     * Alle iOS-Größen (klein 2×2, mittel 4×2, groß 4×4), in die das Widget passt — wie iOS mehrere Größen je Widget
     * anbietet. Maßstab: kleinste Größe, auf die der Anbieter schrumpfen darf (minResize…, sonst min…), und größte
     * (maxResize…, bei nicht skalierbarer Achse nicht viel größer als das Mindestmaß). Etwas zu groß ist erlaubt —
     * [AndroidWidget] verkleinert dann gleichmäßig bis [MIN_FIT_SCALE].
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

    // ── Hinzufügen: id → binden (Dialog) → konfigurieren → fertig ───────────────────────────
    private var pending: Triple<Int, AppWidgetProviderInfo, (WidgetSpec?) -> Unit>? = null
    /** Gewählte iOS-Größe, gehört zur appWidgetId in [pending] (nicht getrennt merken → kein Vertauschen). */
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

    /** Aus Activity.onActivityResult weitergereicht. */
    fun onActivityResult(activity: Activity, requestCode: Int, resultCode: Int) {
        when (requestCode) {
            REQ_BIND -> if (resultCode == Activity.RESULT_OK) configure(activity) else finish(false)
            REQ_CONFIGURE -> finish(resultCode == Activity.RESULT_OK)
        }
    }

    fun delete(id: Int) = host.deleteAppWidgetId(id)

    /**
     * Mit Magisk-Modul: Klicks in Android-Widgets abfangen (AppWidgetHost.setInteractionHandler, intern — wie Launcher3)
     * und den PendingIntent mit unserer Öffnen-Animation starten, Start = Widget-Rahmen. Ohne Modul: System-Standard.
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
                // ganzes Widget als Startrahmen (nicht nur das angetippte Element)
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
                    ?: return@newProxyInstance false // System soll es normal machen
                runCatching {
                    opts.setPendingIntentBackgroundActivityStartMode(android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                    pi.send(context, 0, fill, null, null, null, opts.toBundle()); true
                }.getOrDefault(false)
            }
            AppWidgetHost::class.java.getMethod("setInteractionHandler", handlerClass).invoke(host, handler)
            interactionSet = true
        }.onFailure { android.util.Log.w("AndroidWidgetHost", "Klick-Abfangen nicht verfügbar", it) }
    }

    private var interactionSet = false
    /** Von MainActivity gesetzt: appWidgetId → Widget-Key im Layout. */
    var widgetKeyForId: (Int) -> String? = { null }

    companion object {
        const val HOST_ID = 1024
        const val REQ_BIND = 7101
        const val REQ_CONFIGURE = 7102
        private const val SMALL_MAX_DP = 180f
        /** Ungefähre iOS-Widgetflächen in dp (klein, mittel/groß breit, groß hoch) — nur für die Größenauswahl. */
        private const val SLOT_SMALL = 158f
        private const val SLOT_WIDE = 338f
        private const val SLOT_LARGE_H = 354f
        /** Stärker als auf 72 % wird ein zu großes Widget nicht verkleinert (sonst unlesbar) → Größe nicht anbieten. */
        const val MIN_FIT_SCALE = 0.72f
        /** Nicht skalierbare Achse: höchstens so viel größer als das Mindestmaß anbieten (Rest bliebe leer). */
        private const val NON_RESIZABLE_STRETCH = 1.6f
    }
}

val LocalAndroidWidgetHost = staticCompositionLocalOf<AndroidWidgetHost?> { null }

/**
 * Android-Widget im iOS-Rahmen. Der Anbieter bekommt die echte Fläche gemeldet (AppWidgetHostView.updateAppWidgetSize:
 * setzt OPTION_APPWIDGET_SIZES und die alten MIN/MAX-Werte → responsive Layouts wählen passend). Ist die Fläche kleiner
 * als das Mindestmaß des Anbieters, wird es in Mindestgröße ausgelegt und gleichmäßig auf die Fläche verkleinert
 * (höchstens bis [AndroidWidgetHost.MIN_FIT_SCALE]) statt abgeschnitten.
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
        // Viele Widgets lassen um ihre Fläche (android:id/background, Android-12-Konvention) Rand für Launcher-Zellabstand
        // frei — iOS-Widgets füllen den Rahmen. Darum die Fläche messen und auf den Rahmen hochskalieren + mittig rücken.
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
 * Die Gesten-Ebene des Homescreens liegt über allen Widgets und fängt jede Berührung ab — Android-Widgets bekämen
 * sonst nie einen Tap. Der Homescreen meldet Taps hierher; wir rechnen die Fensterposition durch alle Ebenen
 * (Skalierung, Verschiebung) in Widget-Koordinaten um und spielen DOWN/UP in die AppWidgetHostView ein.
 */
object AndroidWidgetTaps {
    private val views = HashMap<Int, Pair<java.lang.ref.WeakReference<android.view.View>, androidx.compose.ui.layout.LayoutCoordinates>>()

    fun register(id: Int, v: android.view.View, c: androidx.compose.ui.layout.LayoutCoordinates) { views[id] = java.lang.ref.WeakReference(v) to c }

    /** @return true, wenn ein Widget-Ansicht den Tap bekommen hat. */
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
}

/**
 * Zeichnet außer der Fläche [bg] (und ihren Eltern) noch eine andere sichtbare Ansicht? Dann ist [bg] nicht die
 * sichtbare Widget-Fläche (z. B. Analoguhr: unsichtbarer innerer Rahmen, Zifferblatt daneben über die ganze Höhe) —
 * Hochskalieren würde abschneiden.
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

/** Höchstens so weit wird die Widget-Fläche hochskaliert, um Launcher-Rand auszugleichen. */
private const val MAX_FILL_SCALE = 1.3f

/** Beschriftung unter dem Widget (iOS: App-/Widget-Name). */
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
