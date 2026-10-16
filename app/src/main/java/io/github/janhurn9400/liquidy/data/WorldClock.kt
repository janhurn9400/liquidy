package io.github.janhurn9400.liquidy.data

import android.content.Context
import java.time.ZoneId

/**
 * Cities for the clock widget (setting "City"). Saved as "timezone;city" (e.g. "Europe/Vienna;Bleiburg"),
 * null = the device's local time.
 * Offline: all time zones from Android (IANA, e.g. Europe/Vienna → "Vienna") — works without internet.
 * Online additionally any city via the same place search as the weather (Open-Meteo also returns the time zone).
 */
object WorldClock {
    data class City(val zone: String, val name: String, val region: String = "") {
        fun encode() = "$zone;$name"
        val zoneId: ZoneId? get() = runCatching { ZoneId.of(zone) }.getOrNull()
        companion object {
            fun decode(s: String?): City? = s?.split(';', limit = 2)?.takeIf { it.size == 2 }?.let { City(it[0], it[1]) }
                ?.takeIf { it.zoneId != null }
        }
    }

    /** One city per IANA time zone (continent/city), alphabetical. */
    val zoneCities: List<City> by lazy {
        ZoneId.getAvailableZoneIds().asSequence()
            .filter { id -> id.count { it == '/' } >= 1 && id.substringBefore('/') in CONTINENTS }
            .map { id -> City(id, id.substringAfterLast('/').replace('_', ' '), id.substringBefore('/').replace('_', ' ')) }
            .distinctBy { it.name }
            .sortedBy { it.name }
            .toList()
    }

    /** Offline matches (immediate) for [query]. */
    fun searchOffline(query: String, max: Int = 12): List<City> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        return zoneCities.filter { it.name.startsWith(q, ignoreCase = true) } .take(max) +
            zoneCities.filter { !it.name.startsWith(q, ignoreCase = true) && it.name.contains(q, ignoreCase = true) }.take(max)
    }

    /** Online (Open-Meteo geocoding) — cities with time zone. Blocks, call on the IO thread. */
    fun searchOnline(query: String): List<City> =
        WeatherRepository.searchPlaces(query).filter { it.timezone.isNotEmpty() }.map { City(it.timezone, it.name, it.region) }

    private const val PREFS = "worldclock"
    private const val SAVED = "saved"

    fun saved(context: Context): List<City> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(SAVED, null)?.split('\n')?.mapNotNull { City.decode(it) } ?: emptyList()

    fun remember(context: Context, city: City) {
        val list = (listOf(city) + saved(context).filter { it.encode() != city.encode() }).take(MAX_SAVED)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(SAVED, list.joinToString("\n") { it.encode() }).apply()
    }

    private const val MAX_SAVED = 6
    private val CONTINENTS = setOf("Africa", "America", "Antarctica", "Asia", "Atlantic", "Australia", "Europe", "Indian", "Pacific")
}
