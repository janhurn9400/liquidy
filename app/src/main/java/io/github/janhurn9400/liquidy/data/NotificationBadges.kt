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
 * Zähler für die roten iOS-Badges aus den echten Android-Mitteilungen (wie Launcher3s „Notification dots“, nur mit Zahl).
 * Braucht Mitteilungszugriff (Einstellungen → Apps mit Zugriff auf Benachrichtigungen); ohne Zugriff gibt es keine Badges.
 *
 * Zählung je App + Nutzer: Summe aus Notification.number (falls die App eine Zahl setzt) bzw. 1 je Mitteilung;
 * laufende (Musik, Navigation, Vordergrunddienste) und Gruppen-Zusammenfassungen zählen nicht.
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
        // nur Änderungen schreiben (Compose liest die Map)
        counts.keys.filter { it !in next }.forEach { counts.remove(it) }
        next.forEach { (k, v) -> if (counts[k] != v) counts[k] = v }
    }

    companion object {
        /** Paket + Nutzer → Anzahl. */
        val counts = mutableStateMapOf<String, Int>()

        fun key(pkg: String, user: UserHandle) = "$pkg/${user.hashCode()}"
        fun countFor(app: AppEntry): Int = counts[key(app.component.packageName, app.user)] ?: 0

        fun hasAccess(c: Context): Boolean {
            val flat = Settings.Secure.getString(c.contentResolver, "enabled_notification_listeners") ?: return false
            val me = ComponentName(c, NotificationBadges::class.java)
            return flat.split(':').any { ComponentName.unflattenFromString(it) == me }
        }

        /** Öffnet die Systemseite, auf der der Zugriff für genau diesen Dienst erteilt wird. */
        fun requestAccess(c: Context) {
            val detail = android.content.Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, ComponentName(c, NotificationBadges::class.java).flattenToString())
            runCatching { c.startActivity(detail) }.onFailure {
                runCatching { c.startActivity(android.content.Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
            }
        }
    }
}
