package io.github.janhurn9400.liquidy.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import io.github.janhurn9400.liquidy.design.DesignTokens
import io.github.janhurn9400.liquidy.motion.Motion
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * iOS 26/27 swipe to delete for a list row [MEASURED user's iPhone recording 11-26, Messages]: swipe left → the row
 * slides, a round red trash button grows out of a dot at the row end (T-SWIPE.button / inset). Released past half the
 * button it stays open (tap it to delete), otherwise it closes. Pulled further the button stretches into a capsule;
 * past T-SWIPE.fullAt of the row width it snaps to the full row width (M-SWIPE.snap) — releasing there deletes. The
 * deleted row's height then collapses (M-SWIPE.collapse) before [onDelete] removes it.
 */
@Composable
fun SwipeToDelete(
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    /** Row background at rest, and the highlight it turns into while swiped (light list: pressed grey) [MEASURED dark: 36/36/38]. */
    rowColor: Color = Color.White,
    highlight: Color = Color(0xFFE5E5EA),
    /** Smaller rows than the original (≈ 76 pt): cap the button so it keeps a margin above and below. */
    maxButton: androidx.compose.ui.unit.Dp? = null,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val density = LocalDensity.current.density
    val offset = remember { Animatable(0f) }      // row shift, px (≤ 0)
    val full = remember { Animatable(0f) }        // 0…1 snap of the capsule to the full row width
    val collapse = remember { Animatable(1f) }    // row height share while being removed
    val gone = remember { Animatable(0f) }        // 0…1 the remaining sliver leaves on delete
    var armed by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val d = minOf(DesignTokens.swipeButton.value, maxButton?.value ?: Float.MAX_VALUE) * density
    val inset = DesignTokens.swipeInset.value * density
    val open = d + 2 * inset                      // shift at which the button stands fully

    fun delete() {
        if (deleting) return
        deleting = true
        scope.launch {
            // release at the full-swipe point: the sliver slides out, then the row's height collapses from the bottom
            // (the red capsule gets cut, the rows below move up) [MEASURED user's iPhone 11-26]
            full.animateTo(1f, Motion.swipeSnap.spec())
            gone.animateTo(1f, Motion.swipeSnap.spec())
            collapse.animateTo(0f, Motion.swipeCollapse.spec(visibilityThreshold = 0.001f))
            onDelete()
        }
    }

    // clip OUTSIDE the shrinking layout: it then clips to the collapsing height (the capsule gets cut from the bottom and
    // the rows below move up over nothing, like the original) — inside it clipped to the full height
    BoxWithConstraints(modifier.fillMaxWidth().graphicsLayer { clip = true }.layout { m, c ->
        val p = m.measure(c)
        val h = (p.height * collapse.value).roundToInt()
        layout(p.width, h) { p.place(0, 0) }
    }) {
        val rowW = constraints.maxWidth.toFloat()
        val fullAt = rowW * DesignTokens.swipeFullAt.value
        // ── actions behind the row ──
        Canvas(Modifier.matchParentSize().pointerInput(Unit) {
            detectTapGestures { p ->
                // tap on the (open) button deletes
                if (-offset.value >= open * 0.5f && p.x >= size.width - (-offset.value)) delete()
            }
        }) {
            val pull = -offset.value
            if (pull <= 0.5f && full.value <= 0f) return@Canvas
            val cy = size.height / 2
            // grows from a dot to full size while the first button-width is revealed [MEASURED frames 15–19]
            val grow = (pull / open).coerceIn(0f, 1f)
            val dia = d * grow
            // pulled past the open point → capsule anchored at the right inset; snapped → full row width
            val stretchW = maxOf(dia, pull - 2 * inset)
            val sliver = DesignTokens.swipeSliver.value * density * (1f - gone.value)
            val fullW = size.width - sliver - 2 * inset
            val w = stretchW + (fullW - stretchW) * full.value
            val right = size.width - inset
            if (dia < 1f && full.value <= 0f) return@Canvas
            val h = maxOf(dia, if (full.value > 0f) d else dia)
            drawRoundRect(RED, Offset(right - w, cy - h / 2), Size(w, h), CornerRadius(h / 2))
            // trash glyph stays centred while the capsule stretches; at the full-swipe point (haptic tick) it travels to
            // the capsule's left end [MEASURED user's iPhone 11-26]
            val g = h * TRASH_SHARE
            val gx = (right - w / 2) + ((right - w + h / 2) - (right - w / 2)) * full.value
            if (g > 2f) drawTrash(Offset(gx, cy), g)
        }
        // ── the row itself ──
        Box(Modifier.graphicsLayer {
                // armed: the row leaves to the left down to a small sliver, so the full capsule stands free (round on both ends)
                val sliver = DesignTokens.swipeSliver.value * density * (1f - gone.value)
                translationX = offset.value + (-(size.width - sliver) - offset.value) * full.value
            }
            // the moved row is a highlighted card with rounded ends (the original lifts it off the list)
            .drawBehind {
                val k = ((-offset.value) / (open * HIGHLIGHT_IN)).coerceIn(0f, 1f)
                val c = androidx.compose.ui.graphics.lerp(rowColor, highlight, k)
                val rad = DesignTokens.swipeRowCorner.value * density * k
                drawRoundRect(c, cornerRadius = CornerRadius(rad))
            }
            .draggable(rememberDraggableState { dx ->
                if (deleting) return@rememberDraggableState
                scope.launch {
                    val next = (offset.value + dx).coerceAtMost(0f)
                    offset.snapTo(next)
                    val nowArmed = -next >= fullAt
                    if (nowArmed != armed) {
                        armed = nowArmed
                        io.github.janhurn9400.liquidy.data.Haptics.tick(view)
                        launch { full.animateTo(if (nowArmed) 1f else 0f, Motion.swipeSnap.spec()) }
                    }
                }
            }, Orientation.Horizontal, onDragStopped = { v ->
                if (deleting) return@draggable
                when {
                    armed -> delete()
                    -offset.value > open / 2 || v < -FLING_OPEN * density -> scope.launch { offset.animateTo(-open, Motion.swipeSettle.spec(), v) }
                    else -> scope.launch { offset.animateTo(0f, Motion.swipeSettle.spec(), v) }
                }
            })
            // tapping the row while it is open only closes it (like iOS) — seen before the row's own click (Initial pass)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false,
                        pass = androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                    if (offset.value < -0.5f && !deleting) {
                        down.consume()
                        scope.launch { offset.animateTo(0f, Motion.swipeSettle.spec()) }
                        waitForUpOrCancellation(androidx.compose.ui.input.pointer.PointerEventPass.Initial)?.consume()
                    }
                }
            }) { content() }
    }
}

