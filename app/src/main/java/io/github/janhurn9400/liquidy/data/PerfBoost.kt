package io.github.janhurn9400.liquidy.data

import android.content.Context
import android.os.PerformanceHintManager

/**
 * Asks the system for CPU headroom right before a big animation (Android's Performance Hint API, as Launcher3 does).
 * Vendor ROMs (HyperOS, ColorOS) boost on touch/scene changes themselves; on AOSP-like ROMs (LineageOS) the governor
 * (schedutil) only ramps up after it sees the load — short animations (≈ 0.3 s, e.g. opening an App Library category)
 * then start on low clocks and stutter. No effect where unsupported — on LineageOS 23.2 / Mi 11 Lite (schedutil) no
 * measurable effect (category open 18.5 % → 16–19 % janky, 10-19); kept for kernels with ADPF support (e.g. Pixel).
 */
object PerfBoost {
    private var session: PerformanceHintManager.Session? = null
    private var tried = false

    /** Main thread + RenderThread of this process (the two threads that draw a frame). */
    private fun drawingThreads(): IntArray {
        val main = android.os.Process.myPid()
        val render = runCatching {
            java.io.File("/proc/self/task").listFiles()?.firstOrNull { t ->
                runCatching { java.io.File(t, "comm").readText().trim() == "RenderThread" }.getOrDefault(false)
            }?.name?.toInt()
        }.getOrNull()
        return if (render != null) intArrayOf(main, render) else intArrayOf(main)
    }

    fun init(context: Context, frameNanos: Long) {
        if (tried) return
        tried = true
        target = frameNanos
        session = runCatching {
            context.getSystemService(PerformanceHintManager::class.java)?.createHintSession(drawingThreads(), frameNanos)
        }.getOrNull()
    }
    private var target = 0L

    /** Call at the start of a big animation (category, folder, App Library list, search, unlock). */
    fun kick() {
        // (the explicit CPU_LOAD_UP hint isn't public API) — reporting that the last frames took far longer than the
        // target makes the ADPF governor raise the clocks of these threads right away
        val s = session ?: return
        runCatching { repeat(OVERLOAD_REPORTS) { s.reportActualWorkDuration(target * OVERLOAD_FACTOR) } }
    }

    private const val OVERLOAD_FACTOR = 3L
    private const val OVERLOAD_REPORTS = 2
}
