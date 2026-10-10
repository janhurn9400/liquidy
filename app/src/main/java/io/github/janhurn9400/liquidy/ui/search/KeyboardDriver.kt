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
 * Steuert die Tastatur Frame für Frame selbst (WindowInsetsControllerCompat.controlWindowInsetsAnimation),
 * damit sie wie bei iOS schon beim Morph der Pill herauslugt und beim Loslassen ohne Startverzögerung
 * (gemessen 230–410 ms bei keyboard.show()) mit der iOS-Feder ganz hochfährt.
 */
class KeyboardDriver(private val view: View, private val scope: CoroutineScope) {
    private var controller: WindowInsetsAnimationControllerCompat? = null
    private var cancellation: CancellationSignal? = null
    private val bottom = Animatable(0f)
    private var job: Job? = null
    /** Ziel, solange der Controller noch nicht bereit ist: (Pixel oder null = ganz), Feder, danach abschließen? */
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

    /** Übernimmt die Tastatur (zeigt sie unter App-Kontrolle). Setzt voraus, dass ein Textfeld fokussiert ist. */
    fun start() {
        if (active) return
        val signal = CancellationSignal()
        cancellation = signal
        ViewCompat.getWindowInsetsController(view)
            ?.controlWindowInsetsAnimation(WindowInsetsCompat.Type.ime(), -1L, null, signal, listener)
            ?: run { cancellation = null }
    }

    /** Fährt auf [px] Tastaturhöhe (null = ganz offen); [finish]: true = danach offen lassen, false = schließen. */
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
