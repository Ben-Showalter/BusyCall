package com.busycall.app.bridge

import com.busycall.app.FakeBridge
import com.busycall.app.home.HomeViewModel
import com.busycall.app.numbers.NumbersViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RegistrationReasonTest {

    @Test fun `baresip reasons in plain words`() {
        assertEquals(NumberProblem.WrongPassword, RegistrationReason.of("401 Unauthorized"))
        assertEquals(NumberProblem.WrongPassword, RegistrationReason.of("403 Forbidden"))
        assertEquals(NumberProblem.WrongPassword, RegistrationReason.of("407 Proxy Authentication Required"))
        assertEquals(NumberProblem.NoAnswer, RegistrationReason.of("408 Request Timeout"))
        assertEquals(NumberProblem.NoAnswer, RegistrationReason.of("Connection timed out"))
        assertEquals(NumberProblem.ServerNotFound, RegistrationReason.of("Invalid argument"))
        assertEquals(NumberProblem.NoNetwork, RegistrationReason.of("Network is unreachable"))
        assertEquals(NumberProblem.Other("503 Service Unavailable"), RegistrationReason.of(" 503 Service Unavailable "))
        assertNull(RegistrationReason.of(""))
        assertNull(RegistrationReason.of(null))
    }

    @Test fun `home and numbers carry the reason`() {
        val n = NumberUi("sip:104@pbx", "Office", "104", "pbx", Transport.UDP, NumberStatus.NoConnection, 0, true,
            NumberProblem.WrongPassword)
        val bridge = FakeBridge(numbers = mutableListOf(n))
        assertEquals(NumberProblem.WrongPassword, HomeViewModel(bridge).state().numbers.single().problem)
        assertEquals(NumberProblem.WrongPassword, NumbersViewModel(bridge).state(false).numbers.single().problem)
    }
}
