package com.busycall.app.calllog

import com.busycall.app.bridge.Bridge
import com.busycall.app.bridge.Bridges
import com.busycall.app.bridge.PeerName
import com.busycall.app.bridge.RecentUi
import com.busycall.app.ui.TimeLabel
import com.busycall.app.ui.TimeText
import java.time.ZoneId

/** What Call details offers below Call back, in order. */
enum class DetailAction { AddToContacts, OpenContact, Block, Delete }

data class CallDetailsState(val recent: RecentUi, val time: TimeLabel, val actions: List<DetailAction>)

/**
 * Call details (opened with OK on a Home row): one call, where the number is from, and what to
 * do with it. Plain class (see HomeViewModel). The call is found again by its [peerUri] and
 * [stopMillis], which identify a call-log entry.
 */
class CallDetailsViewModel(
    private val peerUri: String,
    private val stopMillis: Long,
    private val bridge: Bridge = Bridges.current,
    private val now: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) {
    /** Null once the entry is gone (deleted, or the log was cleared). */
    fun state(): CallDetailsState? {
        if (!bridge.isRunning()) return null
        val recent = bridge.recents(LOOKUP_LIMIT).firstOrNull { it.peerUri == peerUri && it.stopMillis == stopMillis }
            ?: return null
        val actions = buildList {
            // Only phone numbers go into the phone's contacts (owner, 2026-11-20).
            if (PeerName.phoneNumber(recent.peerUri) != null) {
                add(if (recent.inContacts) DetailAction.OpenContact else DetailAction.AddToContacts)
            }
            add(DetailAction.Block)
            add(DetailAction.Delete)
        }
        return CallDetailsState(recent, TimeText.label(recent.timeMillis, now(), zone()), actions)
    }

    /** Call back, from the number the call was on (AGENTS §6). Returns the (fromId, dial) pair. */
    fun callBackTarget(recent: RecentUi): Pair<String, String> = recent.numberId to recent.peerUri

    /** The number to put in a contact: `+13782343189` or `3782343189`. */
    fun contactNumber(recent: RecentUi): String = PeerName.phoneNumber(recent.peerUri) ?: PeerName.userPart(recent.peerUri)

    fun delete(recent: RecentUi) = bridge.deleteRecent(recent)
    fun block(recent: RecentUi) = bridge.blockCaller(recent.peerUri)

    companion object {
        const val LOOKUP_LIMIT = 200
    }
}
