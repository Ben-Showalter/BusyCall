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
import com.busycall.app.call.CallFromDecision
import com.busycall.app.call.CallMenuItem
import com.busycall.app.call.ConnectStep
import com.busycall.app.bridge.ConnectResult
import com.busycall.app.call.CallFromStep
import com.busycall.app.call.RingKey
import com.busycall.app.dialer.DialTarget
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
        assertEquals(InCallAction.Speaker, vm.actionFor(RingKey.Up, s))
        assertEquals(InCallAction.Mute, vm.actionFor(RingKey.Left, s))
        assertEquals(InCallAction.Hold, vm.actionFor(RingKey.Right, s))
        assertEquals(InCallAction.CallMenu, vm.actionFor(RingKey.Down, s))
        assertEquals(InCallAction.HangUp, vm.actionFor(RingKey.Center, s))
        vm.toggleMute(); vm.toggleSpeaker()
        assertTrue(vm.state().muted)
        assertTrue(vm.state().speaker)
        vm.hold()
        assertEquals(InCallAction.Resume, vm.actionFor(RingKey.Right, vm.state()))
        assertEquals(InCallAction.Nothing, vm.actionFor(RingKey.Down, vm.state())) // the menu only from a live call
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

    @Test fun `dialer typing, deleting and what OK or CALL calls`() {
        val vm = DialerViewModel(FakeBridge(numbers = mutableListOf(office)))
        "*97".forEach { vm.type(it) }
        assertTrue(vm.delete())
        vm.type('7')
        assertEquals(DialTarget.Dial("*97", "*97"), vm.target(null))
        val match = com.busycall.app.dialer.DialMatch("Anna", "Mobile", "tel:+15550198", "+15550198", 0..0, null)
        assertEquals(DialTarget.Dial("tel:+15550198", "+15550198"), vm.target(match))
        vm.setNumber("")
        assertFalse(vm.delete()) // empty: BACK leaves the Dialer
        assertEquals(DialTarget.NothingToCall, vm.target(null))
    }

    @Test fun `dialer empty plus CALL fills in the last number called`() {
        val bridge = FakeBridge(
            numbers = mutableListOf(office),
            recents = mutableListOf(
                RecentUi("Mom", "sip:5550142@pbx", "Office", office.id, CallDirection.In, false, 1L),
                RecentUi("Desk", "sip:100@pbx", "Office", office.id, CallDirection.Out, false, 2L),
            ),
        )
        val vm = DialerViewModel(bridge)
        assertEquals(DialTarget.Redialed, vm.target(null))
        assertEquals("100", vm.number)
    }

    // ---- Call from (owner, 2026-11-20) ----

    @Test fun `call from choices put the default first and the SIM last`() {
        val bridge = FakeBridge(numbers = mutableListOf(office, home))
        bridge.defaultId = home.id
        val choices = CallFromDecision.choices(bridge, hasTelephony = true)
        assertEquals(listOf("Home", "Office", ""), choices.map { it.label })
        assertNull(choices.last().id)
        assertEquals(2, CallFromDecision.choices(bridge, hasTelephony = false).size)
        assertTrue(CallFromDecision.choices(FakeBridge(running = false), hasTelephony = false).isEmpty())
    }

    @Test fun `short press picks, hold calls from the default, call-backs use their number`() {
        val choices = CallFromDecision.choices(FakeBridge(numbers = mutableListOf(office, home)), hasTelephony = true)
        assertEquals(CallFromStep.Pick(choices), CallFromDecision.decide(held = false, choices = choices))
        assertEquals(CallFromStep.CallNow(choices.first()), CallFromDecision.decide(held = true, choices = choices))
        val back = CallFromDecision.decide(held = false, choices = choices, callBackFrom = home.id)
        assertEquals(home.id, (back as CallFromStep.CallNow).from.id)
        // Only one way to call: no picker.
        val one = CallFromDecision.choices(FakeBridge(numbers = mutableListOf(office)), hasTelephony = false)
        assertEquals(CallFromStep.CallNow(one.single()), CallFromDecision.decide(held = false, choices = one))
        assertEquals(CallFromStep.NoNumber, CallFromDecision.decide(held = false, choices = emptyList()))
        // A call-back whose number is gone falls back to the usual rules.
        assertEquals(CallFromStep.CallNow(one.single()), CallFromDecision.decide(false, one, callBackFrom = "sip:gone@x"))
    }

    // ---- In-call ring with two calls ----

    @Test fun `two calls swap on the right key, hang-up follows the other`() {
        val bridge = FakeBridge()
        bridge.calls.add(call(1, CallState.OnHold))
        bridge.calls.add(call(2, CallState.Connected))
        val vm = InCallViewModel(bridge)
        val s = vm.state()
        assertEquals(2L, s.call?.id) // the newest
        assertEquals(1L, s.other?.id)
        assertEquals(InCallAction.Swap, vm.actionFor(RingKey.Right, s))
        assertEquals(InCallAction.Connect, vm.actionFor(RingKey.Down, s)) // two calls: ▼ connects them
        vm.swap()
        assertEquals(CallState.OnHold, bridge.calls.first { it.id == 2L }.state)
        assertEquals(CallState.Connected, bridge.calls.first { it.id == 1L }.state)
        assertEquals(1L, vm.state().call?.id)
        assertTrue(vm.followOther())
        assertEquals(2L, vm.callId)
    }

    @Test fun `add call holds the current one first, no call means only End works`() {
        val bridge = FakeBridge()
        bridge.calls.add(call(5, CallState.Connected))
        val vm = InCallViewModel(bridge)
        vm.state()
        vm.prepareAddCall()
        assertEquals(CallState.OnHold, bridge.calls.single().state)
        val empty = InCallViewModel(FakeBridge()).let { it.actionFor(RingKey.Up, it.state()) to it.actionFor(RingKey.Center, it.state()) }
        assertEquals(InCallAction.Nothing to InCallAction.HangUp, empty)
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

    // ---- Transfer and park (owner, 2026-11-20) ----

    private fun callOn(id: Long, state: CallState, peer: String) =
        CallUi(id, "Peer", peer, "Office · 104", false, state, 10, numberId = "sip:104@pbx")

    @Test fun `the down key menu offers add, transfer and park when there is a park number`() {
        val bridge = FakeBridge()
        bridge.calls.add(callOn(1, CallState.Connected, "sip:5550198@pbx"))
        val vm = InCallViewModel(bridge)
        val s = vm.state()
        assertEquals("700", s.parkNumber)
        assertEquals(listOf(CallMenuItem.AddCall, CallMenuItem.Transfer, CallMenuItem.Park), vm.menuItems(s))
        bridge.parkNumbers["sip:104@pbx"] = ""
        assertEquals(listOf(CallMenuItem.AddCall, CallMenuItem.Transfer), vm.menuItems(vm.state()))
    }

    @Test fun `two live calls - down connects them`() {
        val bridge = FakeBridge()
        bridge.calls.add(callOn(1, CallState.OnHold, "sip:5550198@pbx"))
        bridge.calls.add(callOn(2, CallState.Connected, "sip:105@pbx"))
        val vm = InCallViewModel(bridge)
        val s = vm.state()
        assertEquals(InCallAction.Connect, vm.actionFor(RingKey.Down, s))
        assertEquals(InCallAction.HangUp, vm.actionFor(RingKey.Center, s))
        assertEquals(ConnectStep.Transferred, vm.connect())
        assertEquals(listOf(2L to 1L), bridge.connected)
    }

    @Test fun `joining calls - each outcome, and park without Replaces falls back to a transfer`() {
        val vm = InCallViewModel(FakeBridge())
        assertEquals(ConnectStep.Parked, vm.afterConnect(ConnectResult.Connected, parking = true))
        assertEquals(ConnectStep.Transferred, vm.afterConnect(ConnectResult.Connected, parking = false))
        assertEquals(ConnectStep.ParkFallback, vm.afterConnect(ConnectResult.NoReplaces, parking = true))
        assertEquals(ConnectStep.NoReplaces, vm.afterConnect(ConnectResult.NoReplaces, parking = false))
        assertEquals(ConnectStep.Failed, vm.afterConnect(ConnectResult.Failed, parking = true))

        val bridge = FakeBridge()
        bridge.calls.add(callOn(1, CallState.OnHold, "sip:5550198@pbx"))
        bridge.calls.add(callOn(2, CallState.Connected, "sip:700@pbx"))
        bridge.connectResult = ConnectResult.NoReplaces
        val parking = InCallViewModel(bridge)
        assertEquals(ConnectStep.ParkFallback, parking.connect())
        val hungUp = mutableListOf<Long>()
        assertTrue(parking.parkByTransfer { hungUp.add(it) })
        assertEquals(listOf(2L), hungUp) // the call to 700 is dropped
        assertEquals(listOf(1L to "700"), bridge.transfers) // the caller is sent to the park number
    }

    @Test fun `calling the park number turns OK into Park`() {
        val bridge = FakeBridge()
        bridge.calls.add(callOn(1, CallState.OnHold, "sip:5550198@pbx"))
        bridge.calls.add(callOn(2, CallState.Connected, "sip:700@pbx"))
        val vm = InCallViewModel(bridge)
        val s = vm.state()
        assertTrue(s.parking)
        assertEquals(InCallAction.ParkDone, vm.actionFor(RingKey.Center, s))
        // Not yet answered by the park service: OK still ends it.
        bridge.setState(2, CallState.Dialing)
        assertEquals(InCallAction.HangUp, vm.actionFor(RingKey.Center, vm.state()))
        // A single call to 700 isn't parking anything.
        val solo = FakeBridge().apply { calls.add(callOn(3, CallState.Connected, "sip:700@pbx")) }
        assertFalse(InCallViewModel(solo).state().parking)
    }

    @Test fun `following the newest call after placing a second one`() {
        val bridge = FakeBridge()
        bridge.calls.add(callOn(1, CallState.OnHold, "sip:5550198@pbx"))
        val vm = InCallViewModel(bridge)
        vm.callId = 1L
        bridge.calls.add(callOn(2, CallState.Dialing, "sip:700@pbx"))
        assertEquals(1L, vm.state().call?.id)
        vm.followNewest()
        assertEquals(2L, vm.state().call?.id)
    }

    // ---- BusyCall as the phone's dialer ----

    @Test fun `the SIM becomes a real choice while BusyCall is the phone's dialer`() {
        val bridge = FakeBridge(numbers = mutableListOf(office))
        // Not the dialer: Phone (SIM) only opens the phone's dialer (id null).
        assertNull(CallFromDecision.choices(bridge, hasTelephony = true).last().id)
        bridge.simUi = com.busycall.app.bridge.SimUi("sip:mobile@pstn", ready = true)
        val sim = CallFromDecision.choices(bridge, hasTelephony = true).last()
        assertEquals("sip:mobile@pstn", sim.id)
        assertTrue(sim.isSim)
        assertEquals(NumberStatus.Registered, sim.status)
        bridge.simUi = com.busycall.app.bridge.SimUi("sip:mobile@pstn", ready = false)
        assertEquals(NumberStatus.NoConnection, CallFromDecision.choices(bridge, hasTelephony = true).last().status)
    }

    @Test fun `the phone's dial requests open BusyCall's dialer and call log`() {
        assertEquals(Route.Dialer, CallRouter.decide(null, Intent.ACTION_DIAL, null, false))
        assertEquals(Route.Dialer, CallRouter.decide(null, Intent.ACTION_DIAL, null, false, dataScheme = "tel"))
        assertEquals(Route.Dialer, CallRouter.decide(null, Intent.ACTION_CALL, null, false, dataScheme = "tel"))
        assertEquals(Route.Dialer, CallRouter.decide(null, Intent.ACTION_VIEW, null, false, dataScheme = "sip"))
        assertEquals(Route.Home, CallRouter.decide(null, Intent.ACTION_VIEW, null, false, mimeType = "vnd.android.cursor.dir/calls"))
        assertEquals(Route.Upstream, CallRouter.decide(null, Intent.ACTION_VIEW, null, false, dataScheme = "https"))
    }

    @Test fun `SIM calls have no call options`() {
        val bridge = FakeBridge()
        bridge.calls.add(CallUi(7, "Mom", "tel:+15550142", "Phone (SIM)", false, CallState.Connected, 5, isSim = true))
        val vm = InCallViewModel(bridge)
        val s = vm.state()
        assertEquals(InCallAction.Nothing, vm.actionFor(RingKey.Down, s))
        assertEquals(InCallAction.HangUp, vm.actionFor(RingKey.Center, s))
        assertEquals(InCallAction.Speaker, vm.actionFor(RingKey.Up, s))
    }
}
