package io.github.janhurn9400.liquidy.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.LocationManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Wetter für die Wetter-Widgets: Open-Meteo (frei, ohne Schlüssel), Ort = letzter bekannter Standort (grob). */
object WeatherRepository {
    data class Hour(val time: LocalDateTime, val temp: Int, val code: Int, val day: Boolean, val event: Event? = null)
    enum class Event { SUNRISE, SUNSET }
    data class Day(val date: LocalDate, val code: Int, val min: Int, val max: Int)
    data class Weather(
        val place: String, val current: Boolean, val temp: Int, val code: Int, val day: Boolean,
        val high: Int, val low: Int, val hours: List<Hour>, val days: List<Day>, val fetched: Long,
    )

    sealed interface State {
        data object Loading : State
        data object NoLocation : State
        data class Ready(val w: Weather) : State
    }

    /** Fester Ort (Widget-Einstellung „Ort“); null = aktueller Standort. Als Text gespeichert: „lat;lon;Name“. */
    /** [timezone] nur aus der Suche (IANA, z. B. Europe/Vienna) — nicht gespeichert. */
    data class Place(val lat: Double, val lon: Double, val name: String, val region: String = "", val timezone: String = "") {
        fun encode() = "$lat;$lon;$name"
        val key get() = "%.3f,%.3f".format(java.util.Locale.ROOT, lat, lon)
        companion object {
            fun decode(s: String?): Place? = s?.split(';', limit = 3)?.takeIf { it.size == 3 }
                ?.let { runCatching { Place(it[0].toDouble(), it[1].toDouble(), it[2]) }.getOrNull() }
        }
    }

    private val _places = MutableStateFlow<Map<String, State>>(emptyMap())
    /** Wetter je festem Ort (Schlüssel [Place.key]). */
    val places: StateFlow<Map<String, State>> = _places

    /** Lädt das Wetter für einen festen Ort neu (wenn älter als [maxAgeMs]). */
    suspend fun refreshPlace(place: Place, maxAgeMs: Long = 30 * 60_000L) {
        val cur = _places.value[place.key]
        if (cur is State.Ready && System.currentTimeMillis() - cur.w.fetched < maxAgeMs) return
        val result = withContext(Dispatchers.IO) { runCatching { fetch(place.lat, place.lon, place.name, current = false) }.getOrNull() }
        if (result != null) _places.value = _places.value + (place.key to State.Ready(result))
        else if (cur == null) _places.value = _places.value + (place.key to State.Loading)
    }

