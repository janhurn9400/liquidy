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

/** Setting: hide the Android status bar in the launcher and draw our own (iOS or Duo). Asked in the welcome. */
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

/** Status bar values from real Android sources. [wifi]/[cell]: −1 = none, otherwise 0…3 or 0…4. */
data class StatusInfo(
    val time: String = "",
    val battery: Float = 1f,
    val charging: Boolean = false,
    val wifi: Int = -1,
    val cell: Int = -1,
    val airplane: Boolean = false,
)

/**
 * Reads the status values every few seconds (clock to the minute): battery via sticky broadcast, Wi-Fi strength via the
 * NetworkCapabilities of the active network (RSSI), cellular via TelephonyManager.getSignalStrength (according to AOSP without
 * permission, only telephony hardware), airplane mode from Settings.Global.
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
