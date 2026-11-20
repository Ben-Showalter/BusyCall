package com.busycall.app.ui

/** Which BusyCall call screen is in front, for the END-key service (keys/EndKeyService). */
object CallScreens {
    /** True between InCallActivity's onResume and onPause. */
    @Volatile
    var inCallInFront = false
}