    /** Orte suchen (Open-Meteo-Geocoding, Gerätesprache). Blockiert — im IO-Thread aufrufen. */
    fun searchPlaces(query: String, max: Int = 8): List<Place> {
        val q = query.trim()
        if (q.length < 2) return emptyList()
        return runCatching {
            val url = URL("https://geocoding-api.open-meteo.com/v1/search?count=$max&language=${Locale.getDefault().language}&name=" +
                java.net.URLEncoder.encode(q, "UTF-8"))
            val c = url.openConnection() as HttpURLConnection
            c.connectTimeout = 5000; c.readTimeout = 5000
            val arr = JSONObject(c.inputStream.bufferedReader().use { it.readText() }).optJSONArray("results") ?: return@runCatching emptyList()
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val region = listOfNotNull(o.optString("admin1").ifEmpty { null }, o.optString("country").ifEmpty { null }).joinToString(", ")
                Place(o.getDouble("latitude"), o.getDouble("longitude"), o.getString("name"), region, o.optString("timezone"))
            }
        }.getOrDefault(emptyList())
    }

    /** Zuletzt gewählte Orte („Deine Orte“), neueste zuerst. */
    fun savedPlaces(context: Context): List<Place> =
        context.getSharedPreferences("weather", Context.MODE_PRIVATE).getString(SAVED, null)?.split('\n')?.mapNotNull { Place.decode(it) } ?: emptyList()

    fun rememberPlace(context: Context, p: Place) {
        val list = (listOf(p) + savedPlaces(context).filter { it.key != p.key }).take(12)
        context.getSharedPreferences("weather", Context.MODE_PRIVATE).edit().putString(SAVED, list.joinToString("\n") { it.encode() }).apply()
    }

    private fun fetch(lat: Double, lon: Double, place: String, current: Boolean): Weather {
        val url = URL("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
            "&current=temperature_2m,weather_code,is_day&hourly=temperature_2m,weather_code,is_day" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset&timezone=auto&forecast_days=7")
        val c = url.openConnection() as HttpURLConnection
        c.connectTimeout = 8000; c.readTimeout = 8000
        return parse(JSONObject(c.inputStream.bufferedReader().use { it.readText() }), place, current, System.currentTimeMillis())
    }

    private val _state = MutableStateFlow<State>(State.Loading)
    val state: StateFlow<State> = _state
    private val lock = Mutex()

    fun hasLocationPermission(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Lädt neu, wenn älter als [maxAgeMs] (Standard 30 min). Fehler → letzter Stand bleibt. */
    suspend fun refresh(context: Context, maxAgeMs: Long = 30 * 60_000L) = lock.withLock {
        val cur = _state.value
        if (cur is State.Ready && System.currentTimeMillis() - cur.w.fetched < maxAgeMs) return@withLock
        val app = context.applicationContext
        val prefs = app.getSharedPreferences("weather", Context.MODE_PRIVATE)
        if (cur !is State.Ready) prefs.getString(CACHE, null)?.let { runCatching { parse(JSONObject(it), prefs.getString(PLACE, "") ?: "", true, prefs.getLong(TIME, 0)) }.getOrNull() }
            ?.let { _state.value = State.Ready(it) }
        val loc = withContext(Dispatchers.IO) { lastLocation(app) }
        if (loc == null) { if (_state.value !is State.Ready) _state.value = State.NoLocation; return@withLock }
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val url = URL("https://api.open-meteo.com/v1/forecast?latitude=${loc.first}&longitude=${loc.second}" +
                    "&current=temperature_2m,weather_code,is_day&hourly=temperature_2m,weather_code,is_day" +
                    "&daily=weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset&timezone=auto&forecast_days=7")
                val c = url.openConnection() as HttpURLConnection
                c.connectTimeout = 8000; c.readTimeout = 8000
                val body = c.inputStream.bufferedReader().use { it.readText() }
                val place = placeName(app, loc.first, loc.second)
                prefs.edit().putString(CACHE, body).putString(PLACE, place).putLong(TIME, System.currentTimeMillis()).apply()
                parse(JSONObject(body), place, true, System.currentTimeMillis())
            }.getOrNull()
        }
        if (result != null) _state.value = State.Ready(result)
    }

    private fun lastLocation(context: Context): Pair<Double, Double>? {
        if (!hasLocationPermission(context)) return null
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        return try {
            lm.getProviders(true).mapNotNull { lm.getLastKnownLocation(it) }.maxByOrNull { it.time }?.let { it.latitude to it.longitude }
        } catch (_: SecurityException) { null }
    }

    @Suppress("DEPRECATION")
    private fun placeName(context: Context, lat: Double, lon: Double): String = runCatching {
        Geocoder(context, Locale.getDefault()).getFromLocation(lat, lon, 1)?.firstOrNull()?.let { it.locality ?: it.subAdminArea ?: it.adminArea }
    }.getOrNull() ?: ""

    private fun parse(o: JSONObject, place: String, current: Boolean, fetched: Long): Weather {
        val iso = DateTimeFormatter.ISO_LOCAL_DATE_TIME
        val cur = o.getJSONObject("current")
        val h = o.getJSONObject("hourly"); val ht = h.getJSONArray("time"); val htemp = h.getJSONArray("temperature_2m")
        val hcode = h.getJSONArray("weather_code"); val hday = h.getJSONArray("is_day")
        val d = o.getJSONObject("daily"); val dt = d.getJSONArray("time"); val dcode = d.getJSONArray("weather_code")
        val dmax = d.getJSONArray("temperature_2m_max"); val dmin = d.getJSONArray("temperature_2m_min")
        val rise = d.getJSONArray("sunrise"); val set = d.getJSONArray("sunset")
        // Zeiten kommen in Ortszeit (timezone=auto) — „jetzt“ ebenfalls in Ortszeit, sonst stimmen Stunden bei fernen Orten nicht
        val now = o.optInt("utc_offset_seconds", Int.MIN_VALUE).let { off ->
            if (off == Int.MIN_VALUE) LocalDateTime.now() else LocalDateTime.now(java.time.ZoneOffset.UTC).plusSeconds(off.toLong())
        }
        val nextHour = now.withMinute(0).withSecond(0).withNano(0).plusHours(1)
        val hours = (0 until ht.length()).map { Hour(LocalDateTime.parse(ht.getString(it), iso), Math.round(htemp.getDouble(it)).toInt(), hcode.getInt(it), hday.getInt(it) == 1) }
            .filter { !it.time.isBefore(nextHour) }.take(8).toMutableList()
        // Sonnenauf-/-untergang zwischen den Stunden einfügen (iOS: „18:21“ mit Symbol)
        val events = (0 until dt.length()).flatMap { i ->
            listOf(LocalDateTime.parse(rise.getString(i), iso) to Event.SUNRISE, LocalDateTime.parse(set.getString(i), iso) to Event.SUNSET)
        }.filter { it.first.isAfter(now) && hours.isNotEmpty() && it.first.isBefore(hours.last().time) }
        events.forEach { (t, e) ->
            val idx = hours.indexOfFirst { it.time.isAfter(t) }.let { if (it < 0) hours.size else it }
            val ref = hours.getOrNull(idx - 1) ?: hours.first()
            hours.add(idx, Hour(t, ref.temp, ref.code, e == Event.SUNRISE, e))
        }
        val days = (0 until dt.length()).map { Day(LocalDate.parse(dt.getString(it)), dcode.getInt(it), Math.round(dmin.getDouble(it)).toInt(), Math.round(dmax.getDouble(it)).toInt()) }
        val today = days.firstOrNull { it.date == now.toLocalDate() } ?: days.first()
        return Weather(place, current, Math.round(cur.getDouble("temperature_2m")).toInt(), cur.getInt("weather_code"), cur.getInt("is_day") == 1,
            today.max, today.min, hours.take(6), days.filter { it.date.isAfter(now.toLocalDate()) }.take(5), fetched)
    }

    private const val CACHE = "json"; private const val PLACE = "place"; private const val TIME = "time"; private const val SAVED = "saved_places"
}
