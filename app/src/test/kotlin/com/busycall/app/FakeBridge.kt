package com.busycall.app

import android.app.Activity
import com.busycall.app.bridge.Bridge
import com.busycall.app.bridge.CallState
import com.busycall.app.bridge.CallUi
import com.busycall.app.bridge.ContactUi
import com.busycall.app.bridge.PlaceProblem
import com.busycall.app.bridge.PlaceResult
import com.busycall.app.bridge.NumberDraft
import com.busycall.app.bridge.NumberUi
import com.busycall.app.bridge.RecentUi
import com.busycall.app.bridge.SaveProblem
import com.busycall.app.bridge.SaveResult
import com.busycall.app.bridge.SipAddress

/** In-memory stand-in for baresip's backend (AGENTS.md §11: tests never touch the real service). */
class FakeBridge(
    var running: Boolean = true,
    val numbers: MutableList<NumberUi> = mutableListOf(),
    val recents: MutableList<RecentUi> = mutableListOf(),
) : Bridge {
    val saved = mutableListOf<NumberDraft>()
    val deleted = mutableListOf<String>()
    var defaultId: String? = null
    val retried = mutableListOf<String>()
    var nextProblem: SaveProblem? = null

    override fun isRunning() = running
    override fun startService(activity: Activity) {}
    override fun numbers(): List<NumberUi> = numbers.map { it.copy(isDefault = it.id == (defaultId ?: numbers.firstOrNull()?.id)) }
    var recentsReads = 0
    var recentsVersionBump = 0L
    override fun recents(limit: Int): List<RecentUi> { recentsReads++; return recents.take(limit) }
    override fun recentsVersion(): Long = recents.hashCode().toLong() + recentsVersionBump
    override fun openClassicScreens(activity: Activity, dial: String?) {}
    override fun draftOf(id: String): NumberDraft? = numbers.firstOrNull { it.id == id }?.let {
        NumberDraft(name = it.label, server = it.server, user = it.number, transport = it.transport)
    }

    override fun addNumber(draft: NumberDraft): SaveResult {
        nextProblem?.let { return SaveResult.Failed(it) }
        saved.add(draft)
        return SaveResult.Saved(SipAddress.aorOf(draft))
    }

    override fun updateNumber(id: String, draft: NumberDraft): SaveResult = addNumber(draft)
    override fun deleteNumber(id: String) { deleted.add(id); numbers.removeAll { it.id == id } }
    override fun setDefault(id: String) { defaultId = id }
    override fun retry(id: String) { retried.add(id) }

    // ---- Calls ----
    val calls = mutableListOf<CallUi>()
    val placed = mutableListOf<Pair<String?, String>>()
    val digits = StringBuilder()
    val answered = mutableListOf<Long>()
    val declined = mutableListOf<Long>()
    val hungUp = mutableListOf<Long>()
    var placeProblem: PlaceProblem? = null
    var micMuted = false
    var speakerOn = false
    var silenced = 0

    override fun calls(): List<CallUi> = calls.toList()
    val heldFor = mutableListOf<Long?>()
    override fun placeCall(activity: Activity, fromId: String?, dial: String, holdCallId: Long?): PlaceResult {
        placeProblem?.let { return PlaceResult.Failed(it) }
        placed.add(fromId to dial)
        heldFor.add(holdCallId)
        return PlaceResult.Placed
    }

    val transfers = mutableListOf<Pair<Long, String>>()
    val connected = mutableListOf<Pair<Long, Long>>()
    var transferWorks = true
    var parkNumbers = mutableMapOf<String, String>()
    override fun transfer(callId: Long, dial: String): Boolean { transfers.add(callId to dial); return transferWorks }
    var defaultPhoneApp = false
    val blockModes = mutableMapOf<String, com.busycall.app.bridge.BlockMode>()
    val blockedList = mutableListOf<com.busycall.app.bridge.BlockedUi>()
    val allowedCallers = mutableListOf<String>()
    val blockedCallers = mutableListOf<String>()
    override fun blockMode(numberId: String) = blockModes[numberId] ?: com.busycall.app.bridge.BlockMode.Off
    override fun setBlockMode(numberId: String, mode: com.busycall.app.bridge.BlockMode) { blockModes[numberId] = mode }
    override fun blockedCalls(limit: Int) = blockedList.take(limit)
    override fun allowCaller(peerUri: String) { allowedCallers.add(peerUri) }
    override fun blockCaller(peerUri: String) { blockedCallers.add(peerUri) }
    override fun deleteBlocked(item: com.busycall.app.bridge.BlockedUi) { blockedList.remove(item) }
    var simUi: com.busycall.app.bridge.SimUi? = null
    override fun isDefaultPhoneApp(ctx: android.content.Context) = defaultPhoneApp
    override fun askToBeDefaultPhoneApp(activity: Activity) {}
    override fun startSimCalls(activity: Activity): Boolean = true
    override fun sim() = simUi

    var emergencyNumbers = setOf("911", "112")
    var cell = com.busycall.app.bridge.CellState.InService
    var emergencyPlaced = mutableListOf<String>()
    override fun isEmergencyNumber(ctx: android.content.Context, dial: String) =
        com.busycall.app.bridge.Emergency.isEmergency(dial) { it in emergencyNumbers }
    override fun cellState(ctx: android.content.Context) = cell
    override fun placeEmergencyCall(activity: Activity, dial: String): Boolean {
        if (!defaultPhoneApp) return false
        emergencyPlaced.add(dial); return true
    }

    var connectResult = com.busycall.app.bridge.ConnectResult.Connected
    override fun connectCalls(callIdA: Long, callIdB: Long): com.busycall.app.bridge.ConnectResult {
        connected.add(callIdA to callIdB); return connectResult
    }
    override fun parkNumber(numberId: String): String = parkNumbers[numberId] ?: com.busycall.app.bridge.DEFAULT_PARK_NUMBER
    override fun answer(ctx: android.content.Context, callId: Long) { answered.add(callId) }
    override fun decline(ctx: android.content.Context, callId: Long) { declined.add(callId) }
    override fun hangup(ctx: android.content.Context, callId: Long) { hungUp.add(callId) }
    var alive = false
    override fun setKeptAlive(alive: Boolean) { this.alive = alive }
    override fun sendDigit(callId: Long, digit: Char) { digits.append(digit) }
    override fun isMuted() = micMuted
    override fun setMuted(muted: Boolean) { micMuted = muted }
    override fun isSpeakerOn() = speakerOn
    override fun toggleSpeaker() { speakerOn = !speakerOn }
    override fun hold(callId: Long) = setState(callId, CallState.OnHold)
    override fun resume(callId: Long) = setState(callId, CallState.Connected)
    override fun silenceRinger() { silenced++ }

    // ---- Contacts and call log ----
    var contactsAreOn = true
    val contacts = mutableListOf<ContactUi>()
    val deletedRecents = mutableListOf<RecentUi>()

    override fun contactsOn() = contactsAreOn
    var contactsPermission = true
    override fun hasContactsPermission(ctx: android.content.Context) = contactsPermission
    override fun turnContactsOn(activity: Activity): Boolean { contactsAreOn = true; return true }
    override fun contacts(): List<ContactUi> = if (contactsAreOn) contacts.toList() else emptyList()
    val places = mutableMapOf<String, com.busycall.app.bridge.Place>()
    override fun placeOf(peerUri: String) = places[peerUri]
    override fun contactUri(ctx: android.content.Context, peerUri: String): String? = null
    override fun deleteRecent(recent: RecentUi) { deletedRecents.add(recent); recents.remove(recent) }

    fun setState(callId: Long, state: CallState) {
        val i = calls.indexOfFirst { it.id == callId }
        if (i >= 0) calls[i] = calls[i].copy(state = state)
    }
}
