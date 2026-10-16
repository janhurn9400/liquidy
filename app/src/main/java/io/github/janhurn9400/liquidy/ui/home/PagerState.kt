package io.github.janhurn9400.liquidy.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.janhurn9400.liquidy.motion.Motion
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

/**
 * Own pager (ARCHITECTURE decision 1). Position in pages (0 = first page).
 *  - Finger: position follows 1:1 (M-PAGE-DRAG), rubber band beyond the edges (M-RUBBER).
 *  - Release: spring M-PAGE with the finger velocity as initialVelocity.
 *  - New touch during the spring: snapTo stops it in place → takeover without a jump.
 */
@Stable
class PagerState {
    val position = Animatable(0f)

    var pageCount by mutableIntStateOf(1)
    /** First reachable page: −1 = Today view left of page 0 (not in edit mode). */
    var firstPage by mutableIntStateOf(0)
    var isDragging by mutableStateOf(false)
        private set

    /** Unlimited "raw" finger position in pages; it's displayed with rubber band. */
    private var rawPosition = 0f
    private var dragStartPage = 0

    /** Current home page (never the Today view: −1 becomes 0). */
    val currentPage: Int get() = position.value.roundToInt().coerceIn(0, lastPage)
    private val lastPage: Int get() = (pageCount - 1).coerceAtLeast(0)
    val isScrolling: Boolean get() = isDragging || position.isRunning

    /** Synchronous, so no finger delta is lost; stopping the spring is done by the first snapTo. */
    fun beginDrag() {
        rawPosition = unRubberBand(position.value)
        dragStartPage = position.value.roundToInt().coerceIn(firstPage, lastPage)
        isDragging = true
    }

    /**
     * @param deltaPages positive values = towards higher pages (finger to the left).
     * @return position to display (with rubber band) — the caller sets it via position.snapTo().
     */
    fun dragBy(deltaPages: Float, pageWidthPx: Float): Float {
        rawPosition += deltaPages
        return rubberBand(rawPosition, pageWidthPx)
    }

    /** @param velocityPages velocity in pages/s, positive = towards higher pages. */
    suspend fun endDrag(velocityPages: Float, pageWidthPx: Float, density: Float) {
        isDragging = false
        val velocityPt = abs(velocityPages) * pageWidthPx / density
        val p = position.value
        val target = when {
            velocityPt > Motion.pageFlingVelocity.value -> dragStartPage + velocityPages.sign.toInt()
            p - dragStartPage > Motion.pageDistanceFraction.value -> dragStartPage + 1
            dragStartPage - p > Motion.pageDistanceFraction.value -> dragStartPage - 1
            else -> dragStartPage
        }.coerceIn(firstPage, lastPage)
        animateTo(target, velocityPages)
    }

    /** Touch without drag during a running animation: settle to the nearest page (like UIScrollView paging). */
    suspend fun settle() {
        isDragging = false
        val p = position.value
        val target = if (p > lastPage) lastPage else if (p < firstPage) firstPage else p.roundToInt()
        animateTo(target, 0f)
    }

    suspend fun animateTo(page: Int, initialVelocity: Float = position.velocity) {
        position.animateTo(
            targetValue = page.toFloat(),
            animationSpec = Motion.page.spec(visibilityThreshold = VISIBILITY_THRESHOLD),
            initialVelocity = initialVelocity,
        )
    }

    // M-RUBBER: f(x, d, c) = (x·d·c) / (d + c·x), x and d in px
    private fun rubberBand(raw: Float, d: Float): Float {
        val c = Motion.rubberBandC.value
        return when {
            raw < firstPage -> firstPage - rb((firstPage - raw) * d, d, c) / d
            raw > lastPage -> lastPage + rb((raw - lastPage) * d, d, c) / d
            else -> raw
        }
    }

    private fun rb(x: Float, d: Float, c: Float) = (x * d * c) / (d + c * x)

    // Inverse, so a takeover in the middle of the rubber band doesn't jump: x = y·d / (c·(d − y))
    private fun unRubberBand(shown: Float): Float {
        val c = Motion.rubberBandC.value
        fun inv(y: Float) = y / (c * (1f - y)) // in units of d (page width)
        return when {
            shown < firstPage -> firstPage - inv((firstPage - shown).coerceAtMost(0.99f))
            shown > lastPage -> lastPage + inv((shown - lastPage).coerceAtMost(0.99f))
            else -> shown
        }
    }

    private companion object {
        const val VISIBILITY_THRESHOLD = 0.0005f // pages ≈ 0.2 pt
    }
}

