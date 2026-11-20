package com.busycall.app.bridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Emergency numbers: phone network first, VoIP only with no cell service (owner, 2026-11-20). */
class EmergencyTest {

    private fun number(id: String, status: NumberStatus = NumberStatus.Registered, star: Boolean = false) =
        NumberUi("sip:$id@pbx", "N$id", id, "pbx", Transport.TLS, status, 0, star)

    private val office = number("104", star = true)
    private val home = number("555")
    private val android = setOf("911", "112")

    @Test fun androidListDecides() {
        assertTrue(Emergency.isEmergency("911") { it in android })
        assertTrue(Emergency.isEmergency("tel:112") { it in android })
        assertTrue(Emergency.isEmergency("sip:911@10.1.10.247;transport=tls") { it in android })
        assertFalse(Emergency.isEmergency("104") { it in android })
        assertFalse(Emergency.isEmergency("") { true })
    }

    @Test fun anExtensionAndroidDoesNotListIsNotEmergency() {
        // 110 is a common phone-system extension; only Android's list for the SIM's country counts.
        assertFalse(Emergency.isEmergency("110") { it in android })
    }

    @Test fun fallbackListWhenAndroidCheckThrows() {
        val broken: (String) -> Boolean = { throw IllegalStateException("no telephony") }
        assertTrue(Emergency.isEmergency("911", broken))
        assertTrue(Emergency.isEmergency("112", broken))
        assertFalse(Emergency.isEmergency("110", broken))
    }

    @Test fun anyCellServiceMeansPhoneNetwork() {
        for (cell in listOf(CellState.InService, CellState.EmergencyOnly, CellState.RadioOff, CellState.Unknown)) {
            assertEquals(cell.name, EmergencyRoute.PhoneNetwork, Emergency.route(cell, listOf(office, home)))
            assertFalse(Emergency.allowsVoip(cell, listOf(office, home)))
        }
    }

    @Test fun noServiceFallsBackToTheStarNumber() {
        assertEquals(EmergencyRoute.Voip(office), Emergency.route(CellState.NoService, listOf(home, office)))
        assertEquals(EmergencyRoute.Voip(office), Emergency.route(CellState.NoRadio, listOf(home, office)))
        assertTrue(Emergency.allowsVoip(CellState.NoService, listOf(home, office)))
    }

    @Test fun starNotConnectedUsesTheFirstConnected() {
        val starDown = number("104", NumberStatus.NoConnection, star = true)
        assertEquals(EmergencyRoute.Voip(home), Emergency.route(CellState.NoService, listOf(starDown, home)))
    }

    @Test fun nothingConnectedStillTriesThePhoneNetwork() {
        val down = number("104", NumberStatus.Connecting, star = true)
        assertEquals(EmergencyRoute.PhoneNetwork, Emergency.route(CellState.NoService, listOf(down)))
        assertEquals(EmergencyRoute.PhoneNetwork, Emergency.route(CellState.NoService, emptyList()))
        assertFalse(Emergency.allowsVoip(CellState.NoService, emptyList()))
    }

    @Test fun digitsFromAnyForm() {
        assertEquals("911", Emergency.digitsOf(" tel:9 1 1 "))
        assertEquals("911", Emergency.digitsOf("sip:911@pbx"))
    }
}
