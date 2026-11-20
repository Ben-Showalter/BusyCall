package com.busycall.app.keys

import com.busycall.app.bridge.CallState
import com.busycall.app.bridge.CallUi

/** What END does, decided by [EndKeyDecision]. */
sealed class EndAction {
    object Nothing : EndAction()
    data class Decline(val callId: Long) : EndAction()
    data class HangUp(val callId: Long) : EndAction()
    /** In another app or on the home screen during a call: bring the call screen back. */
    object ShowCall : EndAction()
}

/**
 * END during a call (owner, 2026-11-20). Our calls are Telecom self-managed (upstream), so
 * Android's own END only goes Home; BusyCall's accessibility service sees the key and decides
 * here. Pure, unit-tested. Android's own END action (Home) always happens too.
 */
object EndKeyDecision {

    fun decide(calls: List<CallUi>, callScreenInFront: Boolean): EndAction {
        val live = calls.filter { it.state != CallState.Ended }
        // A ringing call: END declines it (AGENTS §4), wherever the user is.
        live.firstOrNull { it.state == CallState.Ringing }?.let { return EndAction.Decline(it.id) }
        if (live.isEmpty()) return EndAction.Nothing
        if (!callScreenInFront) return EndAction.ShowCall
        // The call the screen shows: the one not on hold, else the newest.
        val shown = live.lastOrNull { it.state != CallState.OnHold } ?: live.last()
        return EndAction.HangUp(shown.id)
    }
}
