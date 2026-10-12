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
import androidx.compose.runtime.Composable
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

    // ── Hinzufügen: id → binden (Dialog) → konfigurieren → fertig ───────────────────────────
    private var pending: Triple<Int, AppWidgetProviderInfo, (WidgetSpec?) -> Unit>? = null

    fun add(activity: Activity, p: AppWidgetProviderInfo, done: (WidgetSpec?) -> Unit) {
        val id = host.allocateAppWidgetId()
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
        val (c, r) = iosSize(p)
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
    }
}

val LocalAndroidWidgetHost = staticCompositionLocalOf<AndroidWidgetHost?> { null }

/** Android-Widget im iOS-Rahmen; meldet dem Anbieter die echte Größe (für passende Layouts). */
@Composable
fun AndroidWidget(spec: WidgetSpec, modifier: Modifier) {
    val host = LocalAndroidWidgetHost.current
    val info = host?.info(spec.appWidgetId)
    if (host == null || info == null) {
        Box(modifier.background(Color(0xFF1C1C1E)))
        return
    }
    val density = LocalDensity.current.density
    AndroidView(
        factory = { ctx ->
            host.host.createView(ctx, spec.appWidgetId, info).apply {
                addOnLayoutChangeListener { _, l, t, r, b, ol, ot, or, ob ->
                    if (r - l != or - ol || b - t != ob - ot) {
                        val size = SizeF((r - l) / density, (b - t) / density)
                        runCatching { host.manager.updateAppWidgetOptions(spec.appWidgetId, Bundle().apply {
                            putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, arrayListOf(size))
                        }) }
                    }
                }
            }
        },
        modifier = modifier,
    )
}

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
        else -> spec.provider?.let { ComponentName.unflattenFromString(it) }?.let { cn ->
            runCatching { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(cn.packageName, 0)).toString() }.getOrNull()
        } ?: ""
    }
}
