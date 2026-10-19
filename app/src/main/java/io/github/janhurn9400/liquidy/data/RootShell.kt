package io.github.janhurn9400.liquidy.data

import androidx.compose.runtime.mutableStateOf
import java.util.concurrent.Executors

/**
 * One persistent `su` shell. Commands are written to its stdin, so a toggle costs no new process (instant, like a
 * system tile). Root is only asked for when the Control Center is first used — Magisk shows its prompt then.
 */
object RootShell {
    /** null = not checked yet, true/false after the first [probe]. */
    val available = mutableStateOf<Boolean?>(null)

    private val exec = Executors.newSingleThreadExecutor { r -> Thread(r, "RootShell").apply { isDaemon = true } }
    @Volatile private var proc: Process? = null

    /** Checks root once in the background; [onResult] runs on the shell thread. */
    fun probe(onResult: (Boolean) -> Unit = {}) = exec.execute { onResult(ensure()) }

    /** Runs [cmd] as root (fire and forget, output discarded). Does nothing without root. */
    fun run(cmd: String) = exec.execute {
        if (!ensure()) return@execute
        runCatching { proc!!.outputStream.apply { write("$cmd >/dev/null 2>&1\n".toByteArray()); flush() } }
            .onFailure { proc = null }
    }

    private fun ensure(): Boolean {
        proc?.let { if (it.isAlive) return true }
        if (available.value == false) return false
        val ok = runCatching {
            val p = Runtime.getRuntime().exec(arrayOf("su"))
            p.outputStream.write("id -u\n".toByteArray()); p.outputStream.flush()
            val line = StringBuilder()
            while (true) { val c = p.inputStream.read(); if (c < 0 || c == '\n'.code) break; line.append(c.toChar()) }
            (line.toString().trim() == "0").also { if (it) proc = p else p.destroy() }
        }.getOrDefault(false)
        available.value = ok
        return ok
    }
}
