package com.busycall.app.keys

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.busycall.app.bridge.Bridges
import com.busycall.app.call.InCallActivity
import com.busycall.app.ui.CallScreens

/**
 * BusyCall's accessibility service (owner, 2026-11-20): the only hook that sees END with our
 * self-managed calls (guide docs/platform.md). During a call END declines a ringing call, hangs
 * up on the call screen, or brings the call screen back from any other app. It never consumes
 * a key, so Android's own END (Home) still happens. While it runs it also keeps BusyCall alive,
 * so upstream's status notification is dropped (UPSTREAM.md).
 */
class EndKeyService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var endDown = false
    private var showOnUp = false

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
        if (KeyRouter.route(event.keyCode, event.scanCode) != LogicalKey.End) return false
        when (event.action) {
            KeyEvent.ACTION_DOWN -> if (event.repeatCount == 0) {
                endDown = true
                showOnUp = false
                when (val action = EndKeyDecision.decide(Bridges.current.calls(), CallScreens.inCallInFront)) {
                    is EndAction.Decline -> Bridges.current.decline(this, action.callId)
                    is EndAction.HangUp -> Bridges.current.hangup(this, action.callId)
                    EndAction.ShowCall -> showOnUp = true
                    EndAction.Nothing -> {}
                }
            }
            KeyEvent.ACTION_UP -> {
                if (endDown && showOnUp) handler.postDelayed(::showCall, SHOW_DELAY_MS)
                endDown = false
                showOnUp = false
            }
        }
        return false // never consume END (AGENTS §4)
    }

    /** After Android's own Home: the call screen in front again. PendingIntent, not startActivity (guide §5). */
    private fun showCall() {
        val intent = Intent(this, InCallActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        try {
            PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT).send()
        } catch (e: PendingIntent.CanceledException) {
            // The call screen can still be reached from the on-call notification.
        }
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
        handler.removeCallbacksAndMessages(null)
        if (!running) return
        running = false
        Bridges.current.setKeptAlive(false)
    }

    companion object {
        /** Long enough for Android's own END (Home) to finish first. */
        private const val SHOW_DELAY_MS = 300L

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
