package com.busycall.app.bridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PeerNameTest {

    private val contacts = listOf(
        ContactUi("Anna Becker", listOf(ContactNumberUi("Mobile", "tel:+13782343189", "+13782343189"))),
        ContactUi("Desk", listOf(ContactNumberUi("", "sip:alice@pbx", "alice@pbx"))),
    )
    private val index = PeerName.index(contacts)

    @Test fun `a phone number from any server shows as the number (owner's 3782343189@10_1_10_247)`() {
        assertEquals("(555) 234-0001", PeerName.of("sip:5552340001@10.1.10.247", emptyMap()))
        assertEquals("(555) 234-0001", PeerName.of("sip:+15552340001@c074.e.gdms.cloud;user=phone", emptyMap()))
        assertEquals("+442071234567", PeerName.of("sip:+442071234567@pbx", emptyMap()))
        assertEquals("1234567", PeerName.of("tel:1234567", emptyMap()))
    }

    @Test fun `matched to contacts by digits, with or without +1`() {
        assertEquals("Anna Becker", PeerName.of("sip:3782343189@10.1.10.247", index))
        assertEquals("Anna Becker", PeerName.of("sip:13782343189@pbx", index))
        assertEquals("Anna Becker", PeerName.of("<sip:+13782343189@pbx>", index))
        assertEquals("Anna Becker", PeerName.of("\"Somebody\" <sip:378-234-3189@pbx>", index))
    }

    @Test fun `SIP names and extensions keep upstream's naming`() {
        assertNull(PeerName.of("sip:alice@pbx", index))
        assertNull(PeerName.of("sip:104@pbx", index)) // a 3-digit extension isn't a phone number
        assertNull(PeerName.of("sip:anonymous@anonymous.invalid", index))
    }

    @Test fun `match keys and formats`() {
        assertEquals("3782343189", PeerName.matchKey("+1 (378) 234-3189"))
        assertEquals("442071234567", PeerName.matchKey("+442071234567"))
        assertEquals("(378) 234-3189", PeerName.format("13782343189"))
        assertEquals("+442071234567", PeerName.format("+442071234567"))
        assertEquals("3782343189", PeerName.phoneNumber("sip:3782343189@10.1.10.247"))
    }
}
