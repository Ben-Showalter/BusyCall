package com.busycall.app.keys

import com.busycall.app.bridge.CallState
import com.busycall.app.bridge.CallUi

/** What END does, decided by [EndKeyDecision]. */
sealed class EndAction {
    object Nothing : EndAction()
    data class Decline(val callId: Long) : EndAction()
    data class HangUp(val callId: Long) : EndAction()
}

/**
 * END during a call (owner, 2026-11-20). Our calls are Telecom self-managed (upstream), so
 * Android's own END only goes Home. The call screens act on it (onUserLeaveHint, END arriving as
 * HOME on Kyocera), and the accessibility service where a phone sends KEYCODE_ENDCALL. In other
 * apps END is left alone. Pure, unit-tested. Android's own END action (Home) always happens too.
 */
object EndKeyDecision {

    fun decide(calls: List<CallUi>, callScreenInFront: Boolean): EndAction {
        val live = calls.filter { it.state != CallState.Ended }
        // A ringing call: END declines it (AGENTS §4), wherever the user is.
        live.firstOrNull { it.state == CallState.Ringing }?.let { return EndAction.Decline(it.id) }
        if (live.isEmpty() || !callScreenInFront) return EndAction.Nothing
        // The call the screen shows: the one not on hold, else the newest.
        val shown = live.lastOrNull { it.state != CallState.OnHold } ?: live.last()
        return EndAction.HangUp(shown.id)
    }
}
