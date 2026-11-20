package com.busycall.app.dialer

import com.busycall.app.bridge.Bridge
import com.busycall.app.bridge.Bridges
import com.busycall.app.bridge.CallDirection
import com.busycall.app.bridge.SipAddress

data class DialerState(val number: String)

/** What OK/CALL in the Dialer should call. */
sealed class DialTarget {
    /** Call [dial] (shown to the user as [shown]) through the Call from… rules (call/CallFrom). */
    data class Dial(val dial: String, val shown: String) : DialTarget()
    /** Empty Dialer: the last number called was filled in; press again to call it. */
    object Redialed : DialTarget()
    object NothingToCall : DialTarget()
}

/**
 * Dialer logic (PLAN.md S2, §3.3). Plain class (see HomeViewModel). Contact matching: the screen
 * calls [buildMatcher] once and [matchesFor] per keypress, both off the main thread.
 */
class DialerViewModel(private val bridge: Bridge = Bridges.current) {

    var number = ""
        private set

    fun state() = DialerState(number)

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

    /**
     * What OK/CALL calls: the focused suggestion ([match]), else the typed number. An empty
     * Dialer fills in the last number called (PLAN §3.1 redial).
     */
    fun target(match: DialMatch?): DialTarget {
        if (match != null) return DialTarget.Dial(match.uri, match.display)
        if (number.isBlank()) {
            val last = bridge.recents(10).firstOrNull { it.direction == CallDirection.Out }
                ?: return DialTarget.NothingToCall
            number = SipAddress.parse(last.peerUri).user.ifEmpty { last.peerUri }
            return DialTarget.Redialed
        }
        return DialTarget.Dial(number, number)
    }

    companion object {
        const val MAX_LENGTH = 40
        private const val LOG_ENTRIES = 100
    }
}
