package com.busycall.app.bridge

import android.app.Activity

/** Registration state of one number, as the user sees it. */
enum class NumberStatus { Registered, Connecting, NoConnection, NotRegistering }

enum class Transport(val param: String) {
    UDP("udp"), TCP("tcp"), TLS("tls");

    companion object {
        fun fromParam(value: String?): Transport? = values().firstOrNull { it.param.equals(value?.trim(), ignoreCase = true) }
    }
}

/** One SIP number (a baresip account), display-ready. */
data class NumberUi(
    /** Stable id: the account's SIP address (AOR). */
    val id: String,
    /** The user's label ("Office"), or the extension when there is none. */
    val label: String,
    /** Extension or number ("104"). */
    val number: String,
    val server: String,
    val transport: Transport,
    val status: NumberStatus,
    val voicemailNew: Int,
    /** The ★ number: the Dialer calls from it unless told otherwise. */
    val isDefault: Boolean,
)

enum class CallDirection { In, Out }

/** One call-log entry, display-ready except for the time (formatted by the screen's view model). */
data class RecentUi(
    /** Contact name if known, else the number. */
    val peerName: String,
    val peerUri: String,
    /** Which of the user's numbers the call was on. */
    val numberLabel: String,
    val direction: CallDirection,
    val missed: Boolean,
    /** When the call happened, epoch millis; null if unknown. */
    val timeMillis: Long?,
)

/** What the user typed or a setup code contained, before it becomes a baresip account. */
data class NumberDraft(
    val name: String = "",
    val server: String = "",
    val port: Int? = null,
    val user: String = "",
    val authUser: String = "",
    val password: String = "",
    val transport: Transport = Transport.UDP,
    val voicemail: String = "",
)

/** Why a number couldn't be saved; each maps to a plain-language message on screen. */
enum class SaveProblem {
    ServiceNotRunning, InvalidServer, InvalidUser, InvalidPort, InvalidPassword,
    InvalidName, NameTaken, AlreadyExists, AllocationFailed,
}

sealed class SaveResult {
    data class Saved(val id: String) : SaveResult()
    data class Failed(val problem: SaveProblem) : SaveResult()
}

/**
 * Everything the BusyCall screens need from baresip's backend. Screens and view models use
 * this interface only; [BaresipBridge] is the one implementation that touches com.tutpro.baresip
 * (AGENTS.md §2). Tests swap in a fake through [Bridges.override].
 */
interface Bridge {
    /** True once baresip's service is up and its accounts are loaded. */
    fun isRunning(): Boolean

    /** Asks for the permissions baresip needs (if missing) and starts its service. */
    fun startService(activity: Activity)

    fun numbers(): List<NumberUi>
    fun recents(limit: Int): List<RecentUi>

    /**
     * Temporary, until BusyCall's own Dialer and call screens exist: opens upstream's screens,
     * optionally with a number or SIP address to dial (ACTION_DIAL, as upstream handles it).
     */
    fun openClassicScreens(activity: Activity, dial: String? = null)

    /** The saved settings of a number, for editing. Password is never returned. */
    fun draftOf(id: String): NumberDraft?

    fun addNumber(draft: NumberDraft): SaveResult
    fun updateNumber(id: String, draft: NumberDraft): SaveResult
    fun deleteNumber(id: String)
    fun setDefault(id: String)
    /** Registers again (Retry after "No connection"). */
    fun retry(id: String)
}

object Bridges {
    /** Set by tests; null in the app. */
    @Volatile
    var override: Bridge? = null

    val current: Bridge
        get() = override ?: BaresipBridge
}
