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
 * Eigener Pager (ARCHITECTURE Entscheidung 1). Position in Seiten (0 = erste Seite).
 *  - Finger: Position folgt 1:1 (M-PAGE-DRAG), jenseits der Ränder Gummiband (M-RUBBER).
 *  - Loslassen: Feder M-PAGE mit Fingergeschwindigkeit als initialVelocity.
 *  - Neuer Touch während der Feder: snapTo stoppt sie an Ort und Stelle → Übernahme ohne Sprung.
 */
@Stable
class PagerState {
    val position = Animatable(0f)

    var pageCount by mutableIntStateOf(1)
    /** Erste erreichbare Seite: −1 = Heute-Ansicht links von Seite 0 (nicht im Bearbeitungsmodus). */
    var firstPage by mutableIntStateOf(0)
    var isDragging by mutableStateOf(false)
        private set

    /** Unbegrenzte „rohe“ Fingerposition in Seiten; angezeigt wird sie mit Gummiband. */
    private var rawPosition = 0f
    private var dragStartPage = 0

    /** Aktuelle Homescreen-Seite (nie die Heute-Ansicht: −1 wird zu 0). */
    val currentPage: Int get() = position.value.roundToInt().coerceIn(0, lastPage)
    private val lastPage: Int get() = (pageCount - 1).coerceAtLeast(0)
    val isScrolling: Boolean get() = isDragging || position.isRunning

    /** Synchron, damit kein Finger-Delta verloren geht; das Stoppen der Feder erledigt das erste snapTo. */
    fun beginDrag() {
        rawPosition = unRubberBand(position.value)
        dragStartPage = position.value.roundToInt().coerceIn(firstPage, lastPage)
        isDragging = true
    }

    /**
     * @param deltaPages positive Werte = Richtung höherer Seiten (Finger nach links).
     * @return anzuzeigende Position (mit Gummiband) — der Aufrufer setzt sie per position.snapTo().
     */
    fun dragBy(deltaPages: Float, pageWidthPx: Float): Float {
        rawPosition += deltaPages
        return rubberBand(rawPosition, pageWidthPx)
    }

    /** @param velocityPages Geschwindigkeit in Seiten/s, positiv = Richtung höherer Seiten. */
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

    /** Touch ohne Drag während laufender Animation: zur nächsten Seite einrasten (wie UIScrollView-Paging). */
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

    // M-RUBBER: f(x, d, c) = (x·d·c) / (d + c·x), x und d in px
    private fun rubberBand(raw: Float, d: Float): Float {
        val c = Motion.rubberBandC.value
        return when {
            raw < firstPage -> firstPage - rb((firstPage - raw) * d, d, c) / d
            raw > lastPage -> lastPage + rb((raw - lastPage) * d, d, c) / d
            else -> raw
        }
    }

    private fun rb(x: Float, d: Float, c: Float) = (x * d * c) / (d + c * x)

    // Umkehrung, damit eine Übernahme mitten im Gummiband nicht springt: x = y·d / (c·(d − y))
    private fun unRubberBand(shown: Float): Float {
        val c = Motion.rubberBandC.value
        fun inv(y: Float) = y / (c * (1f - y)) // in Einheiten von d (Seitenbreite)
        return when {
            shown < firstPage -> firstPage - inv((firstPage - shown).coerceAtMost(0.99f))
            shown > lastPage -> lastPage + inv((shown - lastPage).coerceAtMost(0.99f))
            else -> shown
        }
    }

    private companion object {
        const val VISIBILITY_THRESHOLD = 0.0005f // Seiten ≈ 0,2 pt
    }
}

