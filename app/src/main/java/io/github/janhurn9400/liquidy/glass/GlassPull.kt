package io.github.janhurn9400.liquidy.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import io.github.janhurn9400.liquidy.motion.Motion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Liquid Glass under the finger (M-GLASS-PULL): while held, a soft white shimmer sits where the finger is; moving the
 * finger stretches the glass along the pull (rubber-banded) while it stays almost in place; on release it springs
 * back with a small wobble and the shimmer fades.
 */
class GlassPull(private val scope: CoroutineScope) {
    val x = Animatable(0f)
    val y = Animatable(0f)
    val glow = Animatable(0f)
    /** Finger position in the element's own (untransformed) coordinates, px. */
    var touch by mutableStateOf(Offset.Zero)
    /** Element's top-left in the window (set by [glassPull]); lets the home gesture arena pass window positions. */
    var origin = Offset.Zero
    private var start = Offset.Zero

    fun press(local: Offset) {
        start = local; touch = local
        scope.launch { glow.animateTo(1f, tween(Motion.glassGlowIn.value.toInt())) }
    }

    fun drag(d: Offset) {
        touch = start + d
        scope.launch { x.snapTo(d.x) }; scope.launch { y.snapTo(d.y) }
    }

    fun release() {
        scope.launch { x.animateTo(0f, Motion.glassPullBack.spec()) }
        scope.launch { y.animateTo(0f, Motion.glassPullBack.spec()) }
        scope.launch { glow.animateTo(0f, tween(Motion.glassGlowOut.value.toInt())) }
    }

    val active get() = glow.value > 0.001f || x.value != 0f || y.value != 0f || x.isRunning || y.isRunning
}

/** Shared pull for glass drawn under the home screen's gesture arena (the arena feeds it; the glass reads it). */
object GlassTouch {
    /** Which glass is held (e.g. "pill"), null = none. */
    var key: String? = null
        private set
    private var pull: GlassPull? = null

    fun bind(scope: CoroutineScope): GlassPull = pull ?: GlassPull(scope).also { pull = it }
    /** [window] = finger position in window px. */
    fun press(k: String, window: Offset) { key = k; pull?.let { it.press(window - it.origin) } }
    fun drag(d: Offset) { if (key != null) pull?.drag(d) }
    fun release() { pull?.release(); key = null }
    fun pullFor(k: String): GlassPull? = pull?.takeIf { key == k || it.active }
}

/**
 * Current stretch of a pulled glass: the glass stretches by [e] along the pull direction [angle] (any angle), the
 * content inside only along x/y ([cx], [cy] — never skewed), both about [origin] (the edge facing away from the finger
 * stays put).
 */
class GlassStretch(val angle: Float, val e: Float, val thin: Float, val cx: Float, val cy: Float, val origin: TransformOrigin)

/** Stretch for the current pull [MEASURED Duo simulator 2026-11-26 + user]; null while not pulled. */
fun GlassPull.stretch(density: Float): GlassStretch? {
    val dx = x.value / density; val dy = y.value / density
    val d = kotlin.math.sqrt(dx * dx + dy * dy)
    if (d < 0.3f) return null
    // saturating: long pulls hardly add more
    val e = Motion.glassPullStretch.value * d / (d + Motion.glassPullRange.value)
    val ux = dx / d; val uy = dy / d
    val thin = 1f / (1f + e * Motion.glassPullThin.value)
    return GlassStretch(Math.toDegrees(kotlin.math.atan2(uy, ux).toDouble()).toFloat(), e, thin,
        1f + e * kotlin.math.abs(ux), 1f + e * kotlin.math.abs(uy), TransformOrigin(0.5f - ux * 0.5f, 0.5f - uy * 0.5f))
}

/**
 * Applies [pull] to a whole glass element (shape + shimmer): stretched along the pull. Put [glassPullContent] on its
 * content so icons and text only stretch along x/y.
 */
fun Modifier.glassPull(shape: Shape = CircleShape, pull: () -> GlassPull?): Modifier = this
    .onGloballyPositioned { c -> pull()?.origin = c.positionInWindow() }
    .graphicsLayer {
        val st = pull()?.stretch(density) ?: return@graphicsLayer
        transformOrigin = st.origin; rotationZ = st.angle; scaleX = 1f + st.e; scaleY = st.thin
    }
    .graphicsLayer {
        val st = pull()?.stretch(density) ?: return@graphicsLayer
        transformOrigin = st.origin; rotationZ = -st.angle
    }
    .drawWithContent {
        drawContent()
        val p = pull() ?: return@drawWithContent
        drawGlassGlow(p, shape)
    }

/** Turns [glassPull]'s stretch into a plain x/y stretch for the content inside (it must fill the glass element). */
fun Modifier.glassPullContent(pull: () -> GlassPull?): Modifier = this
    .graphicsLayer {
        val st = pull()?.stretch(density) ?: return@graphicsLayer
        transformOrigin = st.origin; scaleX = st.cx; scaleY = st.cy
    }
    .graphicsLayer {
        val st = pull()?.stretch(density) ?: return@graphicsLayer
        transformOrigin = st.origin; rotationZ = st.angle; scaleX = 1f / (1f + st.e); scaleY = 1f / st.thin
    }
    .graphicsLayer {
        val st = pull()?.stretch(density) ?: return@graphicsLayer
        transformOrigin = st.origin; rotationZ = -st.angle
    }

/** The white shimmer under the finger, clipped to [shape]. */
fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGlassGlow(p: GlassPull, shape: Shape) {
    val g = p.glow.value
    if (g <= 0.001f) return
    val r = maxOf(size.width, size.height) * Motion.glassGlowRadius.value
    drawOutline(shape.createOutline(size, layoutDirection, this),
        Brush.radialGradient(listOf(Color.White.copy(alpha = Motion.glassGlowAlpha.value * g), Color.Transparent), p.touch, r))
}
