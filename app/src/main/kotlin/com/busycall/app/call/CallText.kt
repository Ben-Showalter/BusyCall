package com.busycall.app.call

/** Call timer text. Pure, unit-tested. */
object CallText {
    /** 0 → "00:00", 222 → "03:42", 3723 → "1:02:03". */
    fun duration(seconds: Int): String {
        val s = seconds.coerceAtLeast(0)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
    }
}
