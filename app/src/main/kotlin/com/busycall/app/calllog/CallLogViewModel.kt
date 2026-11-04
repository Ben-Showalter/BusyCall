package com.busycall.app.calllog

import com.busycall.app.bridge.Bridge
import com.busycall.app.bridge.Bridges
import com.busycall.app.bridge.RecentUi
import com.busycall.app.ui.TimeLabel
import com.busycall.app.ui.TimeText
import java.time.ZoneId

data class CallLogRow(val recent: RecentUi, val time: TimeLabel)

data class CallLogState(val running: Boolean, val rows: List<CallLogRow>)

/** Call log logic (PLAN.md S6); plain class (see HomeViewModel). */
class CallLogViewModel(
    private val bridge: Bridge = Bridges.current,
    private val now: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) {
    fun state(): CallLogState {
        if (!bridge.isRunning()) return CallLogState(running = false, rows = emptyList())
        val nowMillis = now()
        val zoneId = zone()
        return CallLogState(
            running = true,
            rows = bridge.recents(LIMIT).map { CallLogRow(it, TimeText.label(it.timeMillis, nowMillis, zoneId)) },
        )
    }

    /** Call back, from the number the call was on (AGENTS §6). Returns the (fromId, dial) pair. */
    fun callBackTarget(row: CallLogRow): Pair<String, String> = row.recent.numberId to row.recent.peerUri

    fun delete(row: CallLogRow) = bridge.deleteRecent(row.recent)

    companion object {
        /** Rows are plain views, so the list is capped: the newest 200 calls. */
        const val LIMIT = 200
    }
}
