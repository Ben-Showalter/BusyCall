package com.busycall.app.call

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import com.busycall.app.bridge.Bridge
import com.busycall.app.bridge.Bridges
import com.busycall.app.bridge.NumberStatus
import com.busycall.app.bridge.PlaceProblem
import com.busycall.app.bridge.PlaceResult
import com.busycall.app.ui.MenuItem
import com.busycall.app.ui.Menus
import com.busycall.app.ui.Words
import com.tutpro.baresip.R

/** One way to call: a SIP number, or the phone's SIM ([id] null). */
data class FromChoice(val id: String?, val label: String, val number: String, val status: NumberStatus? = null)

/** What pressing OK/CALL on a new call leads to. */
sealed class CallFromStep {
    /** Show the Call from… picker. */
    data class Pick(val choices: List<FromChoice>) : CallFromStep()
    /** Call now from this choice. */
    data class CallNow(val from: FromChoice) : CallFromStep()
    object NoNumber : CallFromStep()
}

/**
 * Where a call goes out from (owner, 2026-11-20). Pure, unit-tested.
 * - A call-back (Recents, Call log) uses the number the call came in on, no picker (AGENTS §6).
 * - New calls: a hold of OK/CALL calls from ★ (the first choice); a short press opens the
 *   picker, unless there is only one way to call.
 */
object CallFromDecision {

    /** SIP numbers, ★ default first, then Phone (SIM) when the phone has telephony. */
    fun choices(bridge: Bridge, hasTelephony: Boolean): List<FromChoice> {
        val sip = if (bridge.isRunning()) bridge.numbers().sortedByDescending { it.isDefault } else emptyList()
        return sip.map { FromChoice(it.id, it.label, it.number, it.status) } +
            if (hasTelephony) listOf(FromChoice(null, "", "")) else emptyList()
    }

    fun decide(held: Boolean, choices: List<FromChoice>, callBackFrom: String? = null): CallFromStep {
        callBackFrom?.let { id -> choices.firstOrNull { it.id == id }?.let { return CallFromStep.CallNow(it) } }
        if (choices.isEmpty()) return CallFromStep.NoNumber
        if (held || choices.size == 1) return CallFromStep.CallNow(choices.first())
        return CallFromStep.Pick(choices)
    }
}

/**
 * Places a new call the BusyCall way: decides with [CallFromDecision], shows the Call from…
 * picker when needed, then calls through the bridge, or hands a SIM call to the phone's dialer
 * (PLAN §5.2). Failures come back to [onProblem] in plain words.
 */
object Calling {

    fun start(
        activity: Activity,
        dial: String,
        shown: String,
        held: Boolean,
        callBackFrom: String? = null,
        onPlaced: () -> Unit = {},
        onProblem: (String) -> Unit = { Toast.makeText(activity, it, Toast.LENGTH_LONG).show() },
    ) {
        val hasTelephony = activity.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
        val choices = CallFromDecision.choices(Bridges.current, hasTelephony)
        when (val step = CallFromDecision.decide(held, choices, callBackFrom)) {
            CallFromStep.NoNumber -> onProblem(activity.getString(R.string.bc_dialer_no_numbers))
            is CallFromStep.CallNow -> place(activity, step.from, dial, onPlaced, onProblem)
            is CallFromStep.Pick -> Menus.show(
                activity,
                activity.getString(R.string.bc_callfrom_title, shown),
                step.choices.map { c ->
                    MenuItem(label(activity, c), subtitle(activity, c)) { place(activity, c, dial, onPlaced, onProblem) }
                },
                message = activity.getString(R.string.bc_callfrom_tip),
            )
        }
    }

    fun label(activity: Activity, c: FromChoice): String = when {
        c.id == null -> activity.getString(R.string.bc_sim_title)
        else -> activity.getString(R.string.bc_number_title, c.label, c.number)
    }

    private fun subtitle(activity: Activity, c: FromChoice): String = when (val s = c.status) {
        null -> activity.getString(R.string.bc_sim_subtitle)
        else -> Words.status(activity, s)
    }

    private fun place(activity: Activity, from: FromChoice, dial: String, onPlaced: () -> Unit, onProblem: (String) -> Unit) {
        if (from.id == null) {
            // Phone (SIM): the phone's own dialer, number filled in (PLAN §5.2). Never ACTION_CALL.
            val number = dial.substringAfter("tel:").substringAfter("sip:").substringBefore('@')
            try {
                activity.startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null)))
            } catch (e: ActivityNotFoundException) {
                onProblem(activity.getString(R.string.bc_sim_no_dialer))
            }
            return
        }
        when (val result = Bridges.current.placeCall(activity, from.id, dial)) {
            PlaceResult.Placed -> {
                // A second call from "+ Call": back to the existing In call screen (CLEAR_TOP), not a new one.
                activity.startActivity(Intent(activity, InCallActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                onPlaced()
            }
            is PlaceResult.Failed -> onProblem(activity.getString(problemText(result.problem)))
        }
    }

    fun problemText(problem: PlaceProblem): Int = when (problem) {
        PlaceProblem.ServiceNotRunning -> R.string.bc_problem_not_running
        PlaceProblem.NoNumber -> R.string.bc_dialer_no_numbers
        PlaceProblem.InvalidAddress -> R.string.bc_dialer_invalid
        PlaceProblem.PhoneBusy -> R.string.bc_dialer_phone_busy
        PlaceProblem.Failed -> R.string.bc_dialer_failed
    }
}
