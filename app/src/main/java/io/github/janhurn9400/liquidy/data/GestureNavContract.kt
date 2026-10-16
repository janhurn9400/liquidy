package io.github.janhurn9400.liquidy.data

import android.content.ComponentName
import android.content.Intent
import android.graphics.RectF
import android.os.Bundle
import android.os.Message
import android.os.UserHandle
import android.util.Log

/**
 * Handshake with gesture navigation (AOSP Launcher3 GestureNavContract, since Android 11 QPR1):
 * when the user swipes home in an app, a HOME intent arrives with [EXTRA_GESTURE_CONTRACT]. We answer with the
 * screen position of the matching icon → the system lets the app window fly into this icon.
 * The system determines the flight curve; we only provide the target (+ optionally an icon surface to crossfade into).
 */
class GestureNavContract(val component: ComponentName, val user: UserHandle, private val reply: Message) {

    /**
     * @param surface icon surface the system crossfades the window into at the end (otherwise it only fades out).
     * @param onFinish runs when the system has finished the animation (show the original icon again, remove the surface).
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
        runCatching { reply.replyTo.send(msg) }.onFailure { Log.w(TAG, "Icon position not sent", it) }
    }

    companion object {
        private const val TAG = "GestureNavContract"
        const val EXTRA_GESTURE_CONTRACT = "gesture_nav_contract_v1"
        const val EXTRA_ICON_POSITION = "gesture_nav_contract_icon_position"
        const val EXTRA_ICON_SURFACE = "gesture_nav_contract_surface_control"
        const val EXTRA_REMOTE_CALLBACK = "android.intent.extra.REMOTE_CALLBACK"
        const val EXTRA_ON_FINISH_CALLBACK = "gesture_nav_contract_finish_callback"

        /** Reads (and removes) the contract from the intent; null if there is none. */
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
