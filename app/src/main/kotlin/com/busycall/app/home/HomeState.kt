package com.busycall.app.home

import com.busycall.app.bridge.CallDirection
import com.busycall.app.bridge.NumberProblem
import com.busycall.app.bridge.NumberStatus
import com.busycall.app.bridge.RecentUi
import com.busycall.app.ui.TimeLabel

/**
 * Everything Home shows (PLAN.md S1). Home is the call log (owner, 2026-11-20); above it only
 * what must never fail silently: the numbers that can't connect (AGENTS §6), or "Add a number".
 */
data class HomeState(
    val problem: HomeProblem? = null,
    /** Numbers that can't connect right now; empty when all is well. */
    val trouble: List<HomeNumberRow> = emptyList(),
    /** No numbers at all: show "+ Add a number". */
    val noNumbers: Boolean = false,
    val calls: List<HomeCallRow> = emptyList(),
)

/** One call-log row. */
data class HomeCallRow(val recent: RecentUi, val time: TimeLabel) {
    val icon: CallIcon
        get() = when {
            recent.missed -> CallIcon.Missed
            recent.direction == CallDirection.In -> CallIcon.In
            else -> CallIcon.Out
        }
}

/** The phone icon with an arrow on a call row: green in/out, red missed. */
enum class CallIcon { In, Out, Missed }

/** What Home does about the phone's contacts on this refresh. */
enum class ContactsStep {
    /** Not yet: the service is starting or its permissions are being asked. */
    Wait,
    /** Show Android's contacts prompt (once ever). */
    Ask,
    /** Permission is there but contacts are still off: switch them on (no prompt). */
    SwitchOn,
    Nothing,
}

enum class HomeProblem {
    /** baresip's service hasn't finished starting yet. */
    Starting,
    /** The microphone (or notification) permission was refused. */
    PermissionsDenied,
}

data class HomeNumberRow(
    val id: String,
    val label: String,
    val number: String,
    val status: NumberStatus,
    val voicemailNew: Int,
    val isDefault: Boolean,
    val problem: NumberProblem? = null,
)
