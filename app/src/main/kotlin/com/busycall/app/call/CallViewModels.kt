package com.busycall.app.call

import com.busycall.app.bridge.Bridge
import com.busycall.app.bridge.Bridges
import com.busycall.app.bridge.CallState
import com.busycall.app.bridge.CallUi

/** Picks the call a screen is about: the one it was opened for, else the newest still going. */
internal fun pickCall(calls: List<CallUi>, callId: Long?, wanted: (CallUi) -> Boolean): CallUi? =
    calls.firstOrNull { it.id == callId && it.state != CallState.Ended }
        ?: calls.lastOrNull { it.state != CallState.Ended && wanted(it) }

/** Incoming call (PLAN.md S4). */
data class IncomingState(
    /** Null once the call is gone (answered elsewhere, cancelled, missed): the screen closes. */
    val call: CallUi?,
    /** Answered: the screen moves on to In call. */
    val answered: Boolean,
)

class IncomingViewModel(private val bridge: Bridge = Bridges.current) {
    var callId: Long? = null

    fun state(): IncomingState {
        val call = pickCall(bridge.calls(), callId) { it.state == CallState.Ringing }
        if (call != null) callId = call.id
        return IncomingState(
            call = call?.takeIf { it.state == CallState.Ringing },
            answered = call != null && call.state != CallState.Ringing,
        )
    }
}

/** In a call (PLAN.md S5). */
data class InCallState(
    val call: CallUi?,
    /** "03:42" while connected; empty while dialing/connecting. */
    val timer: String,
    val muted: Boolean,
    val speaker: Boolean,
    /** Keypad tones sent on this call, shown so the user sees the PIN went out. */
    val sentDigits: String,
    /** The other call, when there are two (one is on hold). */
    val other: CallUi? = null,
    /** This call is to the park number while the other is held: OK parks it there. */
    val parking: Boolean = false,
    /** The park number of this call's number (empty: no Park). */
    val parkNumber: String = "",
)

/** What happens after trying to join two calls (attended transfer or park). */
enum class ConnectStep {
    Parked, Transferred,
    /** The far end can't join calls (no Replaces): park by sending the held caller to the park number instead. */
    ParkFallback,
    NoReplaces, Failed,
}

/** The ▼ menu with one call (owner, 2026-11-20). */
enum class CallMenuItem { AddCall, Transfer, Park }

/** The five keys of the in-call D-pad ring (owner, 2026-11-20). */
enum class RingKey { Up, Down, Left, Right, Center }

/** What a ring key does now. */
enum class InCallAction {
    Speaker, Mute, Hold, Resume, Swap,
    /** ▼ with one call: Add a call · Transfer… · Park. */
    CallMenu,
    /** ▼ with two calls: connect them (attended transfer), you drop off. */
    Connect,
    /** OK on a call to the park number: park the held caller there. */
    ParkDone,
    HangUp, Nothing,
}

class InCallViewModel(private val bridge: Bridge = Bridges.current) {
    var callId: Long? = null
    private val sent = StringBuilder()

    fun state(): InCallState {
        val calls = bridge.calls()
        val call = pickCall(calls, callId) { it.state != CallState.Ringing }
        if (call != null && call.id != callId) {
            callId = call.id
            sent.setLength(0)
        }
        val other = calls.lastOrNull { it.id != call?.id && it.state != CallState.Ended && it.state != CallState.Ringing }
        val park = call?.let { bridge.parkNumber(it.numberId) }.orEmpty()
        val parking = call != null && other != null && park.isNotEmpty() &&
            com.busycall.app.bridge.PeerName.userPart(call.peerUri) == park
        return InCallState(
            call = call,
            timer = if (call?.state == CallState.Connected || call?.state == CallState.OnHold) CallText.duration(call.durationSec) else "",
            muted = bridge.isMuted(),
            speaker = bridge.isSpeakerOn(),
            sentDigits = sent.toString(),
            other = other,
            parking = parking,
            parkNumber = park,
        )
    }

    /** Sends one keypad tone; only once the call is connected. */
    fun digit(char: Char): Boolean {
        val call = state().call ?: return false
        if (call.state != CallState.Connected) return false
        bridge.sendDigit(call.id, char)
        sent.append(char)
        if (sent.length > MAX_SHOWN_DIGITS) sent.delete(0, sent.length - MAX_SHOWN_DIGITS)
        return true
    }

