package io.github.janhurn9400.liquidy.data

import android.content.ComponentName
import android.content.Intent
import android.graphics.RectF
import android.os.Bundle
import android.os.Message
import android.os.UserHandle
import android.util.Log

/**
 * Handshake mit der Gesten-Navigation (AOSP Launcher3 GestureNavContract, seit Android 11 QPR1):
 * Wischt der Nutzer in einer App nach Hause, kommt ein HOME-Intent mit [EXTRA_GESTURE_CONTRACT]. Wir antworten mit der
 * Bildschirmposition des passenden Icons → das System lässt das App-Fenster in dieses Icon fliegen.
 * Die Flugkurve bestimmt das System; wir liefern nur das Ziel (+ optional eine Icon-Surface zum Überblenden).
 */
class GestureNavContract(val component: ComponentName, val user: UserHandle, private val reply: Message) {

    /**
     * @param surface Icon-Fläche, in die das System das Fenster am Ende überblendet (sonst blendet es nur aus).
     * @param onFinish läuft, wenn das System die Animation beendet hat (Original-Icon wieder zeigen, Fläche entfernen).
     */
    fun sendEndPosition(position: RectF, surface: android.view.SurfaceControl? = null, onFinish: (() -> Unit)? = null) {
        val result = Bundle().apply {
            putParcelable(EXTRA_ICON_POSITION, position)
            putParcelable(EXTRA_ICON_SURFACE, surface)
            if (onFinish != null) putParcelable(EXTRA_ON_FINISH_CALLBACK, Message.obtain().apply {
                replyTo = android.os.Messenger(android.os.Handler(android.os.Looper.getMainLooper()) { onFinish(); true })
            })
        }
        val msg = Message.obtain().apply { copyFrom(reply); data = result }
        runCatching { reply.replyTo.send(msg) }.onFailure { Log.w(TAG, "Icon-Position nicht gesendet", it) }
    }

    companion object {
        private const val TAG = "GestureNavContract"
        const val EXTRA_GESTURE_CONTRACT = "gesture_nav_contract_v1"
        const val EXTRA_ICON_POSITION = "gesture_nav_contract_icon_position"
        const val EXTRA_ICON_SURFACE = "gesture_nav_contract_surface_control"
        const val EXTRA_REMOTE_CALLBACK = "android.intent.extra.REMOTE_CALLBACK"
        const val EXTRA_ON_FINISH_CALLBACK = "gesture_nav_contract_finish_callback"

        /** Liest (und entfernt) den Vertrag aus dem Intent; null, wenn keiner da ist. */
        @Suppress("DEPRECATION")
        fun fromIntent(intent: Intent): GestureNavContract? {
            val extras = intent.getBundleExtra(EXTRA_GESTURE_CONTRACT) ?: return null
            intent.removeExtra(EXTRA_GESTURE_CONTRACT)
            val component = extras.getParcelable<ComponentName>(Intent.EXTRA_COMPONENT_NAME)
            val user = extras.getParcelable<UserHandle>(Intent.EXTRA_USER)
            val cb = extras.getParcelable<Message>(EXTRA_REMOTE_CALLBACK)
            return if (component != null && user != null && cb?.replyTo != null) GestureNavContract(component, user, cb) else null
        }
    }
}
