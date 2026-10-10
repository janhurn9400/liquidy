package io.github.janhurn9400.liquidy.motion

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import io.github.janhurn9400.liquidy.tuning.Tunable
import io.github.janhurn9400.liquidy.tuning.tunable
import kotlin.math.PI

/**
 * Feder im Apple-Format (response + dampingFraction), umgerechnet nach Compose.
 * Formel siehe docs/RESEARCH.md §3.2:  stiffness = (2π / response)²,  dampingRatio = dampingFraction  (Masse 1).
 */
class IosSpring(val response: Tunable, val dampingFraction: Tunable) {

    val stiffness: Float get() = stiffnessFor(response.value)

    fun <T> spec(visibilityThreshold: T? = null): SpringSpec<T> =
        spring(dampingRatio = dampingFraction.value, stiffness = stiffness, visibilityThreshold = visibilityThreshold)

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
