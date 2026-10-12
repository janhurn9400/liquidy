package io.github.janhurn9400.liquidy.data

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/**
 * Globale Suche (Stufe 1): Web-Vorschläge (Google, abschaltbar), Kontakte, Rechner. Apps sucht weiterhin HomeModel.
 * Alles außer den Web-Vorschlägen bleibt auf dem Gerät.
 */
object UniversalSearch {
    data class Contact(val id: Long, val lookup: String, val name: String, val number: String?, val photo: Uri?)

    /** Einstellung „Websuche-Vorschläge“ (Standard an; prefs „search“/„web“). */
    object WebSuggestions {
        var enabled by androidx.compose.runtime.mutableStateOf(true)
        fun load(c: Context) { enabled = c.getSharedPreferences("search", Context.MODE_PRIVATE).getBoolean("web", true) }
        fun set(c: Context, on: Boolean) { enabled = on; c.getSharedPreferences("search", Context.MODE_PRIVATE).edit().putBoolean("web", on).apply() }
    }

    /** Vorschläge der Google-Suche zum getippten Text (blockierend, im IO-Thread aufrufen). */
    fun webSuggestions(query: String, max: Int = 4): List<String> {
        val q = query.trim()
        if (q.isEmpty() || !WebSuggestions.enabled) return emptyList()
        return runCatching {
            val lang = Locale.getDefault().language
            val url = URL("https://suggestqueries.google.com/complete/search?client=firefox&hl=$lang&q=" + URLEncoder.encode(q, "UTF-8"))
            val c = url.openConnection() as HttpURLConnection
            c.connectTimeout = 3000; c.readTimeout = 3000
            val body = c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val arr = JSONArray(body).getJSONArray(1)
            (0 until arr.length()).map { arr.getString(it) }
                .filter { !it.equals(q, ignoreCase = true) }.take(max - 1).let { listOf(q) + it }
        }.getOrDefault(listOf(q))
    }

    fun hasContacts(c: Context) = ContextCompat.checkSelfPermission(c, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    fun contacts(c: Context, query: String, max: Int = 3): List<Contact> {
        val q = query.trim()
        if (q.length < 2 || !hasContacts(c)) return emptyList()
        val out = LinkedHashMap<Long, Contact>()
        runCatching {
            val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_FILTER_URI, Uri.encode(q))
            c.contentResolver.query(uri, arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.LOOKUP_KEY,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY, ContactsContract.Contacts.PHOTO_THUMBNAIL_URI, ContactsContract.Contacts.HAS_PHONE_NUMBER),
                null, null, null)?.use { cur ->
                while (cur.moveToNext() && out.size < max) {
                    val id = cur.getLong(0)
                    val number = if (cur.getInt(4) > 0) firstNumber(c, id) else null
                    out[id] = Contact(id, cur.getString(1) ?: "", cur.getString(2) ?: "", number, cur.getString(3)?.let(Uri::parse))
                }
            }
        }
        return out.values.toList()
    }

    private fun firstNumber(c: Context, id: Long): String? = runCatching {
        c.contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?", arrayOf(id.toString()), null)?.use { if (it.moveToFirst()) it.getString(0) else null }
    }.getOrNull()

    fun openContact(c: Context, k: Contact) = start(c, Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.getLookupUri(k.id, k.lookup)))
    fun call(c: Context, number: String) = start(c, Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null)))

    /** Im Standard-Browser mit Google suchen. */
    fun searchWeb(c: Context, text: String) =
        start(c, Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + URLEncoder.encode(text.trim(), "UTF-8"))))

    /** Paket des Standard-Browsers (für das Symbol in der Zeile). */
    fun browserPackage(c: Context): String? =
        c.packageManager.resolveActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")), PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName?.takeIf { it != "android" }

    private fun start(c: Context, i: Intent) { runCatching { c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }

    // ── Rechner ───────────────────────────────────────────────────────────────
    /** „12*7“, „(3+4)/2“, „2^10“, „15 % von 80“ → Ergebnis-Text; null, wenn es keine Rechnung ist. */
    fun calculate(input: String): String? {
        var s = input.trim().replace('×', '*').replace('÷', '/').replace(',', '.').replace(" ", "")
        if (s.isEmpty() || s.none { it in "+-*/^%" } || s.any { !(it.isDigit() || it in "+-*/^%().") }) return null
        if (!s.any { it.isDigit() }) return null
        s = s.replace(Regex("""(\d+(?:\.\d+)?)%"""), "($1/100)")
        val v = runCatching { Parser(s).parse() }.getOrNull() ?: return null
        if (v.isNaN() || v.isInfinite()) return null
        val r = Math.round(v * 1e10) / 1e10
        return if (r == Math.floor(r) && kotlin.math.abs(r) < 1e15) String.format(Locale.getDefault(), "%,d", r.toLong())
        else java.text.NumberFormat.getInstance().apply { maximumFractionDigits = 8 }.format(r)
    }

    private class Parser(val s: String) {
        var i = 0
        fun parse(): Double { val v = expr(); require(i == s.length); return v }
        fun expr(): Double { var v = term(); while (i < s.length && s[i] in "+-") { val o = s[i++]; val t = term(); v = if (o == '+') v + t else v - t }; return v }
        fun term(): Double { var v = pow(); while (i < s.length && s[i] in "*/") { val o = s[i++]; val t = pow(); v = if (o == '*') v * t else v / t }; return v }
        fun pow(): Double { val b = unary(); return if (i < s.length && s[i] == '^') { i++; Math.pow(b, pow()) } else b }
        fun unary(): Double = if (i < s.length && s[i] == '-') { i++; -unary() } else if (i < s.length && s[i] == '+') { i++; unary() } else atom()
        fun atom(): Double {
            if (i < s.length && s[i] == '(') { i++; val v = expr(); require(i < s.length && s[i] == ')'); i++; return v }
            val st = i; while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
            require(i > st); return s.substring(st, i).toDouble()
        }
    }
}