/** Outline trash can, white, centred at [c], [s] tall. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTrash(c: Offset, s: Float) {
    val sw = s * 0.11f
    val st = Stroke(sw, cap = StrokeCap.Round)
    val bodyW = s * 0.62f; val bodyTop = c.y - s * 0.28f; val bodyBot = c.y + s * 0.5f
    // lid + handle
    drawLine(Color.White, Offset(c.x - s * 0.42f, bodyTop), Offset(c.x + s * 0.42f, bodyTop), sw, StrokeCap.Round)
    drawRoundRect(Color.White, Offset(c.x - s * 0.14f, c.y - s * 0.46f), Size(s * 0.28f, s * 0.18f), CornerRadius(s * 0.06f), style = st)
    // body
    drawRoundRect(Color.White, Offset(c.x - bodyW / 2, bodyTop), Size(bodyW, bodyBot - bodyTop), CornerRadius(s * 0.12f), style = st)
    drawLine(Color.White, Offset(c.x - s * 0.11f, bodyTop + s * 0.18f), Offset(c.x - s * 0.11f, bodyBot - s * 0.16f), sw * 0.8f, StrokeCap.Round)
    drawLine(Color.White, Offset(c.x + s * 0.11f, bodyTop + s * 0.18f), Offset(c.x + s * 0.11f, bodyBot - s * 0.16f), sw * 0.8f, StrokeCap.Round)
}

private val RED = Color(0xFFFF383D)     // [MEASURED] button fill 255/56/61
private const val TRASH_SHARE = 0.385f   // glyph height / button height [MEASURED 57 px in 148 px]
private const val HIGHLIGHT_IN = 0.3f     // highlight fully on after this share of the button width [ESTIMATED]
private const val FLING_OPEN = 400f      // dp/s: a quick flick opens even when short [ESTIMATED]

/** Highlight of a swiped row on a dark surface [MEASURED 36/36/38 on black ≈ white 14 %]. */
val SwipeHighlightDark = Color.White.copy(alpha = 0.14f)
