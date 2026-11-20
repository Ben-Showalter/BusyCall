package com.busycall.app.call

import com.busycall.app.bridge.CallState
import com.busycall.app.bridge.CallUi

/** Which screen BusyCall shows when it comes to the front. */
enum class OpenTarget { Stay, InCall, Incoming }

/**
 * Call-screen rules (owner, 2026-11-20). Pure, unit-tested.
 */
object CallScreenRules {

    /**
     * BusyCall was opened (launcher, recent apps, the flip, a notification) while [calls] exist:
     * show the call, not the last screen. [isCallScreen]: the screen being opened is already one.
     */
    fun onAppOpened(calls: List<CallUi>, isCallScreen: Boolean): OpenTarget {
        if (isCallScreen) return OpenTarget.Stay
        val live = calls.filter { it.state != CallState.Ended }
        return when {
            live.any { it.state == CallState.Ringing } -> OpenTarget.Incoming
            live.isNotEmpty() -> OpenTarget.InCall
            else -> OpenTarget.Stay
        }
    }

    /**
     * A call screen is being left by the user's own key, not by BusyCall ([onPurpose]: the Home
     * softkey, + Call, Transfer…). On Kyocera END arrives as HOME (as the Pure Speech dialer found),
     * so on a keypad phone that means END: hang up / decline. On a touch phone it's the nav-bar Home.
     */
    fun leavingEnds(keypadPhone: Boolean, onPurpose: Boolean): Boolean = keypadPhone && !onPurpose
}
