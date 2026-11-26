package io.github.janhurn9400.liquidy.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.provider.Settings
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * State and switches behind the Control Center. Read with public APIs; switched with public APIs where Android allows it
 * (torch, volume, media, DND with its access, silent; brightness and rotation lock with WRITE_SETTINGS) and otherwise
 * through [Privileged] — root, else Shizuku (Wi-Fi, Bluetooth, mobile data, airplane mode, dark mode, battery saver …).
 * Without either, a switch opens Android's own panel or settings page for it.
 * Toggles flip the shown state at once and re-read the real state shortly after.
 */
object SystemControls {
    val wifi = mutableStateOf(false)
    val bluetooth = mutableStateOf(false)
    val mobileData = mutableStateOf(false)
    val airplane = mutableStateOf(false)
    val torch = mutableStateOf(false)
    val rotationLock = mutableStateOf(false)
    val dnd = mutableStateOf(false)
    val silent = mutableStateOf(false)
    val darkMode = mutableStateOf(false)
    val batterySaver = mutableStateOf(false)
    val location = mutableStateOf(false)
    val nfc = mutableStateOf(false)
    /** null = the phone has no NFC. */
    val hasNfc = mutableStateOf(true)
    val dataSaver = mutableStateOf(false)
    val hotspot = mutableStateOf(false)
    val nightLight = mutableStateOf(false)
    val autoBrightness = mutableStateOf(false)
    val wifiConnected = mutableStateOf(false)
    /** Slider positions 0…1 (brightness on Android's perceptual curve, like the system slider). */
    val brightness = mutableFloatStateOf(0.5f)
    val volume = mutableFloatStateOf(0.5f)
    val batteryPercent = mutableStateOf(0)
    val charging = mutableStateOf(false)
    val carrier = mutableStateOf("")

    class Media(val title: String, val artist: String, val art: Bitmap?, val playing: Boolean, val controller: MediaController,
                /** ms; position is extrapolated from the playback state while playing. */
                val duration: Long, val position: Long, val positionAt: Long, val speed: Float)
    val media = mutableStateOf<Media?>(null)

    private var torchId: String? = null
    private var torchCallback: CameraManager.TorchCallback? = null
    private var grantedSettings = false
    private var askedWrite = false

