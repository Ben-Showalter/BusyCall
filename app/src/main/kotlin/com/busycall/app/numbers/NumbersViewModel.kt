package com.busycall.app.numbers

import com.busycall.app.bridge.Bridge
import com.busycall.app.bridge.Bridges
import com.busycall.app.bridge.NumberProblem
import com.busycall.app.bridge.NumberStatus
import com.busycall.app.bridge.Transport
import com.busycall.app.numbers.setup.SetupCodeParser

/** Everything the Numbers screen shows (PLAN.md S7). */
data class NumbersState(
    val running: Boolean,
    val numbers: List<NumbersRow>,
    /** Show the "Phone (SIM)" entry (PLAN §5.2): only on phones with telephony. */
    val showSim: Boolean,
)

data class NumbersRow(
    val id: String,
    val label: String,
    val number: String,
    val server: String,
    val transport: Transport,
    val status: NumberStatus,
    val isDefault: Boolean,
    val problem: NumberProblem? = null,
)

/** What a number's menu offers, in order. */
enum class NumberAction { Edit, MakeDefault, Retry, Delete }

/** What pasting a setup link led to. */
sealed class PasteOutcome {
    data class Found(val draft: com.busycall.app.bridge.NumberDraft) : PasteOutcome()
    object NothingCopied : PasteOutcome()
    object NotACode : PasteOutcome()
    object TooNew : PasteOutcome()
    data class Incomplete(val missing: SetupCodeParser.Field) : PasteOutcome()
}

/** Numbers screen logic; plain class (see HomeViewModel). */
class NumbersViewModel(private val bridge: Bridge = Bridges.current) {

    fun state(hasTelephony: Boolean): NumbersState = NumbersState(
        running = bridge.isRunning(),
        numbers = if (bridge.isRunning()) bridge.numbers().map {
            NumbersRow(it.id, it.label, it.number, it.server, it.transport, it.status, it.isDefault, it.problem)
        } else emptyList(),
        showSim = hasTelephony,
    )

    fun actionsFor(row: NumbersRow): List<NumberAction> = buildList {
        add(NumberAction.Edit)
        if (!row.isDefault) add(NumberAction.MakeDefault)
        if (row.status == NumberStatus.NoConnection || row.status == NumberStatus.Connecting) add(NumberAction.Retry)
        add(NumberAction.Delete)
    }

    fun perform(action: NumberAction, row: NumbersRow) {
        when (action) {
            NumberAction.Edit -> {} // the screen opens Number setup
            NumberAction.MakeDefault -> bridge.setDefault(row.id)
            NumberAction.Retry -> bridge.retry(row.id)
            NumberAction.Delete -> bridge.deleteNumber(row.id)
        }
    }

    /** Reads a pasted setup link. [clipboardText] is null when nothing is copied. */
    fun paste(clipboardText: String?): PasteOutcome {
        if (clipboardText.isNullOrBlank()) return PasteOutcome.NothingCopied
        return when (val result = SetupCodeParser.parse(clipboardText)) {
            is SetupCodeParser.Result.Ok -> PasteOutcome.Found(result.draft)
            is SetupCodeParser.Result.Incomplete -> PasteOutcome.Incomplete(result.missing)
            SetupCodeParser.Result.TooNew -> PasteOutcome.TooNew
            SetupCodeParser.Result.NotASetupCode -> PasteOutcome.NotACode
        }
    }
}
