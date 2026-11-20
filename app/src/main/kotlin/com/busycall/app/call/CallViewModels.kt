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
)

/** The five keys of the in-call D-pad ring (owner, 2026-11-20). */
enum class RingKey { Up, Down, Left, Right, Center }

/** What a ring key does now. */
enum class InCallAction { Speaker, Mute, Hold, Resume, Swap, AddCall, HangUp, Nothing }

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
        return InCallState(
            call = call,
            timer = if (call?.state == CallState.Connected || call?.state == CallState.OnHold) CallText.duration(call.durationSec) else "",
            muted = bridge.isMuted(),
            speaker = bridge.isSpeakerOn(),
            sentDigits = sent.toString(),
            other = other,
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
     * ▼ + Call · OK End. Pure, unit-tested.
     */
    fun actionFor(key: RingKey, state: InCallState): InCallAction {
        val call = state.call ?: return if (key == RingKey.Center) InCallAction.HangUp else InCallAction.Nothing
        return when (key) {
            RingKey.Up -> InCallAction.Speaker
            RingKey.Left -> InCallAction.Mute
            RingKey.Center -> InCallAction.HangUp
            RingKey.Down -> if (state.other == null && call.state == CallState.Connected) InCallAction.AddCall else InCallAction.Nothing
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
