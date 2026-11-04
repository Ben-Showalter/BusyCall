package com.busycall.app.dialer

import com.busycall.app.bridge.Bridge
import com.busycall.app.bridge.Bridges
import com.busycall.app.bridge.CallDirection
import com.busycall.app.bridge.PlaceProblem
import com.busycall.app.bridge.PlaceResult
import com.busycall.app.bridge.SipAddress

/** One "Call from" choice: a SIP number, or the phone's SIM ([id] null). */
data class FromChoice(val id: String?, val label: String, val number: String)

data class DialerState(
    val number: String,
    val from: FromChoice?,
    /** More than one choice: show ◀ ▶. */
    val canChangeFrom: Boolean,
)

/** What pressing Call led to. */
sealed class DialOutcome {
    /** A BusyCall call was placed: open In call. */
    object Placed : DialOutcome()
    /** "Phone (SIM)": hand the number to the phone's own dialer (PLAN §5.2). */
    data class PhoneDialer(val number: String) : DialOutcome()
    /** Empty field: the last number called was filled in; press Call again. */
    object Redialed : DialOutcome()
    object NothingToCall : DialOutcome()
    data class Failed(val problem: PlaceProblem) : DialOutcome()
}

/**
 * Dialer logic (PLAN.md S2, §3.3). Plain class (see HomeViewModel). Contact matching: the screen
 * calls [buildMatcher] once and [matchesFor] per keypress, both off the main thread.
 */
class DialerViewModel(private val bridge: Bridge = Bridges.current, private val hasTelephony: Boolean) {

    var number = ""
        private set

    private val choices: List<FromChoice> by lazy {
        val sip = if (bridge.isRunning()) bridge.numbers().sortedByDescending { it.isDefault } else emptyList()
        sip.map { FromChoice(it.id, it.label, it.number) } +
            if (hasTelephony) listOf(FromChoice(null, "", "")) else emptyList()
    }
    private var fromIndex = 0

    fun state() = DialerState(number, choices.getOrNull(fromIndex), choices.size > 1)

    fun type(char: Char) {
        if (number.length < MAX_LENGTH) number += char
    }

    /** Deletes one character. Returns false if there was nothing to delete (BACK then leaves). */
    fun delete(): Boolean {
        if (number.isEmpty()) return false
        number = number.dropLast(1)
        return true
    }

    fun setNumber(text: String) {
        number = text.take(MAX_LENGTH)
    }

    /** ◀ ▶ on "Call from": clamps at the ends, no wrap (guide §2). */
    fun nextFrom() {
        if (fromIndex < choices.size - 1) fromIndex++
    }

    fun previousFrom() {
        if (fromIndex > 0) fromIndex--
    }

    fun chooseFrom(index: Int) {
        if (index in choices.indices) fromIndex = index
    }

    fun fromChoices(): List<FromChoice> = choices

    /**
     * The keypad-letter index of the phone's contacts plus call-log numbers (PLAN §3.3).
     * Slow-ish: call it off the main thread.
     */
    fun buildMatcher(): KeypadMatcher {
        if (!bridge.isRunning()) return KeypadMatcher(emptyList())
        val fromLog = bridge.recents(LOG_ENTRIES).map {
            KeypadMatcher.DialEntry(it.peerName, it.peerUri, SipAddress.parse(it.peerUri).user.ifEmpty { it.peerUri })
        }
        return KeypadMatcher(bridge.contacts(), fromLog)
    }

    fun matchesFor(matcher: KeypadMatcher?, text: String): List<DialMatch> = matcher?.match(text) ?: emptyList()

    /** CALL or OK on a suggestion: calls it from the chosen number (or hands it to the SIM dialer). */
    fun callMatch(match: DialMatch, place: (fromId: String, number: String) -> PlaceResult): DialOutcome {
        val from = choices.getOrNull(fromIndex) ?: return DialOutcome.Failed(PlaceProblem.NoNumber)
        if (from.id == null) return DialOutcome.PhoneDialer(match.display)
        return when (val result = place(from.id, match.uri)) {
            PlaceResult.Placed -> DialOutcome.Placed
            is PlaceResult.Failed -> DialOutcome.Failed(result.problem)
        }
    }

    /** [place] places a BusyCall call (the screen passes `bridge.placeCall` with itself). */
    fun call(place: (fromId: String, number: String) -> PlaceResult): DialOutcome {
        if (number.isBlank()) {
            // Empty Dialer + CALL = redial (PLAN §3.1): fill in the last number called.
            val last = bridge.recents(10).firstOrNull { it.direction == CallDirection.Out }
                ?: return DialOutcome.NothingToCall
            number = SipAddress.parse(last.peerUri).user.ifEmpty { last.peerUri }
            return DialOutcome.Redialed
        }
        val from = choices.getOrNull(fromIndex) ?: return DialOutcome.Failed(PlaceProblem.NoNumber)
        if (from.id == null) return DialOutcome.PhoneDialer(number)
        return when (val result = place(from.id, number)) {
            PlaceResult.Placed -> DialOutcome.Placed
            is PlaceResult.Failed -> DialOutcome.Failed(result.problem)
        }
    }

    companion object {
        const val MAX_LENGTH = 40
        private const val LOG_ENTRIES = 100
    }
}
