package com.busycall.app.bridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SipAddressTest {

    @Test fun `aor with port and transport`() {
        val d = NumberDraft(server = " pbx.example.org ", port = 5061, user = "104 ", transport = Transport.TLS)
        assertEquals("sip:104@pbx.example.org:5061;transport=tls", SipAddress.aorOf(d))
    }

    @Test fun `aor without port`() {
        assertEquals("sip:7@10.0.0.5;transport=udp", SipAddress.aorOf(NumberDraft(server = "10.0.0.5", user = "7")))
    }

    @Test fun `voicemail number becomes a uri on the same server`() {
        assertEquals("sip:*97@pbx.example.org", SipAddress.voicemailUri(NumberDraft(server = "pbx.example.org", voicemail = "*97")))
        assertEquals("sip:vm@other.org", SipAddress.voicemailUri(NumberDraft(server = "x.org", voicemail = "sip:vm@other.org")))
        assertEquals("", SipAddress.voicemailUri(NumberDraft(server = "x.org")))
    }

    @Test fun `parse round-trips aorOf`() {
        val parts = SipAddress.parse("<sip:104@pbx.example.org:5061;transport=tls>")
        assertEquals(SipAddress.Parts("104", "pbx.example.org", 5061, Transport.TLS), parts)
    }

    @Test fun `parse ipv6 host`() {
        assertEquals(SipAddress.Parts("1", "[2001:db8::1]", 5060, null), SipAddress.parse("sip:1@[2001:db8::1]:5060"))
    }

    @Test fun `validation`() {
        assertTrue(SipAddress.isValidHost("pbx.example.org"))
        assertTrue(SipAddress.isValidHost("192.168.1.10"))
        assertFalse(SipAddress.isValidHost("pbx example.org"))
        assertFalse(SipAddress.isValidHost(""))
        assertTrue(SipAddress.isValidUser("104"))
        assertTrue(SipAddress.isValidUser("ben.s"))
        assertFalse(SipAddress.isValidUser("a;b"))
        assertFalse(SipAddress.isValidUser("a@b"))
        assertTrue(SipAddress.isValidPort(null))
        assertFalse(SipAddress.isValidPort(0))
        assertFalse(SipAddress.isValidPort(70000))
        assertTrue(SipAddress.isValidPassword("p@ss w&rd=1"))
        assertFalse(SipAddress.isValidPassword("has\"quote"))
        assertFalse(SipAddress.isValidPassword("x".repeat(65)))
        assertTrue(SipAddress.isValidName("Front desk"))
        assertFalse(SipAddress.isValidName("Office;1"))
    }
}
