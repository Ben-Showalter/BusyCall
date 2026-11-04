package com.busycall.app.numbers.setup

import com.busycall.app.bridge.NumberDraft
import com.busycall.app.bridge.Transport
import com.busycall.app.numbers.setup.SetupCodeParser.Field
import com.busycall.app.numbers.setup.SetupCodeParser.Result
import org.junit.Assert.assertEquals
import org.junit.Test

class SetupCodeParserTest {

    private fun ok(text: String): NumberDraft {
        val result = SetupCodeParser.parse(text)
        check(result is Result.Ok) { "expected Ok, got $result" }
        return result.draft
    }

    // ---- BusyCall links (exactly what tools/setup-code/busycall-setup.html makes) ----

    @Test fun `page link with every field`() {
        val d = ok("busycall:add?v=1&name=Office&server=pbx.example.org&port=5061&user=104&auth=a104&pass=p%40ss%20w%26rd%3D1&transport=tls&vm=*97")
        assertEquals(
            NumberDraft("Office", "pbx.example.org", 5061, "104", "a104", "p@ss w&rd=1", Transport.TLS, "*97"),
            d,
        )
    }

    @Test fun `only server and user are required`() {
        val d = ok("busycall:add?v=1&server=10.0.4.2&user=2201")
        assertEquals(NumberDraft(server = "10.0.4.2", user = "2201"), d)
    }

    @Test fun `plus sign stays a plus`() {
        assertEquals("a+b", ok("busycall:add?v=1&server=x.org&user=1&pass=a+b").password)
    }

    @Test fun `link copied with spaces or quotes around it`() {
        assertEquals("104", ok("  \"busycall:add?v=1&server=x.org&user=104\"  ").user)
    }

    @Test fun `https link with the settings after the hash`() {
        val d = ok("https://ben.example/busycall#add?v=1&server=pbx.example.org&user=104&transport=tcp")
        assertEquals(Transport.TCP, d.transport)
        assertEquals("pbx.example.org", d.server)
    }

    @Test fun `scheme is case-insensitive`() {
        assertEquals("104", ok("BusyCall:add?v=1&server=x.org&user=104").user)
    }

    @Test fun `missing server or user`() {
        assertEquals(Result.Incomplete(Field.Server), SetupCodeParser.parse("busycall:add?v=1&user=104"))
        assertEquals(Result.Incomplete(Field.User), SetupCodeParser.parse("busycall:add?v=1&server=x.org"))
    }

    @Test fun `newer version is refused`() {
        assertEquals(Result.TooNew, SetupCodeParser.parse("busycall:add?v=2&server=x.org&user=1"))
    }

    @Test fun `unknown transport falls back to UDP`() {
        assertEquals(Transport.UDP, ok("busycall:add?v=1&server=x.org&user=1&transport=carrier-pigeon").transport)
    }

    // ---- baresip account lines ----

    @Test fun `baresip line with quoted parameters`() {
        val d = ok("<sip:104@pbx.example.org:5061;transport=tls>;auth_user=\"a104\";auth_pass=\"se;cret\";mwi=yes;vm_uri=\"sip:*97@pbx.example.org\";regint=900;extra=\"nickname=Office;last=empty\"")
        assertEquals(
            NumberDraft("Office", "pbx.example.org", 5061, "104", "a104", "se;cret", Transport.TLS, "*97"),
            d,
        )
    }

    @Test fun `baresip line with a display name and no password`() {
        val d = ok("\"Front Desk\" <sip:100@10.0.0.5>;regint=600")
        assertEquals(NumberDraft(name = "Front Desk", server = "10.0.0.5", user = "100"), d)
    }

    @Test fun `baresip auth user equal to the extension is dropped`() {
        assertEquals("", ok("<sip:104@x.org>;auth_user=104").authUser)
    }

    // ---- sip:user:password@host ----

    @Test fun `sip uri with password`() {
        val d = ok("sip:104:secret@pbx.example.org:5060;transport=udp")
        assertEquals(NumberDraft(server = "pbx.example.org", port = 5060, user = "104", password = "secret"), d)
    }

    @Test fun `sip uri without a user is incomplete`() {
        assertEquals(Result.Incomplete(Field.User), SetupCodeParser.parse("sip:pbx.example.org"))
    }

    // ---- not a setup code ----

    @Test fun `anything else is not a setup code`() {
        for (text in listOf("", "   ", "hello", "https://example.org/page", "tel:5550142", "mailto:a@b.c")) {
            assertEquals(text, Result.NotASetupCode, SetupCodeParser.parse(text))
        }
        assertEquals(Result.NotASetupCode, SetupCodeParser.parse(null))
    }
}
