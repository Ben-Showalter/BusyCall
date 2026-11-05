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
    /** Why the last registration failed, while it still has no connection; null otherwise. */
    val problem: NumberProblem? = null,
)

enum class CallDirection { In, Out }

/** One call-log entry, display-ready except for the time (formatted by the screen's view model). */
data class RecentUi(
    /** Contact name if known, else the number. */
    val peerName: String,
    val peerUri: String,
    /** Which of the user's numbers the call was on. */
    val numberLabel: String,
    /** That number's id, to call back from it (AGENTS §6). */
    val numberId: String,
    val direction: CallDirection,
    val missed: Boolean,
    /** When the call happened, epoch millis; null if unknown. */
    val timeMillis: Long?,
    /** When it was answered (null if never) and when it ended: together they identify the entry to delete. */
    val startMillis: Long? = null,
    val stopMillis: Long = 0L,
)

/** One of a contact's numbers or SIP addresses. */
data class ContactNumberUi(
    /** The phone's label for it ("Mobile", "Work"), or empty. */
    val label: String,
    /** What to dial: `tel:+15550198` or `sip:anna@pbx`. */
    val uri: String,
    /** How to show it: "+15550198" or "anna@pbx". */
    val display: String,
)

/** One contact, display-ready. */
data class ContactUi(val name: String, val numbers: List<ContactNumberUi>)

/** A call's state, as the screens show it. */
enum class CallState {
    /** Incoming, not answered yet. */
    Ringing,
    /** Outgoing, not answered yet. */
    Dialing,
    /** Answered, audio starting. */
    Connecting,
    Connected,
    OnHold,
    Ended,
}

/** One call, display-ready except for the duration (formatted by the view model). */
data class CallUi(
    /** baresip's call pointer: stable while the call exists. */
    val id: Long,
    val peerName: String,
    val peerUri: String,
    /** Which of the user's numbers the call is on ("Office · 104"). */
    val numberLabel: String,
    val incoming: Boolean,
    val state: CallState,
    val durationSec: Int,
)

/** Why a call couldn't be placed; each maps to a plain-language message. */
enum class PlaceProblem { ServiceNotRunning, NoNumber, InvalidAddress, PhoneBusy, Failed }

sealed class PlaceResult {
    object Placed : PlaceResult()
    data class Failed(val problem: PlaceProblem) : PlaceResult()
}

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
    /** What to do with numbers starting with + when calling from this number. */
    val plusRule: PlusRule = PlusRule.DropPlus,
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

    // ---- Calls (all through upstream's own call functions) ----

    /** All current calls, newest last. */
    fun calls(): List<CallUi>

    /** Places a call to [dial] (a number or SIP address) from number [fromId] (null = ★ default). */
    fun placeCall(activity: Activity, fromId: String?, dial: String): PlaceResult

    fun answer(activity: Activity, callId: Long)
    fun decline(activity: Activity, callId: Long)
    fun hangup(activity: Activity, callId: Long)
    /** Sends one keypad tone (0-9, *, #) on the call. */
    fun sendDigit(callId: Long, digit: Char)
    fun isMuted(): Boolean
    fun setMuted(muted: Boolean)
    fun isSpeakerOn(): Boolean
    fun toggleSpeaker()
    fun hold(callId: Long)
    fun resume(callId: Long)
    /** Stops the ringtone of an incoming call without declining it. */
    fun silenceRinger()

    // ---- Contacts and call log (upstream's Contact and CallHistory) ----

    /** True when the phone's contacts are being read (upstream's contacts mode isn't "baresip"). */
    fun contactsOn(): Boolean

    /** True when BusyCall may read (and, as upstream needs, write) the phone's contacts. */
    fun hasContactsPermission(ctx: android.content.Context): Boolean

    /**
     * Starts reading the phone's contacts. If READ_CONTACTS / WRITE_CONTACTS are missing, asks for
     * them (request code [CONTACTS_REQUEST]) and returns false; the screen calls again on the result.
     */
    fun turnContactsOn(activity: Activity): Boolean

    /** Contacts, sorted by name (favourites first, as upstream sorts them). */
    fun contacts(): List<ContactUi>

    /** Deletes one call-log entry. */
    fun deleteRecent(recent: RecentUi)

    companion object {
        const val CONTACTS_REQUEST = 4202
    }
}

object Bridges {
    /** Set by tests; null in the app. */
    @Volatile
    var override: Bridge? = null

    val current: Bridge
        get() = override ?: BaresipBridge
}
