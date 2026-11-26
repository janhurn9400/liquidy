package io.github.janhurn9400.liquidy.data

/**
 * Opens Android's power menu (global actions) from the Control Center's power button. Long-pressing power via
 * `input keyevent` opens the assistant on Pixel defaults instead, so the root shell calls
 * IWindowManager.showGlobalActions() directly; its binder code differs per Android release and is read from the
 * framework (hidden API — allowed when the Animation Module is installed). Without it: the long press as before.
 */
object PowerMenu {
    private val code: Int? by lazy {
        runCatching { Class.forName("android.view.IWindowManager\$Stub").getDeclaredField("TRANSACTION_showGlobalActions").apply { isAccessible = true }.getInt(null) }
            .getOrNull()
    }

    fun show() {
        val c = code
        RootShell.run(if (c != null) "service call window $c" else "input keyevent --longpress 26")
    }
}
