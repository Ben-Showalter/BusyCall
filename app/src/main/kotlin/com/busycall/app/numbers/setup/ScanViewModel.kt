package com.busycall.app.numbers.setup

import com.busycall.app.bridge.NumberDraft

/** What a decoded QR code led to (PLAN.md §5.1). */
sealed class ScanOutcome {
    /** A setup code: open "Setup code found". Nothing is saved yet (AGENTS §6a). */
    data class Found(val draft: NumberDraft) : ScanOutcome()
    /** A QR code, but not a setup code (a web link, a Grandstream Wave code, …). */
    object NotACode : ScanOutcome()
    object TooNew : ScanOutcome()
    data class Incomplete(val missing: SetupCodeParser.Field) : ScanOutcome()
    /** The same rejected code again: keep the message that's already on screen. */
    object Repeat : ScanOutcome()
}

/** Scan screen logic; plain class (see HomeViewModel). Never logs or keeps the decoded text. */
class ScanViewModel {

    // A hash only, so the rejected code itself isn't kept around.
    private var lastRejected: Int? = null

    fun onDecoded(text: String): ScanOutcome {
        val outcome = when (val result = SetupCodeParser.parse(text)) {
            is SetupCodeParser.Result.Ok -> return ScanOutcome.Found(result.draft)
            is SetupCodeParser.Result.Incomplete -> ScanOutcome.Incomplete(result.missing)
            SetupCodeParser.Result.TooNew -> ScanOutcome.TooNew
            SetupCodeParser.Result.NotASetupCode -> ScanOutcome.NotACode
        }
        val hash = text.hashCode()
        if (hash == lastRejected) return ScanOutcome.Repeat
        lastRejected = hash
        return outcome
    }

    /** "Scan again": show the next rejection even if it's the same code. */
    fun reset() {
        lastRejected = null
    }
}
