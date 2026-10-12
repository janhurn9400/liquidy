package io.github.janhurn9400.liquidy.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.provider.MediaStore.Files.FileColumns as f
import android.provider.Settings
import androidx.core.content.ContextCompat
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/**
 * Globale Suche (Stufe 2/3): Fotos (Dateiname, Album, Monat/Jahr), Dateien (Name), Android-Einstellungen (Stichworte).
 * Nutzt den vorhandenen Android-Index (MediaStore) — kein eigenes Indexieren; bleibt komplett auf dem Gerät.
 */
object DeviceSearch {
    data class Photo(val uri: Uri, val name: String)
    data class FileHit(val uri: Uri, val name: String, val mime: String?, val size: Long, val modified: Long, val folder: String)
    data class SettingHit(val label: String, val action: String)

    // ── Fotos ─────────────────────────────────────────────────────────────────
    fun hasPhotos(c: Context) =
        ContextCompat.checkSelfPermission(c, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(c, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED

    /** „Screenshots“, „Camera“ (Album), „IMG_2024“ (Name), „Juli 2025“ / „July“ / „2024“ (Aufnahmedatum). */
    fun photos(c: Context, query: String, max: Int = 12): List<Photo> {
        val q = query.trim()
        if (q.length < 2 || !hasPhotos(c)) return emptyList()
        val range = dateRange(q)
        val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val sel: String; val args: Array<String>
        if (range != null) {
            // ohne EXIF-Aufnahmedatum (z. B. Screenshots, geladene Bilder) zählt das Hinzufügedatum
            val d = "COALESCE(${MediaStore.Images.Media.DATE_TAKEN}, ${MediaStore.Images.Media.DATE_ADDED} * 1000)"
            sel = "$d >= ? AND $d < ?"
            args = arrayOf(range.first.toString(), range.second.toString())
        } else {
            sel = "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ? OR ${MediaStore.Images.Media.BUCKET_DISPLAY_NAME} LIKE ?"
            args = arrayOf("%$q%", "%$q%")
        }
        val out = ArrayList<Photo>()
        runCatching {
            c.contentResolver.query(uri, arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME), sel, args,
                "${MediaStore.Images.Media.DATE_TAKEN} DESC")?.use { cur ->
                while (cur.moveToNext() && out.size < max) out += Photo(ContentUris.withAppendedId(uri, cur.getLong(0)), cur.getString(1) ?: "")
            }
        }
        return out
    }

    /** Monat (+ optional Jahr) oder Jahr im Text → [Start, Ende) in ms; Monatsnamen in Gerätesprache und Englisch. */
    private fun dateRange(q: String): Pair<Long, Long>? {
        val words = q.lowercase(Locale.getDefault()).split(' ', ',', '.').filter { it.isNotBlank() }
        val year = words.firstNotNullOfOrNull { w -> w.toIntOrNull()?.takeIf { it in 1990..2100 } }
        val month = words.firstNotNullOfOrNull { w ->
            if (w.length < 3) null else (1..12).firstOrNull { m ->
                listOf(Locale.getDefault(), Locale.ENGLISH).any { l ->
                    java.time.Month.of(m).getDisplayName(TextStyle.FULL, l).lowercase(l).startsWith(w) ||
                        w.startsWith(java.time.Month.of(m).getDisplayName(TextStyle.SHORT, l).lowercase(l).trimEnd('.'))
                }
            }
        }
        if (month == null && year == null) return null
        // nur Monats-/Jahres-Wörter → Datumssuche; sonst Namenssuche (z. B. „Mai Urlaub“ bleibt Name)
        if (words.size > (if (month != null) 1 else 0) + (if (year != null) 1 else 0)) return null
        val zone = ZoneId.systemDefault()
        val y = year ?: LocalDate.now().year.let { if (month != null && month > LocalDate.now().monthValue) it - 1 else it }
        val start = if (month != null) LocalDate.of(y, month, 1) else LocalDate.of(y, 1, 1)
        val end = if (month != null) start.plusMonths(1) else start.plusYears(1)
        return start.atStartOfDay(zone).toInstant().toEpochMilli() to end.atStartOfDay(zone).toInstant().toEpochMilli()
    }

    fun openPhoto(c: Context, p: Photo) = view(c, p.uri, "image/*")

    // ── Dateien ───────────────────────────────────────────────────────────────
    /** Alle Dateien lesbar? (Dateizugriff — sonst sieht der Launcher nur Medien.) */
    fun hasAllFiles() = Environment.isExternalStorageManager()

    fun files(c: Context, query: String, max: Int = 5): List<FileHit> {
        val q = query.trim()
        if (q.length < 2) return emptyList()
        val uri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        // keine Fotos/Videos (eigener Abschnitt), keine Ordner, keine versteckten/Cache-Dateien
        val sel = "${f.DISPLAY_NAME} LIKE ? AND ${f.MEDIA_TYPE} NOT IN (${f.MEDIA_TYPE_IMAGE}, ${f.MEDIA_TYPE_VIDEO}) AND ${f.MIME_TYPE} IS NOT NULL " +
            "AND ${f.RELATIVE_PATH} NOT LIKE 'Android/%' AND ${f.DISPLAY_NAME} NOT LIKE '.%'"
        val out = ArrayList<FileHit>()
        runCatching {
            c.contentResolver.query(uri, arrayOf(f._ID, f.DISPLAY_NAME, f.MIME_TYPE, f.SIZE, f.DATE_MODIFIED, f.RELATIVE_PATH), sel, arrayOf("%$q%"),
                "${f.DATE_MODIFIED} DESC")?.use { cur ->
                while (cur.moveToNext() && out.size < max) {
                    out += FileHit(ContentUris.withAppendedId(uri, cur.getLong(0)), cur.getString(1) ?: "", cur.getString(2), cur.getLong(3),
                        cur.getLong(4) * 1000, (cur.getString(5) ?: "").trimEnd('/'))
                }
            }
        }
        return out
    }

    fun openFile(c: Context, f: FileHit) = view(c, f.uri, f.mime ?: "*/*")

    private fun view(c: Context, uri: Uri, mime: String) {
        runCatching {
            c.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    // ── Einstellungen ─────────────────────────────────────────────────────────
    /** Stichwort-Liste (de + en) → Android-Einstellungsseite. Name je Sprache aus den Strings. */
    private class Entry(val labelRes: Int, val action: String, val keys: List<String>)

    private val entries by lazy {
        listOf(
            Entry(io.github.janhurn9400.liquidy.R.string.set_wifi, Settings.ACTION_WIFI_SETTINGS, listOf("wlan", "wifi", "wi-fi", "netzwerk", "network", "internet")),
            Entry(io.github.janhurn9400.liquidy.R.string.set_bluetooth, Settings.ACTION_BLUETOOTH_SETTINGS, listOf("bluetooth", "kopfhörer", "headphones", "koppeln", "pair")),
            Entry(io.github.janhurn9400.liquidy.R.string.set_mobile, Settings.ACTION_NETWORK_OPERATOR_SETTINGS, listOf("mobil", "mobile", "daten", "data", "sim", "roaming", "netz", "cellular")),
            Entry(io.github.janhurn9400.liquidy.R.string.set_hotspot, Settings.ACTION_WIRELESS_SETTINGS, listOf("hotspot", "tethering", "teilen", "share")),
            Entry(io.github.janhurn9400.liquidy.R.string.set_airplane, Settings.ACTION_AIRPLANE_MODE_SETTINGS, listOf("flug", "airplane", "flight")),
            Entry(io.github.janhurn9400.liquidy.R.string.set_display, Settings.ACTION_DISPLAY_SETTINGS, listOf("display", "anzeige", "helligkeit", "brightness", "bildschirm", "screen", "dunkel", "dark")),
            Entry(io.github.janhurn9400.liquidy.R.string.set_sound, Settings.ACTION_SOUND_SETTINGS, listOf("ton", "sound", "lautstärke", "volume", "klingelton", "ringtone", "vibration")),
            Entry(io.github.janhurn9400.liquidy.R.string.set_notifications, "android.settings.NOTIFICATION_SETTINGS", listOf("mitteilung", "benachrichtig", "notification")),
            Entry(io.github.janhurn9400.liquidy.R.string.set_dnd, "android.settings.ZEN_MODE_SETTINGS", listOf("nicht stören", "do not disturb", "dnd", "fokus", "focus")),
            Entry(io.github.janhurn9400.liquidy.R.string.set_battery, Intent.ACTION_POWER_USAGE_SUMMARY, listOf("akku", "battery", "batterie", "energie", "power")),
            Entry(io.github.janhurn9400.liquidy.R.string.set_storage, Settings.ACTION_INTERNAL_STORAGE_SETTINGS, listOf("speicher", "storage", "platz", "space")),
            Entry(io.github.janhurn9400.liquidy.R.string.set_apps, Settings.ACTION_APPLICATION_SETTINGS, listOf("apps", "anwendungen", "applications")),
            Entry(io.github.janhurn9400.liquidy.R.string.set_location, Settings.ACTION_LOCATION_SOURCE_SETTINGS, listOf("standort", "location", "gps", "ortung")),
            Entry(io.github.janhurn9400.liquidy.R.string.set_security, Settings.ACTION_SECURITY_SETTINGS, listOf("sicherheit", "security", "passwort", "password", "pin", "fingerabdruck", "fingerprint", "sperr", "lock")),
            Entry(io.github.janhurn9400.liquidy.R.string.set_privacy, Settings.ACTION_PRIVACY_SETTINGS, listOf("datenschutz", "privacy", "berechtigung", "permission")),
            Entry(io.github.janhurn9400.liquidy.R.string.set_accessibility, Settings.ACTION_ACCESSIBILITY_SETTINGS, listOf("bedienungshilfe", "accessibility", "barrierefrei", "schrift", "font", "talkback")),
            Entry(io.github.janhurn9400.liquidy.R.string.set_language, Settings.ACTION_LOCALE_SETTINGS, listOf("sprache", "language", "tastatur", "keyboard")),
            Entry(io.github.janhurn9400.liquidy.R.string.set_date, Settings.ACTION_DATE_SETTINGS, listOf("datum", "date", "uhrzeit", "time", "zeitzone", "timezone")),
            Entry(io.github.janhurn9400.liquidy.R.string.set_wallpaper, Intent.ACTION_SET_WALLPAPER, listOf("hintergrund", "wallpaper")),
            Entry(io.github.janhurn9400.liquidy.R.string.set_nfc, Settings.ACTION_NFC_SETTINGS, listOf("nfc", "bezahlen", "pay", "kontaktlos")),
            Entry(io.github.janhurn9400.liquidy.R.string.set_developer, Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS, listOf("entwickler", "developer", "usb-debugging", "debug")),
            Entry(io.github.janhurn9400.liquidy.R.string.set_about, Settings.ACTION_DEVICE_INFO_SETTINGS, listOf("über", "about", "telefon info", "geräteinfo", "android version", "build")),
        )
    }

    fun settings(c: Context, query: String, max: Int = 3): List<SettingHit> {
        val q = query.trim().lowercase(Locale.getDefault())
        if (q.length < 2) return emptyList()
        return entries.mapNotNull { e ->
            val label = c.getString(e.labelRes)
            val score = when {
                label.lowercase(Locale.getDefault()).startsWith(q) -> 0
                e.keys.any { it.startsWith(q) } -> 1
                q.length >= 3 && (label.lowercase(Locale.getDefault()).contains(q) || e.keys.any { it.contains(q) }) -> 2
                else -> return@mapNotNull null
            }
            score to SettingHit(label, e.action)
        }.sortedBy { it.first }.map { it.second }.distinctBy { it.action }.take(max)
    }

    fun openSetting(c: Context, s: SettingHit) {
        runCatching { c.startActivity(Intent(s.action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { runCatching { c.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
    }
}
