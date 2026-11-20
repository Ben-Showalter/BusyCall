package com.busycall.app.keys

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.busycall.app.bridge.Bridges
import com.busycall.app.ui.CallScreens

/** One key press the service saw: its name, keycode and scan code. */
data class KeySeen(val name: String, val keyCode: Int, val scanCode: Int)

/**
 * BusyCall's accessibility service (owner, 2026-11-20). It keeps BusyCall alive, so upstream's
 * status notification is dropped (UPSTREAM.md). On a phone that sends `KEYCODE_ENDCALL` it
 * declines a ringing call and hangs up on the call screen; in other apps END is left alone. On
 * Kyocera END arrives as HOME, which the call screens handle themselves (onUserLeaveHint). It
 * remembers the last END/HOME it saw, for Settings. It never consumes a key.
 */
class EndKeyService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        running = true
        // Flags in code as well as XML: the OEM build doesn't always honour the XML (guide §5).
        serviceInfo = serviceInfo?.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
            eventTypes = AccessibilityEvent.TYPE_ANNOUNCEMENT
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        }
        Bridges.current.setKeptAlive(true)
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        // Cheap for every other key: it blocks key delivery to every app (guide §5).
        val key = KeyRouter.route(event.keyCode, event.scanCode)
        val isHome = event.keyCode == KeyEvent.KEYCODE_HOME
        if (key != LogicalKey.End && !isHome) return false
        if (event.action != KeyEvent.ACTION_DOWN || event.repeatCount != 0) return false
        // What this phone sends for END, for Settings (the E4810 spike, SPIKES S8).
        lastKey = KeySeen(if (isHome) "HOME" else "ENDCALL", event.keyCode, event.scanCode)
        if (key != LogicalKey.End) return false // HOME: the call screens handle it (onUserLeaveHint)
        when (val action = EndKeyDecision.decide(Bridges.current.calls(), CallScreens.inCallInFront)) {
            is EndAction.Decline -> Bridges.current.decline(this, action.callId)
            is EndAction.HangUp -> Bridges.current.hangup(this, action.callId)
            EndAction.Nothing -> {}
        }
        return false // never consume END (AGENTS §4)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        stopped()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        stopped()
        super.onDestroy()
    }

    private fun stopped() {
        if (!running) return
        running = false
        Bridges.current.setKeptAlive(false)
    }

    companion object {
        /** The last END or HOME press the service saw; null until one is pressed. */
        @Volatile
        var lastKey: KeySeen? = null
            private set

        @Volatile
        var running = false
            private set

        /** True when the user has switched the service on in Android's Accessibility settings. */
        fun isEnabled(ctx: Context): Boolean {
            val enabled = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
            val me = "${ctx.packageName}/${EndKeyService::class.java.name}"
            return enabled.split(':').any { it.equals(me, ignoreCase = true) }
        }
    }
}
