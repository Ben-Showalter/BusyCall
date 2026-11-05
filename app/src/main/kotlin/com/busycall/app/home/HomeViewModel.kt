package com.busycall.app.home

import com.busycall.app.bridge.Bridge
import com.busycall.app.bridge.Bridges
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
        return HomeState(
            numbers = bridge.numbers().map {
                HomeNumberRow(it.id, it.label, it.number, it.status, it.voicemailNew, it.isDefault, it.problem)
            },
            recents = bridge.recents(RECENT_LIMIT).map {
                HomeRecentRow(
                    peerName = it.peerName,
                    peerUri = it.peerUri,
                    numberLabel = it.numberLabel,
                    numberId = it.numberId,
                    direction = it.direction,
                    missed = it.missed,
                    time = TimeText.label(it.timeMillis, nowMillis, zoneId),
                )
            },
        )
    }

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
        const val RECENT_LIMIT = 8
    }
}
