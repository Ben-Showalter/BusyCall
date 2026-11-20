package com.busycall.app.keys

import com.busycall.app.bridge.CallState
import com.busycall.app.bridge.CallUi
import org.junit.Assert.assertEquals
import org.junit.Test

/** END during a call: the call screens (END = HOME on Kyocera) and the accessibility service (ENDCALL). */
class EndKeyDecisionTest {

    private fun call(id: Long, state: CallState, incoming: Boolean = false) =
        CallUi(id, "Anna", "sip:100@pbx", "Office · 104", incoming, state, 0)

    @Test fun `no call - END is left to Android`() {
        assertEquals(EndAction.Nothing, EndKeyDecision.decide(emptyList(), callScreenInFront = false))
        assertEquals(EndAction.Nothing, EndKeyDecision.decide(listOf(call(1, CallState.Ended)), callScreenInFront = true))
    }

    @Test fun `a ringing call is declined wherever you are`() {
        val calls = listOf(call(1, CallState.Connected), call(2, CallState.Ringing, incoming = true))
        assertEquals(EndAction.Decline(2), EndKeyDecision.decide(calls, callScreenInFront = false))
        assertEquals(EndAction.Decline(2), EndKeyDecision.decide(calls, callScreenInFront = true))
    }

    @Test fun `on the call screen END hangs up the call it shows`() {
        assertEquals(EndAction.HangUp(1), EndKeyDecision.decide(listOf(call(1, CallState.Dialing)), callScreenInFront = true))
        val two = listOf(call(1, CallState.OnHold), call(2, CallState.Connected))
        assertEquals(EndAction.HangUp(2), EndKeyDecision.decide(two, callScreenInFront = true))
        assertEquals(EndAction.HangUp(1), EndKeyDecision.decide(listOf(call(1, CallState.OnHold)), callScreenInFront = true))
    }

    @Test fun `in other apps END is left alone (owner, 2026-11-20)`() {
        assertEquals(EndAction.Nothing, EndKeyDecision.decide(listOf(call(1, CallState.Connected)), callScreenInFront = false))
    }
}
