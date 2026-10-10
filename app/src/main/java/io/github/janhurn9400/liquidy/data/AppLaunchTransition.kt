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
 * iOS-artige Öffnen-Animation: das App-Fenster wächst mit einer Feder aus dem Icon, die Ecken runden sich vom
 * Icon-Radius auf den Bildschirmradius. Wie Launcher3 über ActivityOptions.makeRemoteTransition (Shell-Transitions).
 * Nur möglich als System-App mit CONTROL_REMOTE_APP_TRANSITION_ANIMATIONS + Hidden-API-Freigabe (Magisk-Modul);
 * sonst liefert [options] null und der Aufrufer nimmt die normale System-Animation.
 */
object AppLaunchTransition {

    private const val TAG = "AppLaunchTransition"
    private const val TRANSIT_OPEN = 1
    private const val TRANSIT_TO_FRONT = 3
    private const val FLAG_IS_WALLPAPER = 1 shl 1

    /** Paket → Widget-Key, aus dem die App zuletzt geöffnet wurde (Ziel fürs Zurückwischen statt eines Icons). */
    val widgetOrigins = java.util.concurrent.ConcurrentHashMap<String, String>()
    /** Widget-Key → Bild des Widgets beim Antippen (Fläche, in die das Fenster beim Zurückwischen überblendet). */
    val widgetSnapshots = java.util.concurrent.ConcurrentHashMap<String, android.graphics.Bitmap>()

    /** Nimmt den Bildschirmbereich [rect] des eigenen Fensters auf (PixelCopy) und merkt ihn für [key]. */
    fun snapshotWidget(window: android.view.Window, key: String, rect: RectF) {
        val r = Rect(rect.left.toInt(), rect.top.toInt(), rect.right.toInt(), rect.bottom.toInt())
        if (r.width() <= 0 || r.height() <= 0) return
        val bmp = android.graphics.Bitmap.createBitmap(r.width(), r.height(), android.graphics.Bitmap.Config.ARGB_8888)
        runCatching {
            android.view.PixelCopy.request(window, r, bmp, { res -> if (res == android.view.PixelCopy.SUCCESS) widgetSnapshots[key] = bmp },
                Handler(Looper.getMainLooper()))
        }
    }

    /** Von MainActivity gesetzt: Magisk-Modul aktiv + Karten-/Display-Radius (px). */
    @Volatile var enabled = false
    @Volatile var cardCornerPx = 0f
    @Volatile var displayCornerPx = 0f

    /** Icon/Widget, aus dem gerade eine App wächst — wird im Launcher ausgeblendet (iOS: das Icon WIRD zur Karte). */
    val launchingKey = androidx.compose.runtime.mutableStateOf<String?>(null)

    /** Für beliebige Startrahmen (Icon, Widget): ActivityOptions mit unserer Animation oder null (dann System-Animation). */
    fun optionsFor(rect: RectF, startCornerPx: Float, key: String? = null): ActivityOptions? {
        if (!enabled || rect.width() <= 0f) return null
        val o = options(rect, startCornerPx, cardCornerPx, null, displayCornerPx) ?: return null
        if (key != null) {
            launchingKey.value = key
            // Sicherheitsnetz, falls die Animation nie startet (App startet nicht)
            Handler(Looper.getMainLooper()).postDelayed({ if (launchingKey.value == key) launchingKey.value = null }, LAUNCH_HIDE_MAX_MS)
        }
        return o
    }

    /** ActivityOptions mit unserer Animation, oder null (keine Berechtigung / Schnittstelle fehlt). */
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
    }.onFailure { Log.w(TAG, "Remote-Transition nicht verfügbar", it) }.getOrNull()

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
            Log.w(TAG, "TransitionInfo nicht lesbar", e)
        }
        // Startzustand: Fenster schon im Icon, bevor es sichtbar wird
        opening.forEach { apply(start, it, icon, iconCorner, screenCorner, 0f, 0f, 0f, 0f) }
        start.apply()
        if (opening.isEmpty()) { launchingKey.value = null; done(finish); return }

        // Feder wie iOS (response/Dämpfung aus Motion), analytisch — kein Compose nötig
        val springs = listOf(Motion.appLaunch, Motion.appLaunchY, Motion.appLaunchH)
        // Entwickleroption „Animatorskala“ beachten (0 = aus, 10 = Zeitlupe)
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
                    opening.forEach { apply(end, it, icon, iconCorner, screenCorner, 1f, 1f, 1f, 1f) } // Vollbild: Radius = Display (kein Sprung)
                    end.apply()
                    launchingKey.value = null // App deckt jetzt alles ab → Icon unsichtbar zurück an seinen Platz
                    done(finish)
                }
            }
        }
        choreographer.postFrameCallback(frame)
    }

    /** Fortschritt 0 → 1 einer Feder ohne Startgeschwindigkeit (unter-/kritisch gedämpft). */
    private fun spring(t: Double, w0: Double, zeta: Double): Double = if (zeta < 1.0) {
        val wd = w0 * sqrt(1 - zeta * zeta)
        1 - exp(-zeta * w0 * t) * (Math.cos(wd * t) + zeta * w0 / wd * Math.sin(wd * t))
    } else 1 - exp(-w0 * t) * (1 + w0 * t)

    /**
     * Karte zwischen Icon und Vollbild wie iOS (recording9): [px] Breite + waagrechte Mitte, [py] senkrechte Mitte,
     * [ph] Höhe — jeweils eigene Feder. Inhalt: auf Kartenbreite skaliert, oben angesetzt, unten abgeschnitten.
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
        // wie Launcher3: setWindowCrop (intern) — der Eckenradius bezieht sich auf diesen Zuschnitt
        windowCrop?.let { runCatching { it.invoke(t, target.leash, crop) }.getOrNull() } ?: t.setCrop(target.leash, crop)
        t.setScale(target.leash, scale, scale)
        t.setPosition(target.leash, cx - cw / 2f, cy - ch / 2f)
        // Ecken (recording9): schnell vom Icon-Radius auf den Karten-Radius (62 pt), am Ende weich auf den echten
        // Display-Radius dieses Handys — so endet die Karte genau in der Bildschirmform, ohne Sprung auf eckig.
        val toCard = lerp(iconCorner, screenCorner, (px * CORNER_SPEED).coerceAtMost(1f))
        val e = ((minOf(px, ph) - CORNER_SETTLE_FROM) / (1f - CORNER_SETTLE_FROM)).coerceIn(0f, 1f)
        val corner = lerp(toCard, displayCorner, e * e * (3f - 2f * e))
        runCatching { cornerRadius?.invoke(t, target.leash, corner / scale) } // intern (Hidden-API-Freigabe)
            .onFailure { if (!loggedCorner) { loggedCorner = true; Log.w(TAG, "Eckenradius", it) } }
        if (cornerRadius == null && !loggedCorner) { loggedCorner = true; Log.w(TAG, "setCornerRadius nicht gefunden") }
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

    private const val SETTLE_RESPONSES = 1.4 // Feder ist danach praktisch ruhig
    private const val ALPHA_IN_S = 0.035 // Karte ist praktisch sofort deckend (recording9: ab dem 1. Frame App sichtbar)
    private const val CORNER_SPEED = 2f
    private const val LAUNCH_HIDE_MAX_MS = 3000L
    private const val CORNER_SETTLE_FROM = 0.6f
    /** Echter Display-Eckenradius (px) des Geräts; 0 bei eckigem Display. */
    @Volatile private var displayCorner = 0f
    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
}
