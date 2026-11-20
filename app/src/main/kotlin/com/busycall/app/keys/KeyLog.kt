package com.busycall.app.keys

import android.view.KeyEvent

/** One logged key press: what Android called it, its keycode and scan code. */
data class LoggedKey(val name: String, val keyCode: Int, val scanCode: Int, val source: String)

/**
 * The last few key presses BusyCall saw (its screens and the "Stay ready" service), shown in
 * Settings › Key log so a new phone's END, Clear and softkeys can be learned (guide §7).
 * Never records which digit was pressed (they may be PINs, AGENTS §10).
 */
object KeyLog {
    const val SIZE = 8
    private val entries = ArrayDeque<LoggedKey>()

    /** "BACK" for KEYCODE_BACK; replaceable in unit tests (no Android there). */
    var nameOf: (Int) -> String = { KeyEvent.keyCodeToString(it).removePrefix("KEYCODE_") }

    fun add(keyCode: Int, scanCode: Int, source: String) {
        val name = if (KeyRouter.route(keyCode, scanCode) is LogicalKey.Digit) "digit"
            else nameOf(keyCode)
        val digit = name == "digit"
        synchronized(entries) {
            entries.addFirst(LoggedKey(name, if (digit) 0 else keyCode, if (digit) 0 else scanCode, source))
            while (entries.size > SIZE) entries.removeLast()
        }
    }

    /** Newest first. */
    fun entries(): List<LoggedKey> = synchronized(entries) { entries.toList() }
}
