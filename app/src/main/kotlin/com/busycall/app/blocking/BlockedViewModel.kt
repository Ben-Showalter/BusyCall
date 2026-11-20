package com.busycall.app.blocking

import com.busycall.app.bridge.Bridge
import com.busycall.app.bridge.BlockedUi
import com.busycall.app.bridge.Bridges
import com.busycall.app.ui.TimeLabel
import com.busycall.app.ui.TimeText
import java.time.ZoneId

data class BlockedRow(val item: BlockedUi, val time: TimeLabel)

/** Blocked calls (owner, 2026-11-20); plain class (see HomeViewModel). */
class BlockedViewModel(
    private val bridge: Bridge = Bridges.current,
    private val now: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) {
    fun rows(): List<BlockedRow> {
        if (!bridge.isRunning()) return emptyList()
        val n = now()
        val z = zone()
        return bridge.blockedCalls(LIMIT).map { BlockedRow(it, TimeText.label(it.timeMillis, n, z)) }
    }

    /** Unblock: this caller always rings from now on, and the entry goes away. */
    fun unblock(row: BlockedRow) {
        bridge.allowCaller(row.item.peerUri)
        bridge.deleteBlocked(row.item)
    }

    fun delete(row: BlockedRow) = bridge.deleteBlocked(row.item)

    companion object {
        const val LIMIT = 200
    }
}
