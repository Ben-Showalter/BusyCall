package com.busycall.app.bridge

import com.busycall.app.FakeBridge
import com.busycall.app.numbers.NumberSetupViewModel
import com.busycall.app.numbers.SetupField
import com.busycall.app.numbers.setup.SetupCodeParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NatTest {

    @Test fun `STUN servers as baresip wants them`() {
        assertEquals("stun:nat-a.gdms.cloud", SipAddress.stunUri("nat-a.gdms.cloud"))
        assertEquals("stun:nat-a.gdms.cloud", SipAddress.stunUri(" stun:nat-a.gdms.cloud "))
        assertEquals("stun:stun.example.org:3478", SipAddress.stunUri("stun.example.org:3478"))
        assertEquals("stuns:stun.example.org", SipAddress.stunUri("stuns:stun.example.org"))
        assertNull(SipAddress.stunUri(""))
        assertNull(SipAddress.stunUri("not a host"))
        assertNull(SipAddress.stunUri("turn:relay.example.org"))
    }

    @Test fun `nat modes`() {
        assertEquals(NatMode.Stun, NatMode.fromParam("STUN"))
        assertEquals(NatMode.Ice, NatMode.fromParam("ice"))
        assertEquals(NatMode.Off, NatMode.fromParam(""))
        assertEquals(NatMode.Off, NatMode.fromParam("turn")) // not offered
        assertEquals(NatMode.Off, NumberDraft().nat)
    }

    @Test fun `setup links carry network help`() {
        val ok = SetupCodeParser.parse("busycall:add?v=1&server=c074.e.gdms.cloud&port=5061&user=104&transport=tls&nat=stun&stun=nat-a.gdms.cloud")
        val draft = (ok as SetupCodeParser.Result.Ok).draft
        assertEquals(NatMode.Stun, draft.nat)
        assertEquals("nat-a.gdms.cloud", draft.stunServer)
        // A STUN server alone means STUN.
        val alone = SetupCodeParser.parse("busycall:add?v=1&server=pbx&user=104&stun=nat-a.gdms.cloud") as SetupCodeParser.Result.Ok
        assertEquals(NatMode.Stun, alone.draft.nat)
        val none = SetupCodeParser.parse("busycall:add?v=1&server=pbx&user=104") as SetupCodeParser.Result.Ok
        assertEquals(NatMode.Off, none.draft.nat)
    }

    @Test fun `number setup asks for a STUN server when network help is on`() {
        val vm = NumberSetupViewModel(FakeBridge())
        val draft = NumberDraft(server = "pbx", user = "104", nat = NatMode.Stun)
        assertEquals(SaveProblem.InvalidStun, vm.check(draft, ""))
        assertEquals(SetupField.Stun, vm.fieldOf(SaveProblem.InvalidStun))
        assertNull(vm.check(draft.copy(stunServer = "nat-a.gdms.cloud"), ""))
        assertNull(vm.check(draft.copy(nat = NatMode.Off), "")) // off: no server needed
    }
}
