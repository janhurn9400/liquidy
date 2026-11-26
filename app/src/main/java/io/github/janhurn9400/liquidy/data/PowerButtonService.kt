package io.github.janhurn9400.liquidy.data

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.view.accessibility.AccessibilityEvent

/**
 * Optional accessibility service, used for exactly one thing: opening Android's power menu from the Control Center's
 * power button on phones without root or Shizuku (GLOBAL_ACTION_POWER_DIALOG). It reads no screen content and
 * receives no events (see res/xml/power_button_service.xml).
 */
class PowerButtonService : AccessibilityService() {
    override fun onServiceConnected() { instance = this }
    override fun onUnbind(intent: android.content.Intent?): Boolean { instance = null; return super.onUnbind(intent) }
    override fun onDestroy() { instance = null; super.onDestroy() }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    companion object {
        @Volatile private var instance: PowerButtonService? = null

        /** true when the service is on and opened the menu. */
        fun showPowerMenu(): Boolean = instance?.performGlobalAction(GLOBAL_ACTION_POWER_DIALOG) == true

        /** Whether the user turned the service on (Settings › Accessibility). */
        fun enabled(c: Context): Boolean {
            val list = android.provider.Settings.Secure.getString(c.contentResolver, android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
            return list.split(':').any { it.equals("${c.packageName}/${PowerButtonService::class.java.name}", true) ||
                it.equals("${c.packageName}/.data.PowerButtonService", true) }
        }
    }
}
