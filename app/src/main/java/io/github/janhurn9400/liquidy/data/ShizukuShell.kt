package io.github.janhurn9400.liquidy.data

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.runtime.mutableStateOf
import rikka.shizuku.Shizuku
import java.util.concurrent.Executors

/**
 * Shizuku (shizuku.rikka.app): ADB-level rights without root — the way current Android control-center and tile apps
 * switch Wi-Fi, mobile data, Bluetooth, airplane mode … on unrooted phones. Like [RootShell], one persistent `sh`
 * (here started by the Shizuku server as the shell user) takes the commands, so a toggle is instant.
 * Without root, Shizuku has to be started again after every reboot (wireless debugging); [state] tells the UI.
 */
object ShizukuShell {
    enum class State { NotInstalled, NotRunning, NoPermission, Ready }

    const val PACKAGE = "moe.shizuku.privileged.api"
    private const val REQUEST = 4711

    val state = mutableStateOf(State.NotInstalled)
    val ready get() = state.value == State.Ready

    private val exec = Executors.newSingleThreadExecutor { r -> Thread(r, "ShizukuShell").apply { isDaemon = true } }
    @Volatile private var proc: Process? = null
    private var appContext: Context? = null
    private var listening = false

    /** Call once at start: follows the Shizuku server coming and going and the user's answer to the permission prompt. */
    fun init(context: Context) {
        appContext = context.applicationContext
        if (!listening) {
            listening = true
            runCatching {
                Shizuku.addBinderReceivedListenerSticky { refresh() }
                Shizuku.addBinderDeadListener { proc = null; refresh() }
                Shizuku.addRequestPermissionResultListener { _, _ -> refresh() }
            }
        }
        refresh()
    }

    fun refresh() {
        val c = appContext ?: return
        state.value = when {
            !installed(c) -> State.NotInstalled
            !runCatching { Shizuku.pingBinder() }.getOrDefault(false) -> State.NotRunning
            runCatching { Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }.getOrDefault(false) -> State.Ready
            else -> State.NoPermission
        }
    }

    private fun installed(c: Context) = runCatching { c.packageManager.getPackageInfo(PACKAGE, 0); true }.getOrDefault(false)

    /** Asks for Shizuku access (its own dialog); false when the server isn't running. */
    fun requestPermission(): Boolean = runCatching {
        if (!Shizuku.pingBinder()) return false
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) Shizuku.requestPermission(REQUEST)
        true
    }.getOrDefault(false)

    /** Runs [cmd] with shell rights (fire and forget). false when Shizuku isn't ready. */
    fun run(cmd: String): Boolean {
        if (!ready) return false
        exec.execute {
            if (!ensure()) return@execute
            runCatching { proc!!.outputStream.apply { write("$cmd >/dev/null 2>&1\n".toByteArray()); flush() } }
                .onFailure { proc = null }
        }
        return true
    }

    private fun ensure(): Boolean {
        proc?.let { if (runCatching { it.exitValue(); false }.getOrDefault(true)) return true }
        // Shizuku.newProcess is no longer public in API 13 but still there (the documented successor, UserService,
        // would need a second APK-internal process just to run shell commands)
        proc = runCatching {
            val m = Shizuku::class.java.getDeclaredMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java)
            m.isAccessible = true
            m.invoke(null, arrayOf("sh"), null, null) as Process
        }.getOrNull()
        return proc != null
    }
}

/**
 * Who may switch system settings for the Control Center: root (Magisk), else Shizuku, else nobody — then the
 * switches open Android's own panel or settings page instead (like every unrooted control-center app has to).
 */
object Privileged {
    enum class Via { Root, Shizuku, None }

    val via: Via get() = when {
        RootShell.available.value == true -> Via.Root
        ShizukuShell.ready -> Via.Shizuku
        else -> Via.None
    }

    /** Runs [cmd] with the best available rights; false when there are none. */
    fun run(cmd: String): Boolean = when (via) {
        Via.Root -> { RootShell.run(cmd); true }
        Via.Shizuku -> ShizukuShell.run(cmd)
        Via.None -> false
    }
}
