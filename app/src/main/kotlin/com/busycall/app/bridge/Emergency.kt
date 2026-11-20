package com.busycall.app.bridge

/** The phone's cell connection, as emergency routing needs it. */
enum class CellState {
    InService,
    /** No usable SIM or a locked one, but a tower takes emergency calls. */
    EmergencyOnly,
    /** Airplane mode: Android turns the radio back on for an emergency call itself. */
    RadioOff,
    /** No coverage at all. */
    NoService,
    /** The phone has no cell radio. */
    NoRadio,
    /** Not known yet (the listener hasn't reported). Treated as in service. */
    Unknown,
}

/** Where an emergency call goes out. */
sealed class EmergencyRoute {
    object PhoneNetwork : EmergencyRoute()
    /** No cell service: over this VoIP number (owner, 2026-11-20: still fall back). */
    data class Voip(val number: NumberUi) : EmergencyRoute()
}

/**
 * Emergency numbers (911, 112, …) go out on the phone network, which gives the caller's location
 * and needs no phone system. With no cell service at all they go out over a connected VoIP number
 * instead of failing. Pure and unit-tested; the bridge feeds it.
 */
object Emergency {

    /** Used only if Android's own check fails. */
    val FALLBACK_NUMBERS = setOf("911", "112")

    /** The plain number from what was dialed: `tel:911`, `sip:911@pbx`, "9 1 1". */
    fun digitsOf(dial: String): String =
        dial.trim().removePrefix("tel:").removePrefix("sip:").substringBefore('@').substringBefore(';')
            .filter { it.isDigit() || it == '+' || it == '*' || it == '#' }

    /** [androidCheck] is Android's list for the SIM's country (it may throw on some phones). */
    fun isEmergency(dial: String, androidCheck: (String) -> Boolean): Boolean {
        val number = digitsOf(dial)
        if (number.isEmpty()) return false
        return try {
            androidCheck(number)
        } catch (e: Exception) {
            number in FALLBACK_NUMBERS
        }
    }

    /** [numbers] are the user's SIP numbers (★ marked by isDefault). */
    fun route(cell: CellState, numbers: List<NumberUi>): EmergencyRoute {
        val noCell = cell == CellState.NoService || cell == CellState.NoRadio
        if (!noCell) return EmergencyRoute.PhoneNetwork
        val connected = numbers.filter { it.status == NumberStatus.Registered }
        val voip = connected.firstOrNull { it.isDefault } ?: connected.firstOrNull()
        // Nothing connected either: the phone network anyway (Android keeps trying).
        return voip?.let { EmergencyRoute.Voip(it) } ?: EmergencyRoute.PhoneNetwork
    }

    /** The last guard in placeCall: may this emergency call go out on a SIP number? */
    fun allowsVoip(cell: CellState, numbers: List<NumberUi>): Boolean =
        route(cell, numbers) is EmergencyRoute.Voip
}
