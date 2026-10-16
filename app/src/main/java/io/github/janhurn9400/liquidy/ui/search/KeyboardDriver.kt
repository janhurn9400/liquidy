package io.github.janhurn9400.liquidy.ui.search

import android.os.CancellationSignal
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationControlListenerCompat
import androidx.core.view.WindowInsetsAnimationControllerCompat
import androidx.core.view.WindowInsetsCompat
import io.github.janhurn9400.liquidy.motion.IosSpring
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Drives the keyboard frame by frame itself (WindowInsetsControllerCompat.controlWindowInsetsAnimation),
 * so that like on iOS it already peeks out during the pill morph and on release comes all the way up without a start delay
 * (measured 230–410 ms with keyboard.show()) with the iOS spring.
 */
class KeyboardDriver(private val view: View, private val scope: CoroutineScope) {
    private var controller: WindowInsetsAnimationControllerCompat? = null
    private var cancellation: CancellationSignal? = null
    private val bottom = Animatable(0f)
    private var job: Job? = null
    /** Target while the controller isn't ready yet: (pixels or null = fully), spring, finish afterwards? */
    private var pending: Triple<Float?, IosSpring, Boolean?>? = null

    val active: Boolean get() = cancellation != null

    private val listener = object : WindowInsetsAnimationControlListenerCompat {
        override fun onReady(c: WindowInsetsAnimationControllerCompat, types: Int) {
            controller = c
            scope.launch { bottom.snapTo(c.currentInsets.bottom.toFloat()) }
            pending?.let { (px, spring, finish) -> run(px, spring, finish) }
            pending = null
        }
        override fun onFinished(c: WindowInsetsAnimationControllerCompat) = reset()
        override fun onCancelled(c: WindowInsetsAnimationControllerCompat?) = reset()
    }

    private fun reset() {
        job?.cancel(); controller = null; cancellation = null; pending = null
    }

    /** Takes over the keyboard (shows it under app control). Requires a focused text field. */
    fun start() {
        if (active) return
        val signal = CancellationSignal()
        cancellation = signal
        ViewCompat.getWindowInsetsController(view)
            ?.controlWindowInsetsAnimation(WindowInsetsCompat.Type.ime(), -1L, null, signal, listener)
            ?: run { cancellation = null }
    }

    /** Moves to [px] keyboard height (null = fully open); [finish]: true = leave open afterwards, false = close. */
    fun animateTo(px: Float?, spring: IosSpring, finish: Boolean? = null): Job? {
        if (controller == null) { pending = Triple(px, spring, finish); return null }
        return run(px, spring, finish)
    }

    private fun run(px: Float?, spring: IosSpring, finish: Boolean?): Job? {
        val c = controller ?: return null
        val hidden = c.hiddenStateInsets.bottom.toFloat()
        val shown = c.shownStateInsets.bottom.toFloat()
        val target = (px ?: shown).coerceIn(hidden, shown)
        job?.cancel()
        job = scope.launch {
            bottom.animateTo(target, spring.spec()) {
                if (!c.isFinished && !c.isCancelled) {
                    val fraction = if (shown > hidden) (value - hidden) / (shown - hidden) else 1f
                    c.setInsetsAndAlpha(Insets.of(0, 0, 0, value.toInt()), 1f, fraction.coerceIn(0f, 1f))
                }
            }
            if (finish != null && !c.isFinished && !c.isCancelled) c.finish(finish)
        }
        return job
    }

    fun cancel() {
        cancellation?.cancel()
        reset()
    }
}
