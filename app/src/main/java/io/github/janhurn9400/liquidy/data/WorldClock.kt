package io.github.janhurn9400.liquidy.data

import android.content.Context
import java.time.ZoneId

/**
 * Städte für das Uhr-Widget (Einstellung „Stadt“). Gespeichert als „Zeitzone;Stadt“ (z. B. „Europe/Vienna;Bleiburg“),
 * null = Ortszeit des Geräts.
 * Offline: alle Zeitzonen von Android (IANA, z. B. Europe/Vienna → „Vienna“) — funktioniert ohne Internet.
 * Online zusätzlich jede Stadt über dieselbe Ortssuche wie das Wetter (Open-Meteo liefert die Zeitzone mit).
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

    /** Eine Stadt je IANA-Zeitzone (Kontinent/Stadt), alphabetisch. */
    val zoneCities: List<City> by lazy {
        ZoneId.getAvailableZoneIds().asSequence()
            .filter { id -> id.count { it == '/' } >= 1 && id.substringBefore('/') in CONTINENTS }
            .map { id -> City(id, id.substringAfterLast('/').replace('_', ' '), id.substringBefore('/').replace('_', ' ')) }
            .distinctBy { it.name }
            .sortedBy { it.name }
            .toList()
    }

    /** Offline-Treffer (sofort) für [query]. */
    fun searchOffline(query: String, max: Int = 12): List<City> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        return zoneCities.filter { it.name.startsWith(q, ignoreCase = true) } .take(max) +
            zoneCities.filter { !it.name.startsWith(q, ignoreCase = true) && it.name.contains(q, ignoreCase = true) }.take(max)
    }

    /** Online (Open-Meteo-Geocoding) — Städte mit Zeitzone. Blockiert, im IO-Thread aufrufen. */
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
