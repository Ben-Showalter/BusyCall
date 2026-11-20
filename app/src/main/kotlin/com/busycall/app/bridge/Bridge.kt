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
    /** Where the number is from ("Ohio"), when it's a phone number. */
    val place: Place? = null,
    /** True when the number is one of the phone's contacts. */
    val inContacts: Boolean = false,
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
    /** The id of the user's number the call is on (for + Call, Transfer and Park from it). */
    val numberId: String = "",
    /** A phone (SIM) call, run through upstream while BusyCall is the phone's dialer. */
    val isSim: Boolean = false,
    /** Where the caller's number is from ("Ohio"), when it's a phone number. */
    val place: Place? = null,
)

/** The phone's SIM as a way to call, while BusyCall is the default phone app. */
data class SimUi(
    /** Upstream's Mobile account id, used as the "from" of a SIM call. */
    val id: String,
    /** True when the SIM has mobile service. */
    val ready: Boolean,
)

/** How connecting two calls (attended transfer, park) went. */
enum class ConnectResult {
    Connected,
    /** The far end doesn't support SIP Replaces, so the two can't be joined this way. */
    NoReplaces,
    Failed,
}

/** Why a call couldn't be placed; each maps to a plain-language message. */
enum class PlaceProblem {
    ServiceNotRunning, NoNumber, InvalidAddress, PhoneBusy, Failed,
    /** An emergency number while the phone network is there: it must go out there (Emergency). */
    Emergency,
}

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
    /** Network help for call audio behind NAT (upstream's medianat). */
    val nat: NatMode = NatMode.Off,
    /** STUN server host (`nat-a.gdms.cloud`) or URI, used when [nat] isn't Off. */
    val stunServer: String = "",
    /** The phone system's call-park number (`700` on Asterisk-based systems); empty hides Park. */
    val parkNumber: String = DEFAULT_PARK_NUMBER,
    /** Call blocking for this number. */
    val blockMode: BlockMode = BlockMode.Off,
)

/** One blocked call, for the Blocked calls screen. */
data class BlockedUi(
    val numberId: String,
    /** Which of the user's numbers it was to. */
    val numberLabel: String,
    val peerUri: String,
    val peerName: String,
    val timeMillis: Long,
    val reason: BlockReason?,
)

/** Call park on Asterisk-based systems (Grandstream UCM included); a per-number setting. */
const val DEFAULT_PARK_NUMBER = "700"

/**
 * Network help for call audio (upstream's per-account `medianat`): needed when the phone and
 * the phone system can't reach each other's audio directly, e.g. Grandstream UCM RemoteConnect
 * (its guide: STUN `nat-a.gdms.cloud`). A setting any system can use (AGENTS §0.6).
 */
enum class NatMode(val param: String) {
    Off(""), Stun("stun"), Ice("ice");

    companion object {
        fun fromParam(value: String?): NatMode = values().firstOrNull { it.param.isNotEmpty() && it.param == value?.trim()?.lowercase() } ?: Off
    }
}

/** Why a number couldn't be saved; each maps to a plain-language message on screen. */
enum class SaveProblem {
    ServiceNotRunning, InvalidServer, InvalidUser, InvalidPort, InvalidPassword,
    InvalidName, NameTaken, AlreadyExists, AllocationFailed, InvalidStun,
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
     * Changes whenever [recents] would: a call added or removed, or the contacts reloaded. Cheap,
     * so Home can poll it each second and re-read the log only when it changed.
     */
    fun recentsVersion(): Long

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

    /**
     * Places a call to [dial] (a number or SIP address) from number [fromId] (null = ★ default).
     * With [holdCallId] it's a second call while that one is on hold, linked to it, so the two
     * can later be connected ([connectCalls]).
     */
    fun placeCall(activity: Activity, fromId: String?, dial: String, holdCallId: Long? = null): PlaceResult

    /** Blind transfer: sends call [callId] to [dial] (same number rules as [placeCall]). */
    fun transfer(callId: Long, dial: String): Boolean

    /** Attended transfer / park: connects a held call and the call placed after it. */
    fun connectCalls(callIdA: Long, callIdB: Long): ConnectResult

    /** The park number of number [numberId] (empty: no Park). */
    fun parkNumber(numberId: String): String

    fun answer(ctx: android.content.Context, callId: Long)
    fun decline(ctx: android.content.Context, callId: Long)
    fun hangup(ctx: android.content.Context, callId: Long)
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

    /** The phone contact with this number, as a `content://` lookup URI to open, or null. */
    fun contactUri(ctx: android.content.Context, peerUri: String): String?

    /**
     * True while BusyCall's END-key service keeps the app alive: upstream then shows no status
     * notification (UPSTREAM.md). False brings the notification back.
     */
    fun setKeptAlive(alive: Boolean)

    /** Where a number is from (area code / country code), or null. */
    fun placeOf(peerUri: String): Place?

    /** Deletes one call-log entry. */
    fun deleteRecent(recent: RecentUi)

    // ---- BusyCall as the phone's dialer (SIM calls through upstream's InCallService) ----

    /** True when BusyCall is the phone's default dialer, so SIM calls run through it. */
    fun isDefaultPhoneApp(ctx: android.content.Context): Boolean

    /** Asks Android to make BusyCall the default dialer (answer comes to onActivityResult, [DIALER_REQUEST]). */
    fun askToBeDefaultPhoneApp(activity: Activity)

    /**
     * Once BusyCall is the default dialer: asks for the phone permissions SIM calls need
     * ([PHONE_REQUEST]) and returns false, or adds the SIM to upstream's accounts and returns true.
     */
    fun startSimCalls(activity: Activity): Boolean

    /** The SIM while SIM calls run through BusyCall, else null. */
    fun sim(): SimUi?

    // ---- Emergency numbers (Emergency: phone network first, VoIP with no cell service) ----

    /** True when [dial] is an emergency number where the phone is (Android's list for the SIM's country). */
    fun isEmergencyNumber(ctx: android.content.Context, dial: String): Boolean

    /** The phone's cell connection right now. */
    fun cellState(ctx: android.content.Context): CellState

    /**
     * Places an emergency call on the phone network while BusyCall is the phone's dialer
     * (upstream's InCallService then shows it on BusyCall's call screen). False when BusyCall
     * isn't the dialer, or Android refused: the caller opens the phone's own dialer instead.
     */
    fun placeEmergencyCall(activity: Activity, dial: String): Boolean

    // ---- Call blocking (BusyCall's CallFilter through upstream's callFilter hook) ----

    fun blockMode(numberId: String): BlockMode
    fun setBlockMode(numberId: String, mode: BlockMode)
    /** Blocked calls, newest first. */
    fun blockedCalls(limit: Int): List<BlockedUi>
    /** Always let this caller ring (and forget it was blocked). */
    fun allowCaller(peerUri: String)
    /** Always block this caller. */
    fun blockCaller(peerUri: String)
    fun deleteBlocked(item: BlockedUi)

    companion object {
        const val CONTACTS_REQUEST = 4202
        const val DIALER_REQUEST = 4205
        const val PHONE_REQUEST = 4206
    }
}

object Bridges {
    /** Set by tests; null in the app. */
    @Volatile
    var override: Bridge? = null

    val current: Bridge
        get() = override ?: BaresipBridge
}