    /** Every read on its own: a ROM that guards one of them must not take the others (or the launcher) down. */
    fun refresh(c: Context) {
        val cr = c.contentResolver
        fun read(block: () -> Unit) { runCatching(block) }
        read { wifi.value = Settings.Global.getInt(cr, "wifi_on", 0) != 0 }
        read { bluetooth.value = Settings.Global.getInt(cr, "bluetooth_on", 0) != 0 }
        read { mobileData.value = Settings.Global.getInt(cr, "mobile_data", 0) != 0 }
        read { airplane.value = Settings.Global.getInt(cr, Settings.Global.AIRPLANE_MODE_ON, 0) != 0 }
        read { rotationLock.value = Settings.System.getInt(cr, Settings.System.ACCELEROMETER_ROTATION, 1) == 0 }
        read {
            val nm = c.getSystemService(android.app.NotificationManager::class.java)
            dnd.value = (nm?.currentInterruptionFilter ?: 1) > android.app.NotificationManager.INTERRUPTION_FILTER_ALL
        }
        read {
            val am = c.getSystemService(AudioManager::class.java) ?: return@read
            silent.value = am.ringerMode != AudioManager.RINGER_MODE_NORMAL
            volume.floatValue = am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        }
        read { darkMode.value = (c.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES }
        read { batterySaver.value = c.getSystemService(android.os.PowerManager::class.java)?.isPowerSaveMode == true }
        read {
            val b = Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS, 128)
            brightness.floatValue = linearToSlider(((b - 1) / 254f).coerceIn(0f, 1f))
        }
        read {
            // sticky broadcast: works on every ROM (BATTERY_PROPERTY_CAPACITY returned 0 on LineageOS renoir)
            val i = c.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)) ?: return@read
            val level = i.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1); val scale = i.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100)
            if (level >= 0) batteryPercent.value = level * 100 / scale.coerceAtLeast(1)
            charging.value = i.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, 0) != 0
        }
        read { location.value = Settings.Secure.getInt(cr, Settings.Secure.LOCATION_MODE, 0) != 0 }
        read {
            val a = android.nfc.NfcAdapter.getDefaultAdapter(c)
            hasNfc.value = a != null; nfc.value = a?.isEnabled == true
        }
        read {
            val cm = c.getSystemService(android.net.ConnectivityManager::class.java)
            dataSaver.value = cm?.restrictBackgroundStatus == android.net.ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED
            wifiConnected.value = cm?.getNetworkCapabilities(cm.activeNetwork)?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true
        }
        read {
            val wm = c.getSystemService(android.net.wifi.WifiManager::class.java)
            hotspot.value = wm?.javaClass?.getMethod("isWifiApEnabled")?.invoke(wm) == true
        }
        read { nightLight.value = Settings.Secure.getInt(cr, "night_display_activated", 0) != 0 }
        read { autoBrightness.value = Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, 0) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC }
        read { carrier.value = c.getSystemService(android.telephony.TelephonyManager::class.java)?.networkOperatorName.orEmpty() }
        read { refreshMedia(c) }
        read { watchTorch(c) }
    }

    fun refreshMedia(c: Context) {
        val ctrl = runCatching {
            c.getSystemService(MediaSessionManager::class.java)
                ?.getActiveSessions(ComponentName(c, NotificationBadges::class.java))?.firstOrNull()
        }.getOrNull() // no notification access → no media module content
        if (ctrl == null) { media.value = null; return }
        val md = ctrl.metadata
        media.value = Media(
            title = md?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty(),
            artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST).orEmpty(),
            art = md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART),
            playing = ctrl.playbackState?.state == PlaybackState.STATE_PLAYING,
            controller = ctrl,
            duration = md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L,
            position = ctrl.playbackState?.position ?: 0L,
            positionAt = ctrl.playbackState?.lastPositionUpdateTime ?: 0L,
            speed = ctrl.playbackState?.playbackSpeed ?: 1f,
        )
    }

    private fun watchTorch(c: Context) {
        if (torchCallback != null) return
        val cm = c.getSystemService(CameraManager::class.java) ?: return
        torchId = runCatching { cm.cameraIdList.firstOrNull { cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true } }.getOrNull()
        val cb = object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(cameraId: String, enabled: Boolean) { if (cameraId == torchId) torch.value = enabled }
        }
        cm.registerTorchCallback(cb, android.os.Handler(android.os.Looper.getMainLooper()))
        torchCallback = cb
    }

    /**
     * With root or Shizuku: lets the app write system settings (brightness, rotation) and DND without asking — once
     * per start, so the public APIs below just work.
     */
    fun grantPrivileged(c: Context) {
        if (grantedSettings || Privileged.via == Privileged.Via.None) return
        grantedSettings = true
        Privileged.run("appops set ${c.packageName} WRITE_SETTINGS allow")
        Privileged.run("cmd notification allow_dnd ${c.packageName}")
    }

    private fun recheck(c: Context) {
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ refresh(c) }, RECHECK_MS)
    }

    /**
     * Flips [state] with [cmd] (root or Shizuku). Without either, Android lets no app switch this — then [fallback]
     * opens Android's own panel or settings page for it (what every unrooted control-center app does).
     */
    private fun flip(c: Context, state: androidx.compose.runtime.MutableState<Boolean>, fallback: String?, cmd: (Boolean) -> String) {
        val on = !state.value
        if (Privileged.run(cmd(on))) { state.value = on; recheck(c) } else openSystem(c, fallback)
    }

    /** Opens a system panel/settings action, the first that exists. */
    fun openSystem(c: Context, vararg actions: String?) {
        for (a in actions) {
            if (a == null) continue
            val i = Intent(a).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (runCatching { c.startActivity(i) }.isSuccess) return
        }
    }

    /** Settings › Modify system settings for this app (brightness, rotation lock without root). */
    private fun askWriteSettings(c: Context) = runCatching {
        c.startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, android.net.Uri.fromParts("package", c.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun toggleWifi(c: Context) = flip(c, wifi, Settings.Panel.ACTION_WIFI) { "svc wifi ${if (it) "enable" else "disable"}" }
    fun toggleBluetooth(c: Context) = flip(c, bluetooth, Settings.ACTION_BLUETOOTH_SETTINGS) {
        val on = if (it) "enable" else "disable"; "cmd bluetooth_manager $on || svc bluetooth $on"
    }
    fun toggleMobileData(c: Context) = flip(c, mobileData, Settings.Panel.ACTION_INTERNET_CONNECTIVITY) { "svc data ${if (it) "enable" else "disable"}" }
    fun toggleAirplane(c: Context) = flip(c, airplane, Settings.ACTION_AIRPLANE_MODE_SETTINGS) { "cmd connectivity airplane-mode ${if (it) "enable" else "disable"}" }
    fun toggleDarkMode(c: Context) = flip(c, darkMode, ACTION_DARK_THEME) { "cmd uimode night ${if (it) "yes" else "no"}" }
    fun toggleBatterySaver(c: Context) = flip(c, batterySaver, Settings.ACTION_BATTERY_SAVER_SETTINGS) { "cmd power set-mode ${if (it) 1 else 0}" }
    fun toggleLocation(c: Context) = flip(c, location, Settings.ACTION_LOCATION_SOURCE_SETTINGS) { "cmd location set-location-enabled $it" }
    fun toggleNfc(c: Context) { if (hasNfc.value) flip(c, nfc, Settings.Panel.ACTION_NFC) { "svc nfc ${if (it) "enable" else "disable"}" } }
    fun toggleDataSaver(c: Context) = flip(c, dataSaver, ACTION_DATA_SAVER) { "cmd netpolicy set restrict-background $it" }
    fun toggleNightLight(c: Context) = flip(c, nightLight, ACTION_NIGHT_LIGHT) { "settings put secure night_display_activated ${if (it) 1 else 0}" }

    /** DND: public API with "Do Not Disturb" access (the user grants it once); else root/Shizuku; else that settings page. */
    fun toggleDnd(c: Context) {
        val on = !dnd.value
        val nm = c.getSystemService(android.app.NotificationManager::class.java)
        val ok = nm?.isNotificationPolicyAccessGranted == true && runCatching {
            nm.setInterruptionFilter(if (on) android.app.NotificationManager.INTERRUPTION_FILTER_PRIORITY else android.app.NotificationManager.INTERRUPTION_FILTER_ALL)
        }.isSuccess
        if (ok || Privileged.run("cmd notification set_dnd ${if (on) "priority" else "off"}")) { dnd.value = on; recheck(c) }
        else openSystem(c, Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
    }

    fun toggleSilent(c: Context) {
        val on = !silent.value
        val am = c.getSystemService(AudioManager::class.java)
        val mode = if (on) AudioManager.RINGER_MODE_VIBRATE else AudioManager.RINGER_MODE_NORMAL
        val ok = runCatching { am!!.ringerMode = mode }.isSuccess ||
            Privileged.run("cmd audio set-ringer-mode ${if (on) "VIBRATE" else "NORMAL"}")
        if (ok) { silent.value = on; recheck(c) } else openSystem(c, Settings.ACTION_SOUND_SETTINGS)
    }

    /** Writes a system setting: directly (WRITE_SETTINGS), else root/Shizuku, else asks for the permission. */
    private fun putSystem(c: Context, key: String, v: Int): Boolean =
        runCatching { Settings.System.putInt(c.contentResolver, key, v) }.isSuccess ||
            Privileged.run("settings put system $key $v") ||
            run { askWriteSettings(c); false }

    fun toggleAutoBrightness(c: Context) {
        val on = !autoBrightness.value
        val v = if (on) Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC else Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
        if (putSystem(c, Settings.System.SCREEN_BRIGHTNESS_MODE, v)) autoBrightness.value = on
    }

    fun toggleTorch(c: Context) {
        val id = torchId ?: return
        val on = !torch.value
        torch.value = on
        runCatching { c.getSystemService(CameraManager::class.java)?.setTorchMode(id, on) }.onFailure { torch.value = !on }
    }

    fun toggleRotationLock(c: Context) {
        val on = !rotationLock.value
        if (putSystem(c, Settings.System.ACCELEROMETER_ROTATION, if (on) 0 else 1)) rotationLock.value = on
    }

    /** Slider 0…1 → screen brightness. Manual mode while dragging, like a fixed brightness on the slider. */
    fun setBrightness(c: Context, slider: Float) {
        brightness.floatValue = slider.coerceIn(0f, 1f)
        val v = (1 + sliderToLinear(brightness.floatValue) * 254).toInt().coerceIn(1, 255)
        val cr = c.contentResolver
        val ok = runCatching {
            Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            autoBrightness.value = false
            Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, v)
        }.isSuccess
        if (!ok && !Privileged.run("settings put system screen_brightness_mode 0; settings put system screen_brightness $v") && !askedWrite) {
            askedWrite = true; askWriteSettings(c) // once per start — the slider is dragged many times a second
        }
    }

    fun setVolume(c: Context, v: Float) {
        volume.floatValue = v.coerceIn(0f, 1f)
        val am = c.getSystemService(AudioManager::class.java) ?: return
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        runCatching { am.setStreamVolume(AudioManager.STREAM_MUSIC, (volume.floatValue * max).toInt().coerceIn(0, max), 0) }
    }

    // Android's brightness slider curve (BrightnessUtils, HLG): perceptual slider ↔ linear brightness
    private const val R = 0.5f; private const val A = 0.17883277f; private const val B = 0.28466892f; private const val C = 0.55991073f
    private fun sliderToLinear(s: Float): Float = (if (s <= R) (s / R) * (s / R) else exp((s - C) / A) + B) / 12f
    private fun linearToSlider(l: Float): Float { val v = l * 12f; return if (v <= 1f) R * sqrt(v) else A * ln(v - B) + C }

    /** Android's notification shade (top-left pull with our own status bar, like iOS's Notification Center side). */
    fun expandNotifications(c: Context) {
        val ok = runCatching {
            val sbm = c.getSystemService("statusbar")!!
            sbm.javaClass.getMethod("expandNotificationsPanel").invoke(sbm)
        }.isSuccess
        if (!ok) Privileged.run("cmd statusbar expand-notifications")
    }

    private const val RECHECK_MS = 1500L
    // settings pages without a public constant (all exist in AOSP Settings)
    private const val ACTION_DARK_THEME = "android.settings.DARK_THEME_SETTINGS"
    private const val ACTION_DATA_SAVER = "android.settings.DATA_SAVER_SETTINGS"
    private const val ACTION_NIGHT_LIGHT = "android.settings.NIGHT_DISPLAY_SETTINGS"
}
