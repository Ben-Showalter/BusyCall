package com.busycall.app.call

import com.busycall.app.bridge.PeerName
import com.busycall.app.bridge.SipAddress

/** Call timer and number text. Pure, unit-tested. */
object CallText {
    /** 0 → "00:00", 222 → "03:42", 3723 → "1:02:03". */
    fun duration(seconds: Int): String {
        val s = seconds.coerceAtLeast(0)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
    }

    /** The number line under a caller's name: `(378) 234-3189` for phone numbers, else the SIP user. */
    fun number(peerUri: String): String =
        PeerName.phoneNumber(peerUri)?.let { PeerName.format(it) } ?: SipAddress.parse(peerUri).user
}
