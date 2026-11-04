package com.busycall.app.keys

/**
 * Hold 0 for `+` (AGENTS.md §4), timed by us: Kyocera's `isLongPress` can fire both the tap
 * and the hold for one press (guide §1). Pure logic; the screen posts the delay and calls
 * [fire] when it elapses. Unit-tested.
 */
class HoldTimer(val holdMs: Long = 700L) {
    private var holding: Char? = null
    private var fired = false

    /** A key went down. Returns true if the screen should start the hold delay. */
    fun press(char: Char): Boolean {
        holding = char
        fired = false
        return char == '0'
    }

    /** The hold delay elapsed. Returns what to type now (`+`), or null if the key was already released. */
    fun fire(): Char? {
        if (holding != '0' || fired) return null
        fired = true
        return '+'
    }

    /** The key came up. Returns what to type for a tap, or null if the hold already typed `+`. */
    fun release(char: Char): Char? {
        val wasHeld = fired && holding == char
        holding = null
        fired = false
        return if (wasHeld) null else char
    }
}
