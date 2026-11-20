package com.busycall.app.home

import com.busycall.app.bridge.Bridge
import com.busycall.app.bridge.Bridges
import com.busycall.app.bridge.NumberStatus
import com.busycall.app.ui.TimeText
import java.time.ZoneId

/**
 * Home's logic. A plain class, not an androidx ViewModel: BusyCall screens use plain Activities,
 * and `configChanges` keeps them alive across a flip or rotation (guide §4).
 */
class HomeViewModel(
    private val bridge: Bridge = Bridges.current,
    private val now: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) {
    var permissionsDenied = false

    fun state(): HomeState {
        if (permissionsDenied) return HomeState(problem = HomeProblem.PermissionsDenied)
        if (!bridge.isRunning()) return HomeState(problem = HomeProblem.Starting)
        val nowMillis = now()
        val zoneId = zone()
        val numbers = bridge.numbers()
        return HomeState(
            trouble = numbers.filter { it.status == NumberStatus.NoConnection }.map {
                HomeNumberRow(it.id, it.label, it.number, it.status, it.voicemailNew, it.isDefault, it.problem)
            },
            noNumbers = numbers.isEmpty(),
            calls = bridge.recents(CALL_LIMIT).map { HomeCallRow(it, TimeText.label(it.timeMillis, nowMillis, zoneId)) },
        )
    }

    /** Call back, from the number the call was on (AGENTS §6). Returns the (fromId, dial) pair. */
    fun callBackTarget(row: HomeCallRow): Pair<String, String> = row.recent.numberId to row.recent.peerUri

    fun delete(row: HomeCallRow) = bridge.deleteRecent(row.recent)

    /**
     * Contacts at startup (AGENTS §7): ask once, as soon as the phone service is up (so the
     * microphone prompt has been answered first, guide §5: one at a time).
     */
    fun contactsStep(asked: Boolean, hasPermission: Boolean): ContactsStep = when {
        permissionsDenied || !bridge.isRunning() -> ContactsStep.Wait
        hasPermission -> if (bridge.contactsOn()) ContactsStep.Nothing else ContactsStep.SwitchOn
        asked -> ContactsStep.Nothing
        else -> ContactsStep.Ask
    }

    companion object {
        /** Rows are plain views, so the log is capped: the newest 200 calls. */
        const val CALL_LIMIT = 200
    }
}
