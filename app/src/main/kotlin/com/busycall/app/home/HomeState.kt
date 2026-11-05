package com.busycall.app.home

import com.busycall.app.bridge.CallDirection
import com.busycall.app.bridge.NumberProblem
import com.busycall.app.bridge.NumberStatus
import com.busycall.app.ui.TimeLabel

/** Everything Home shows (PLAN.md S1). */
data class HomeState(
    val problem: HomeProblem? = null,
    val numbers: List<HomeNumberRow> = emptyList(),
    val recents: List<HomeRecentRow> = emptyList(),
)

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

data class HomeRecentRow(
    val peerName: String,
    val peerUri: String,
    val numberLabel: String,
    val numberId: String,
    val direction: CallDirection,
    val missed: Boolean,
    val time: TimeLabel,
)
