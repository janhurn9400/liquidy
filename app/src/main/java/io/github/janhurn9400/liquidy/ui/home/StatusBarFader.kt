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
 * Fade the status bar out in place instead of sliding it up (iOS: it blurs along with the folder —
 * a system bar can't be blurred, but its opacity can be set app-driven:
 * WindowInsetsAnimationController.setInsetsAndAlpha, alpha acts on the bar surface, InsetsAnimationControlImpl).
 */
class StatusBarFader(private val view: View) {

    /** Takes over the status bar; null if the system doesn't give control. */
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
     * Follows [alpha] (1 = visible, 0 = gone) frame by frame, the bar stays in its place meanwhile.
     * Ends as soon as [done] returns true; afterwards the bar is hidden ([showAtEnd] = false) or visible.
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
