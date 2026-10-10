package io.github.janhurn9400.liquidy.ui.home

import android.os.CancellationSignal
import android.view.View
import androidx.compose.runtime.withFrameNanos
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationControlListenerCompat
import androidx.core.view.WindowInsetsAnimationControllerCompat
import androidx.core.view.WindowInsetsCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext

/**
 * Statusleiste an ihrem Platz ausblenden statt nach oben schieben (iOS: wird beim Ordner mitverwischt —
 * eine Systemleiste lässt sich nicht verwischen, aber ihre Deckkraft lässt sich App-gesteuert setzen:
 * WindowInsetsAnimationController.setInsetsAndAlpha, Alpha wirkt auf die Leisten-Surface, InsetsAnimationControlImpl).
 */
class StatusBarFader(private val view: View) {

    /** Übernimmt die Statusleiste; null, wenn das System die Kontrolle nicht gibt. */
    private suspend fun control(): Pair<WindowInsetsAnimationControllerCompat, CancellationSignal>? {
        val ready = CompletableDeferred<WindowInsetsAnimationControllerCompat?>()
        val signal = CancellationSignal()
        val listener = object : WindowInsetsAnimationControlListenerCompat {
            override fun onReady(c: WindowInsetsAnimationControllerCompat, types: Int) { ready.complete(c) }
            override fun onFinished(c: WindowInsetsAnimationControllerCompat) { ready.complete(null) }
            override fun onCancelled(c: WindowInsetsAnimationControllerCompat?) { ready.complete(null) }
        }
        val ctl = ViewCompat.getWindowInsetsController(view) ?: return null
        ctl.controlWindowInsetsAnimation(WindowInsetsCompat.Type.statusBars(), -1L, null, signal, listener)
        return ready.await()?.let { it to signal }
    }

    /**
     * Folgt [alpha] (1 = sichtbar, 0 = weg) Frame für Frame, die Leiste bleibt dabei an ihrem Platz.
     * Endet, sobald [done] true liefert; danach ist die Leiste versteckt ([showAtEnd] = false) bzw. sichtbar.
     */
    suspend fun follow(alpha: () -> Float, done: () -> Boolean, showAtEnd: () -> Boolean) {
        val (c, signal) = control() ?: return
        try {
            val shown = c.shownStateInsets
            while (coroutineContext.isActive && !c.isFinished && !c.isCancelled && !done()) {
                withFrameNanos { }
                c.setInsetsAndAlpha(shown, alpha().coerceIn(0f, 1f), 1f)
            }
            if (!c.isFinished && !c.isCancelled) c.finish(showAtEnd())
        } catch (e: Exception) {
            signal.cancel()
            throw e
        }
    }
}
