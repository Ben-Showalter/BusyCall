package com.busycall.app

import com.busycall.app.bridge.CallState
import com.busycall.app.bridge.CallUi
import com.busycall.app.bridge.PeerName
import com.busycall.app.call.CallRouter
import com.busycall.app.call.CallScreenRules
import com.busycall.app.call.OpenTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Opening BusyCall during a call, END arriving as HOME (Kyocera), new calls (owner, 2026-11-20). */
class CallScreenRulesTest {

    private fun call(id: Long, state: CallState) = CallUi(id, "Anna", "sip:100@pbx", "Office · 104", state == CallState.Ringing, state, 0)

    @Test fun `opening the app during a call shows the call`() {
        assertEquals(OpenTarget.InCall, CallScreenRules.onAppOpened(listOf(call(1, CallState.Connected)), isCallScreen = false))
        assertEquals(OpenTarget.InCall, CallScreenRules.onAppOpened(listOf(call(1, CallState.OnHold)), isCallScreen = false))
        assertEquals(OpenTarget.Incoming, CallScreenRules.onAppOpened(listOf(call(1, CallState.Connected), call(2, CallState.Ringing)), false))
    }

    @Test fun `no call, or the call screen itself, stays`() {
        assertEquals(OpenTarget.Stay, CallScreenRules.onAppOpened(emptyList(), isCallScreen = false))
        assertEquals(OpenTarget.Stay, CallScreenRules.onAppOpened(listOf(call(1, CallState.Ended)), isCallScreen = false))
        assertEquals(OpenTarget.Stay, CallScreenRules.onAppOpened(listOf(call(1, CallState.Connected)), isCallScreen = true))
    }

    @Test fun `leaving a call screen by key ends the call only on keypad phones, and never on purpose`() {
        assertTrue(CallScreenRules.leavingEnds(keypadPhone = true, onPurpose = false)) // Kyocera E4810: END = HOME
        assertFalse(CallScreenRules.leavingEnds(keypadPhone = true, onPurpose = true)) // Home softkey, + Call
        assertFalse(CallScreenRules.leavingEnds(keypadPhone = false, onPurpose = false)) // a touch phone's Home
    }

    @Test fun `a new outgoing call opens the call screen unless it's in front`() {
        assertTrue(CallRouter.shouldShowOutgoing(callScreenInFront = false, blockedFromBackground = false))
        assertFalse(CallRouter.shouldShowOutgoing(callScreenInFront = true, blockedFromBackground = false))
        assertFalse(CallRouter.shouldShowOutgoing(callScreenInFront = false, blockedFromBackground = true))
    }

    @Test fun `the phone's voicemail call is recognised`() {
        assertTrue(PeerName.isVoicemail("voicemail:"))
        assertTrue(PeerName.isVoicemail(" Voicemail:1"))
        assertFalse(PeerName.isVoicemail("sip:voicemail@pbx"))
    }
}
