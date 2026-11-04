package com.busycall.app

import android.content.Intent
import com.busycall.app.bridge.CallDirection
import com.busycall.app.bridge.CallState
import com.busycall.app.bridge.CallUi
import com.busycall.app.bridge.NumberStatus
import com.busycall.app.bridge.NumberUi
import com.busycall.app.bridge.PlaceProblem
import com.busycall.app.bridge.PlaceResult
import com.busycall.app.bridge.RecentUi
import com.busycall.app.bridge.Transport
import com.busycall.app.call.CallRouter
import com.busycall.app.call.CallRouter.Route
import com.busycall.app.call.CallText
import com.busycall.app.call.InCallAction
import com.busycall.app.call.InCallViewModel
import com.busycall.app.call.IncomingViewModel
import com.busycall.app.dialer.DialOutcome
import com.busycall.app.dialer.DialerViewModel
import com.busycall.app.keys.HoldTimer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CallsTest {

    private fun call(id: Long, state: CallState, incoming: Boolean = true, seconds: Int = 0) =
        CallUi(id, "Anna Becker", "sip:5550198@pbx", "Office · 104", incoming, state, seconds)

    // ---- Timer ----

    @Test fun `call timer text`() {
        assertEquals("00:00", CallText.duration(0))
        assertEquals("03:42", CallText.duration(222))
        assertEquals("1:02:03", CallText.duration(3723))
        assertEquals("00:00", CallText.duration(-5))
    }

    // ---- Router: upstream's call events → BusyCall screens ----

    @Test fun `router decisions`() {
        assertEquals(Route.Incoming, CallRouter.decide("call show", null, CallState.Ringing, true))
        assertEquals(Route.InCall, CallRouter.decide("call show", null, CallState.Connected, true))
        assertEquals(Route.Home, CallRouter.decide("call show", null, null, false)) // already gone: missed
        assertEquals(Route.InCall, CallRouter.decide("call answer", null, CallState.Ringing, true))
        assertEquals(Route.Home, CallRouter.decide("call answer", null, CallState.Ended, false))
        assertEquals(Route.Home, CallRouter.decide("call missed", null, null, false))
        assertEquals(Route.InCall, CallRouter.decide(null, Intent.ACTION_MAIN, null, true))
        assertEquals(Route.Home, CallRouter.decide(null, Intent.ACTION_MAIN, null, false))
        // Classic screens, messages and transfers stay with upstream.
        assertEquals(Route.Upstream, CallRouter.decide(null, null, null, true))
        assertEquals(Route.Upstream, CallRouter.decide("message show", null, null, false))
        assertEquals(Route.Upstream, CallRouter.decide("transfer show", null, CallState.Connected, true))
    }

    // ---- Incoming ----

    @Test fun `incoming shows the ringing call, then knows when it was answered or ended`() {
        val bridge = FakeBridge()
        bridge.calls.add(call(7, CallState.Ringing))
        val vm = IncomingViewModel(bridge)
        assertEquals(7L, vm.state().call?.id)
        assertFalse(vm.state().answered)
        bridge.setState(7, CallState.Connecting)
        assertTrue(vm.state().answered)
        bridge.calls.clear()
        val gone = vm.state()
        assertNull(gone.call)
        assertFalse(gone.answered)
    }

    // ---- In call ----

    @Test fun `keypad tones go out only once connected, and are shown`() {
        val bridge = FakeBridge()
        bridge.calls.add(call(9, CallState.Dialing, incoming = false))
        val vm = InCallViewModel(bridge)
        assertFalse(vm.digit('1')) // not connected yet
        bridge.setState(9, CallState.Connected)
        "1234#".forEach { assertTrue(vm.digit(it)) }
        assertEquals("1234#", bridge.digits.toString())
        assertEquals("1234#", vm.state().sentDigits)
    }

    @Test fun `in-call timer, mute, speaker and menu`() {
        val bridge = FakeBridge()
        bridge.calls.add(call(3, CallState.Connected, seconds = 75))
        val vm = InCallViewModel(bridge)
        val s = vm.state()
        assertEquals("01:15", s.timer)
        assertEquals(listOf(InCallAction.Speaker, InCallAction.Mute, InCallAction.Hold, InCallAction.HangUp), vm.actions(s))
        vm.toggleMute(); vm.toggleSpeaker()
        assertTrue(vm.state().muted)
        assertTrue(vm.state().speaker)
        vm.hold()
        assertEquals(listOf(InCallAction.Speaker, InCallAction.Mute, InCallAction.Resume, InCallAction.HangUp), vm.actions(vm.state()))
        vm.resume()
        assertEquals(CallState.Connected, vm.state().call?.state)
    }

    @Test fun `in call follows the newest call when opened without an id, never a ringing one`() {
        val bridge = FakeBridge()
        bridge.calls.add(call(1, CallState.Connected))
        bridge.calls.add(call(2, CallState.Ringing))
        assertEquals(1L, InCallViewModel(bridge).state().call?.id)
    }

    // ---- Dialer ----

    private val office = NumberUi("sip:104@pbx", "Office", "104", "pbx", Transport.TLS, NumberStatus.Registered, 0, false)
    private val home = NumberUi("sip:555@voip", "Home", "555", "voip", Transport.UDP, NumberStatus.Registered, 0, false)

    @Test fun `dialer starts on the default number, offers the SIM last, clamps at the ends`() {
        val bridge = FakeBridge(numbers = mutableListOf(office, home))
        bridge.defaultId = home.id
        val vm = DialerViewModel(bridge, hasTelephony = true)
        assertEquals("Home", vm.state().from?.label)
        vm.previousFrom()
        assertEquals("Home", vm.state().from?.label) // already first: no wrap
        vm.nextFrom(); vm.nextFrom(); vm.nextFrom()
        assertNull(vm.state().from?.id) // Phone (SIM), last
        assertEquals(3, vm.fromChoices().size)
    }

    @Test fun `dialer typing, deleting and calling`() {
        val bridge = FakeBridge(numbers = mutableListOf(office))
        val vm = DialerViewModel(bridge, hasTelephony = false)
        "*97".forEach { vm.type(it) }
        assertTrue(vm.delete())
        vm.type('7')
        val placed = mutableListOf<Pair<String, String>>()
        val outcome = vm.call { from, n -> placed.add(from to n); PlaceResult.Placed }
        assertEquals(DialOutcome.Placed, outcome)
        assertEquals(listOf(office.id to "*97"), placed)
        vm.setNumber("")
        assertFalse(vm.delete()) // empty: BACK leaves the Dialer
    }

    @Test fun `dialer from the SIM hands the number to the phone`() {
        val vm = DialerViewModel(FakeBridge(), hasTelephony = true)
        vm.setNumber("5550142")
        assertEquals(DialOutcome.PhoneDialer("5550142"), vm.call { _, _ -> error("must not place a SIP call") })
    }

    @Test fun `dialer empty plus CALL fills in the last number called`() {
        val bridge = FakeBridge(
            numbers = mutableListOf(office),
            recents = mutableListOf(
                RecentUi("Mom", "sip:5550142@pbx", "Office", office.id, CallDirection.In, false, 1L),
                RecentUi("Desk", "sip:100@pbx", "Office", office.id, CallDirection.Out, false, 2L),
            ),
        )
        val vm = DialerViewModel(bridge, hasTelephony = false)
        assertEquals(DialOutcome.Redialed, vm.call { _, _ -> error("not yet") })
        assertEquals("100", vm.number)
    }

    @Test fun `dialer reports problems`() {
        val vm = DialerViewModel(FakeBridge(numbers = mutableListOf(office)), hasTelephony = false)
        vm.setNumber("104")
        assertEquals(DialOutcome.Failed(PlaceProblem.PhoneBusy), vm.call { _, _ -> PlaceResult.Failed(PlaceProblem.PhoneBusy) })
        val none = DialerViewModel(FakeBridge(), hasTelephony = false)
        none.setNumber("104")
        assertEquals(DialOutcome.Failed(PlaceProblem.NoNumber), none.call { _, _ -> PlaceResult.Placed })
    }

    // ---- Hold 0 for + ----

    @Test fun `hold timer`() {
        val t = HoldTimer()
        assertTrue(t.press('0'))
        assertEquals('0', t.release('0')) // quick tap
        assertTrue(t.press('0'))
        assertEquals('+', t.fire())
        assertNull(t.release('0')) // the hold already typed +
        assertFalse(t.press('5'))
        assertNull(t.fire())
        assertEquals('5', t.release('5'))
    }
}
