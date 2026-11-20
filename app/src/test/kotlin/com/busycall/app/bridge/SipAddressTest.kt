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

    @Test fun `calls on the number's own server keep its port and transport (RemoteConnect)`() {
        val rc = "sip:104@c074.e.gdms.cloud:5061;transport=tls"
        assertEquals("sip:*97@c074.e.gdms.cloud:5061;transport=tls", SipAddress.withAccountRoute("sip:*97@c074.e.gdms.cloud", rc))
        assertEquals(
            "sip:15550198@c074.e.gdms.cloud:5061;user=phone;transport=tls",
            SipAddress.withAccountRoute("sip:15550198@c074.e.gdms.cloud;user=phone", rc),
        )
        // The <…> form baresip keeps as luri works too.
        assertEquals("sip:105@c074.e.gdms.cloud:5061;transport=tls", SipAddress.withAccountRoute("sip:105@c074.e.gdms.cloud", "<$rc>"))
    }

    @Test fun `other addresses are left alone`() {
        val rc = "sip:104@c074.e.gdms.cloud:5061;transport=tls"
        assertEquals("sip:anna@other.example.org", SipAddress.withAccountRoute("sip:anna@other.example.org", rc))
        assertEquals("sip:105@c074.e.gdms.cloud:5070", SipAddress.withAccountRoute("sip:105@c074.e.gdms.cloud:5070", rc))
        // An address that names its own transport keeps it (only the port is added).
        assertEquals("sip:105@c074.e.gdms.cloud:5061;transport=tcp", SipAddress.withAccountRoute("sip:105@c074.e.gdms.cloud;transport=tcp", rc))
        // A plain UDP number on the standard port: nothing to add.
        assertEquals("sip:*97@10.1.10.247", SipAddress.withAccountRoute("sip:*97@10.1.10.247", "sip:104@10.1.10.247;transport=udp"))
        assertEquals("tel:5550198", SipAddress.withAccountRoute("tel:5550198", rc))
    }
}
