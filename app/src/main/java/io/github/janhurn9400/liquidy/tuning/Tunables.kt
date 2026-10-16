package io.github.janhurn9400.liquidy.tuning

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import io.github.janhurn9400.liquidy.design.DesignTokens
import io.github.janhurn9400.liquidy.motion.Motion
import java.util.Locale

/**
 * A live-adjustable value. The defaults live exclusively in Motion.kt / DesignTokens.kt;
 * the debug panel only overrides the runtime value. It's read via [value] (Compose state),
 * so the UI recomposes/re-animates immediately when a slider is moved.
 */
class Tunable(
    val id: String,
    val label: String,
    val group: String,
    val default: Float,
    val min: Float,
    val max: Float,
    val unit: String,
) {
    var value by mutableFloatStateOf(default)
    val isChanged: Boolean get() = value != default
}

object Tunables {
    private val registry = LinkedHashMap<String, Tunable>()
    private const val PREFS = "tunables"

    val all: List<Tunable> get() = registry.values.toList()

    fun register(t: Tunable): Tunable {
        check(registry.put(t.id, t) == null) { "Tunable registered twice: ${t.id}" }
        return t
    }

    /** Forces registration of all values and loads saved overrides. */
    fun init(context: Context) {
        Motion.touch()
        DesignTokens.touch()
        DebugOptions.touch()
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        registry.values.forEach { t ->
            if (prefs.contains(t.id)) t.value = prefs.getFloat(t.id, t.default)
        }
    }

    fun save(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            clear()
            registry.values.filter { it.isChanged }.forEach { putFloat(it.id, it.value) }
        }.apply()
    }

    fun resetAll(context: Context) {
        registry.values.forEach { it.value = it.default }
        save(context)
    }

    /** Changed values as text — taken over into Motion.kt / DesignTokens.kt as new defaults. */
    fun export(): String {
        val changed = registry.values.filter { it.isChanged }
        if (changed.isEmpty()) return "No changes compared to the defaults."
        return changed.joinToString("\n") {
            String.format(Locale.US, "%s = %.3f %s  (Default %.3f)", it.id, it.value, it.unit, it.default)
        }
    }
}

fun tunable(id: String, label: String, group: String, default: Float, min: Float, max: Float, unit: String = "") =
    Tunables.register(Tunable(id, label, group, default, min, max, unit))
