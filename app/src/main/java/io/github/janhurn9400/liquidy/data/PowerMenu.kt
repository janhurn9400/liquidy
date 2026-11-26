package io.github.janhurn9400.liquidy.data

import android.content.Context
import android.content.Intent

/**
 * Opens Android's power menu (global actions) from the Control Center's power button:
 * 1. root or Shizuku: IWindowManager.showGlobalActions() through `service call window <code>` — its binder code
 *    differs per Android release and is read from the framework (hidden API, reached via HiddenApiBypass);
 * 2. else the optional accessibility service ([PowerButtonService], GLOBAL_ACTION_POWER_DIALOG — the one thing it is
 *    used for);
 * 3. else the accessibility settings so the user can turn that service on (a short note says why).
 * (Long-pressing power through `input keyevent` opens the assistant on Pixel defaults, so it's only the last resort.)
 */
object PowerMenu {
    // hidden API on purpose (exempted through HiddenApiBypass); null → the fallbacks below
    @get:android.annotation.SuppressLint("BlockedPrivateApi")
    private val code: Int? by lazy @android.annotation.SuppressLint("BlockedPrivateApi") {
        runCatching {
            org.lsposed.hiddenapibypass.HiddenApiBypass.addHiddenApiExemptions("Landroid/view/IWindowManager")
            Class.forName("android.view.IWindowManager\$Stub").getDeclaredField("TRANSACTION_showGlobalActions").apply { isAccessible = true }.getInt(null)
        }.getOrNull()
    }

    fun show(context: Context) {
        val c = code
        if (Privileged.run(if (c != null) "service call window $c" else "input keyevent --longpress 26")) return
        if (PowerButtonService.showPowerMenu()) return
        android.widget.Toast.makeText(context, io.github.janhurn9400.liquidy.R.string.cc_power_needs_access, android.widget.Toast.LENGTH_LONG).show()
        runCatching { context.startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}
