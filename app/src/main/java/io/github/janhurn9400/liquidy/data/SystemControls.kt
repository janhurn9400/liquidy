package io.github.janhurn9400.liquidy.data

import android.content.ComponentName
import android.content.Context
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
 * (torch, volume, media) and otherwise through [RootShell] (Wi-Fi, Bluetooth, mobile data, airplane mode, DND, silent,
 * dark mode, battery saver). Brightness and rotation lock are written with WRITE_SETTINGS, which root grants once.
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
    /** Slider positions 0…1 (brightness on Android's perceptual curve, like the system slider). */
    val brightness = mutableFloatStateOf(0.5f)
    val volume = mutableFloatStateOf(0.5f)
    val batteryPercent = mutableStateOf(0)
    val charging = mutableStateOf(false)
    val carrier = mutableStateOf("")

    class Media(val title: String, val artist: String, val art: Bitmap?, val playing: Boolean, val controller: MediaController)
    val media = mutableStateOf<Media?>(null)

    private var torchId: String? = null
    private var torchCallback: CameraManager.TorchCallback? = null
    private var grantedSettings = false

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

    /** Lets the app write system settings (brightness, rotation) and change the ringer without asking — once per start. */
    fun grantWithRoot(c: Context) {
        if (grantedSettings) return
        grantedSettings = true
        RootShell.run("appops set ${c.packageName} WRITE_SETTINGS allow")
        RootShell.run("cmd notification allow_dnd ${c.packageName}")
    }

    private fun recheck(c: Context) {
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ refresh(c) }, RECHECK_MS)
    }

    fun toggleWifi(c: Context) { wifi.value = !wifi.value; RootShell.run("svc wifi ${if (wifi.value) "enable" else "disable"}"); recheck(c) }
    fun toggleBluetooth(c: Context) {
        bluetooth.value = !bluetooth.value
        val on = if (bluetooth.value) "enable" else "disable"
        RootShell.run("cmd bluetooth_manager $on || svc bluetooth $on"); recheck(c)
    }
    fun toggleMobileData(c: Context) { mobileData.value = !mobileData.value; RootShell.run("svc data ${if (mobileData.value) "enable" else "disable"}"); recheck(c) }
    fun toggleAirplane(c: Context) {
        airplane.value = !airplane.value
        RootShell.run("cmd connectivity airplane-mode ${if (airplane.value) "enable" else "disable"}"); recheck(c)
    }
    fun toggleDnd(c: Context) { dnd.value = !dnd.value; RootShell.run("cmd notification set_dnd ${if (dnd.value) "priority" else "off"}"); recheck(c) }
    fun toggleSilent(c: Context) {
        silent.value = !silent.value
        val am = c.getSystemService(AudioManager::class.java)
        val mode = if (silent.value) AudioManager.RINGER_MODE_VIBRATE else AudioManager.RINGER_MODE_NORMAL
        if (runCatching { am?.ringerMode = mode }.isFailure) RootShell.run("cmd audio set-ringer-mode ${if (silent.value) "VIBRATE" else "NORMAL"}")
        recheck(c)
    }
    fun toggleDarkMode(c: Context) { darkMode.value = !darkMode.value; RootShell.run("cmd uimode night ${if (darkMode.value) "yes" else "no"}") }
    fun toggleBatterySaver(c: Context) { batterySaver.value = !batterySaver.value; RootShell.run("cmd power set-mode ${if (batterySaver.value) 1 else 0}"); recheck(c) }

    fun toggleTorch(c: Context) {
        val id = torchId ?: return
        val on = !torch.value
        torch.value = on
        runCatching { c.getSystemService(CameraManager::class.java)?.setTorchMode(id, on) }.onFailure { torch.value = !on }
    }

    fun toggleRotationLock(c: Context) {
        rotationLock.value = !rotationLock.value
        val v = if (rotationLock.value) 0 else 1
        if (runCatching { Settings.System.putInt(c.contentResolver, Settings.System.ACCELEROMETER_ROTATION, v) }.isFailure)
            RootShell.run("settings put system accelerometer_rotation $v")
    }

    /** Slider 0…1 → screen brightness. Manual mode while dragging, like a fixed brightness on the slider. */
    fun setBrightness(c: Context, slider: Float) {
        brightness.floatValue = slider.coerceIn(0f, 1f)
        val v = (1 + sliderToLinear(brightness.floatValue) * 254).toInt().coerceIn(1, 255)
        val cr = c.contentResolver
        val ok = runCatching {
            Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, v)
        }.isSuccess
        if (!ok) RootShell.run("settings put system screen_brightness_mode 0; settings put system screen_brightness $v")
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
        if (!ok) RootShell.run("cmd statusbar expand-notifications")
    }

    private const val RECHECK_MS = 1500L
}
