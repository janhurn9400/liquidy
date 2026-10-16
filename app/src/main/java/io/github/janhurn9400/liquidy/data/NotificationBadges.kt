package io.github.janhurn9400.liquidy.data

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.os.UserHandle
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.runtime.mutableStateMapOf

/**
 * Counters for the red iOS badges from the real Android notifications (like Launcher3's "Notification dots", just with a number).
 * Needs notification access (Settings → apps with notification access); without access there are no badges.
 *
 * Count per app + user: sum of Notification.number (if the app sets a number) or 1 per notification;
 * ongoing ones (music, navigation, foreground services) and group summaries don't count.
 */
class NotificationBadges : NotificationListenerService() {

    override fun onListenerConnected() = recount()
    override fun onListenerDisconnected() { counts.clear() }
    override fun onNotificationPosted(sbn: StatusBarNotification?) = recount()
    override fun onNotificationRemoved(sbn: StatusBarNotification?) = recount()

    private fun recount() {
        val all = runCatching { activeNotifications }.getOrNull() ?: return
        val next = HashMap<String, Int>()
        for (sbn in all) {
            val n = sbn.notification
            if (sbn.isOngoing || n.flags and Notification.FLAG_FOREGROUND_SERVICE != 0) continue
            if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) continue
            val k = key(sbn.packageName, sbn.user)
            next[k] = (next[k] ?: 0) + if (n.number > 0) n.number else 1
        }
        // only write changes (Compose reads the map)
        counts.keys.filter { it !in next }.forEach { counts.remove(it) }
        next.forEach { (k, v) -> if (counts[k] != v) counts[k] = v }
    }

    companion object {
        /** Package + user → count. */
        val counts = mutableStateMapOf<String, Int>()

        fun key(pkg: String, user: UserHandle) = "$pkg/${user.hashCode()}"
        fun countFor(app: AppEntry): Int = counts[key(app.component.packageName, app.user)] ?: 0

        fun hasAccess(c: Context): Boolean {
            val flat = Settings.Secure.getString(c.contentResolver, "enabled_notification_listeners") ?: return false
            val me = ComponentName(c, NotificationBadges::class.java)
            return flat.split(':').any { ComponentName.unflattenFromString(it) == me }
        }

        /** Opens the system page where access is granted for exactly this service. */
        fun requestAccess(c: Context) {
            val detail = android.content.Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, ComponentName(c, NotificationBadges::class.java).flattenToString())
            runCatching { c.startActivity(detail) }.onFailure {
                runCatching { c.startActivity(android.content.Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
            }
        }
    }
}
