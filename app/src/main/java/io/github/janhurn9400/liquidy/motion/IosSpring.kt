package io.github.janhurn9400.liquidy.motion

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import io.github.janhurn9400.liquidy.tuning.Tunable
import io.github.janhurn9400.liquidy.tuning.tunable
import kotlin.math.PI

/**
 * Spring in Apple's format (response + dampingFraction), converted to Compose.
 * Formel siehe docs/RESEARCH.md §3.2:  stiffness = (2π / response)²,  dampingRatio = dampingFraction  (Masse 1).
 */
class IosSpring(val response: Tunable, val dampingFraction: Tunable) {

    val stiffness: Float get() = stiffnessFor(response.value)

    fun <T> spec(visibilityThreshold: T? = null): SpringSpec<T> =
        spring(dampingRatio = dampingFraction.value, stiffness = stiffness, visibilityThreshold = visibilityThreshold)

    /**
     * Remaining displacement after [t] s of a spring released at rest from [x0] (closed form, mass 1) — for many items
     * that share one clock but run with their own [responseScale] (e.g. staggered unlock zoom).
     */
    fun displacement(x0: Float, t: Float, responseScale: Float = 1f): Float {
        if (t <= 0f) return x0
        val w = 2.0 * PI / (response.value * responseScale)
        val z = dampingFraction.value.toDouble().coerceAtMost(0.999)
        val wd = w * kotlin.math.sqrt(1 - z * z)
        return (x0 * kotlin.math.exp(-z * w * t) * (kotlin.math.cos(wd * t) + z * w / wd * kotlin.math.sin(wd * t))).toFloat()
    }

    companion object {
        fun stiffnessFor(response: Float): Float {
            val omega = 2.0 * PI / response
            return (omega * omega).toFloat()
        }
    }
}

internal fun iosSpring(id: String, label: String, response: Float, dampingFraction: Float) = IosSpring(
    response = tunable("$id.response", "$label – response", "Motion", response, 0.05f, 1.2f, "s"),
    dampingFraction = tunable("$id.damping", "$label – dampingFraction", "Motion", dampingFraction, 0.2f, 1.5f),
)