    /**
     * The ring: ▲ Speaker · ◀ Mute · ▶ Hold (Resume when on hold, Swap with two calls) ·
     * ▼ the call menu (Transfer with two calls) · OK End (Park on a call to the park number).
     * Pure, unit-tested.
     */
    fun actionFor(key: RingKey, state: InCallState): InCallAction {
        val call = state.call ?: return if (key == RingKey.Center) InCallAction.HangUp else InCallAction.Nothing
        return when (key) {
            RingKey.Up -> InCallAction.Speaker
            RingKey.Left -> InCallAction.Mute
            RingKey.Center -> if (state.parking && call.state == CallState.Connected) InCallAction.ParkDone else InCallAction.HangUp
            RingKey.Down -> when {
                // SIM calls: upstream can't transfer, join or park them.
                call.isSim -> InCallAction.Nothing
                state.other != null && call.state == CallState.Connected -> InCallAction.Connect
                state.other == null && call.state == CallState.Connected -> InCallAction.CallMenu
                else -> InCallAction.Nothing
            }
            RingKey.Right -> when {
                state.other != null -> InCallAction.Swap
                call.state == CallState.Connected -> InCallAction.Hold
                call.state == CallState.OnHold -> InCallAction.Resume
                else -> InCallAction.Nothing
            }
        }
    }

    fun toggleMute() = bridge.setMuted(!bridge.isMuted())
    fun toggleSpeaker() = bridge.toggleSpeaker()

    fun hold() {
        callId?.let { bridge.hold(it) }
    }

    fun resume() {
        callId?.let { bridge.resume(it) }
    }

    /** Two calls: hold this one, resume the other, and show it. */
    fun swap() {
        val state = state()
        val other = state.other ?: return
        state.call?.let { if (it.state == CallState.Connected) bridge.hold(it.id) }
        if (other.state == CallState.OnHold) bridge.resume(other.id)
        callId = other.id
        sent.setLength(0)
    }

    /** What ▼'s menu offers for this call: Park only when its number has a park number. */
    fun menuItems(state: InCallState): List<CallMenuItem> = buildList {
        add(CallMenuItem.AddCall)
        add(CallMenuItem.Transfer)
        if (state.parkNumber.isNotEmpty()) add(CallMenuItem.Park)
    }

    /** Joins the two calls (attended transfer or park) the way upstream does. */
    fun connect(): ConnectStep {
        val state = state()
        val call = state.call ?: return ConnectStep.Failed
        val other = state.other ?: return ConnectStep.Failed
        return afterConnect(bridge.connectCalls(call.id, other.id), state.parking)
    }

    /** Pure, unit-tested. */
    fun afterConnect(result: com.busycall.app.bridge.ConnectResult, parking: Boolean): ConnectStep = when (result) {
        com.busycall.app.bridge.ConnectResult.Connected -> if (parking) ConnectStep.Parked else ConnectStep.Transferred
        com.busycall.app.bridge.ConnectResult.NoReplaces -> if (parking) ConnectStep.ParkFallback else ConnectStep.NoReplaces
        com.busycall.app.bridge.ConnectResult.Failed -> ConnectStep.Failed
    }

    /**
     * Park when calls can't be joined: drop the call to the park number, and send the held
     * caller there (blind transfer). Returns true if the transfer went out.
     */
    fun parkByTransfer(hangup: (Long) -> Unit): Boolean {
        val state = state()
        val parkCall = state.call ?: return false
        val held = state.other ?: return false
        if (state.parkNumber.isEmpty()) return false
        hangup(parkCall.id)
        return bridge.transfer(held.id, state.parkNumber)
    }

    /** After placing a second call: show the newest one. */
    fun followNewest() {
        callId = null
        sent.setLength(0)
    }

    /** "+ Call": put this call on hold before the Dialer opens. */
    fun prepareAddCall() {
        val call = state().call ?: return
        if (call.state == CallState.Connected) bridge.hold(call.id)
    }

    /** After a hang-up, follow the other call if there is one. Returns true if there is. */
    fun followOther(): Boolean {
        val other = state().other ?: return false
        callId = other.id
        sent.setLength(0)
        return true
    }

    companion object {
        const val MAX_SHOWN_DIGITS = 16
    }
}
