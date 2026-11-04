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
)

/** What the in-call Options menu offers, in order. */
enum class InCallAction { Speaker, Mute, Hold, Resume, HangUp }

class InCallViewModel(private val bridge: Bridge = Bridges.current) {
    var callId: Long? = null
    private val sent = StringBuilder()

    fun state(): InCallState {
        val call = pickCall(bridge.calls(), callId) { it.state != CallState.Ringing }
        if (call != null && call.id != callId) {
            callId = call.id
            sent.setLength(0)
        }
        return InCallState(
            call = call,
            timer = if (call?.state == CallState.Connected || call?.state == CallState.OnHold) CallText.duration(call.durationSec) else "",
            muted = bridge.isMuted(),
            speaker = bridge.isSpeakerOn(),
            sentDigits = sent.toString(),
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

    fun actions(state: InCallState): List<InCallAction> = buildList {
        add(InCallAction.Speaker)
        add(InCallAction.Mute)
        when (state.call?.state) {
            CallState.Connected -> add(InCallAction.Hold)
            CallState.OnHold -> add(InCallAction.Resume)
            else -> {}
        }
        add(InCallAction.HangUp)
    }

    fun toggleMute() = bridge.setMuted(!bridge.isMuted())
    fun toggleSpeaker() = bridge.toggleSpeaker()

    fun hold() {
        callId?.let { bridge.hold(it) }
    }

    fun resume() {
        callId?.let { bridge.resume(it) }
    }

    companion object {
        const val MAX_SHOWN_DIGITS = 16
    }
}
