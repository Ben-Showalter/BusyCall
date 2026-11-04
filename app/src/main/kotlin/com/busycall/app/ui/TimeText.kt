package com.busycall.app.ui

import java.time.Instant
import java.time.ZoneId

/** When something happened, relative to now; the screen turns it into words. Pure, unit-tested. */
sealed class TimeLabel {
    object Unknown : TimeLabel()
    object JustNow : TimeLabel()
    data class MinutesAgo(val minutes: Int) : TimeLabel()
    /** Earlier today: show the clock time. */
    data class Today(val millis: Long) : TimeLabel()
    object Yesterday : TimeLabel()
    /** Older: show the date. */
    data class OnDate(val millis: Long) : TimeLabel()
}

object TimeText {
    fun label(millis: Long?, nowMillis: Long, zone: ZoneId): TimeLabel {
        if (millis == null || millis <= 0) return TimeLabel.Unknown
        val ago = nowMillis - millis
        if (ago < 60_000) return TimeLabel.JustNow
        if (ago < 60 * 60_000) return TimeLabel.MinutesAgo((ago / 60_000).toInt())
        val day = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        return when (day) {
            today -> TimeLabel.Today(millis)
            today.minusDays(1) -> TimeLabel.Yesterday
            else -> TimeLabel.OnDate(millis)
        }
    }
}
