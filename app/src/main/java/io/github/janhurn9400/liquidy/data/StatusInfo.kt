package io.github.janhurn9400.liquidy.data

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.provider.Settings
import android.telephony.TelephonyManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Einstellung: Android-Statusleiste im Launcher ausblenden und eigene (iOS bzw. Duo) zeichnen. Abgefragt im Willkommen. */
object OwnStatusBar {
    private const val PREFS = "statusbar"
    var enabled by mutableStateOf(false)
        private set

    fun load(c: Context) { enabled = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("own", false) }
    fun set(c: Context, on: Boolean) {
        enabled = on
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("own", on).apply()
    }
}

/** Werte der Statusleiste aus echten Android-Quellen. [wifi]/[cell]: −1 = keins, sonst 0…3 bzw. 0…4. */
data class StatusInfo(
    val time: String = "",
    val battery: Float = 1f,
    val charging: Boolean = false,
    val wifi: Int = -1,
    val cell: Int = -1,
    val airplane: Boolean = false,
)

/**
 * Liest die Statuswerte alle paar Sekunden (Uhr minutengenau): Akku per Sticky-Broadcast, WLAN-Stärke über die
 * NetworkCapabilities des aktiven Netzes (RSSI), Mobilfunk über TelephonyManager.getSignalStrength (laut AOSP ohne
 * Berechtigung, nur Telefonie-Hardware), Flugmodus aus Settings.Global.
 */
@Composable
fun rememberStatusInfo(): State<StatusInfo> {
    val context = LocalContext.current
    return produceState(StatusInfo(time = timeNow(context))) {
        while (true) {
            value = withContext(Dispatchers.IO) { read(context) }
            delay(STATUS_POLL_MS)
        }
    }
}

private fun timeNow(c: Context): String =
    android.text.format.DateFormat.getTimeFormat(c).format(java.util.Date()).replace(" AM", "").replace(" PM", "")

private fun read(c: Context): StatusInfo {
    val bat = c.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val level = bat?.let { it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1).toFloat() / it.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1) } ?: 1f
    val status = bat?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
    val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    val airplane = Settings.Global.getInt(c.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1
    val wifi = runCatching {
        val cm = c.getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return@runCatching -1
        if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return@runCatching -1
        val wm = c.getSystemService(WifiManager::class.java)
        val max = wm.maxSignalLevel.coerceAtLeast(1)
        val lvl = wm.calculateSignalLevel(caps.signalStrength)
        ((lvl.toFloat() / max) * 3f).let { kotlin.math.round(it).toInt() }.coerceIn(0, 3)
    }.getOrDefault(-1)
    val cell = if (airplane) -1 else runCatching {
        val tm = c.getSystemService(TelephonyManager::class.java)
        if (tm.simState != TelephonyManager.SIM_STATE_READY) -1 else tm.signalStrength?.level?.coerceIn(0, 4) ?: -1
    }.getOrDefault(-1)
    return StatusInfo(timeNow(c), level.coerceIn(0f, 1f), charging, wifi, cell, airplane)
}

private const val STATUS_POLL_MS = 5_000L
