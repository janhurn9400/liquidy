package io.github.janhurn9400.liquidy.tuning

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import io.github.janhurn9400.liquidy.design.DesignTokens
import io.github.janhurn9400.liquidy.motion.Motion
import java.util.Locale

/**
 * Ein live-justierbarer Wert. Die Defaults stehen ausschließlich in Motion.kt / DesignTokens.kt;
 * das Debug-Panel überschreibt nur den Laufzeitwert. Gelesen wird über [value] (Compose-State),
 * dadurch rekomponiert/re-animiert die UI sofort beim Verschieben eines Sliders.
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
        check(registry.put(t.id, t) == null) { "Tunable doppelt registriert: ${t.id}" }
        return t
    }

    /** Erzwingt die Registrierung aller Werte und lädt gespeicherte Overrides. */
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

    /** Geänderte Werte als Text — wird in Motion.kt / DesignTokens.kt als neue Defaults übernommen. */
    fun export(): String {
        val changed = registry.values.filter { it.isChanged }
        if (changed.isEmpty()) return "Keine Änderungen gegenüber den Defaults."
        return changed.joinToString("\n") {
            String.format(Locale.US, "%s = %.3f %s  (Default %.3f)", it.id, it.value, it.unit, it.default)
        }
    }
}

fun tunable(id: String, label: String, group: String, default: Float, min: Float, max: Float, unit: String = "") =
    Tunables.register(Tunable(id, label, group, default, min, max, unit))
