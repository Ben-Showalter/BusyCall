package com.busycall.app.bridge

import com.busycall.app.FakeBridge
import com.busycall.app.home.ContactsStep
import com.busycall.app.home.HomeViewModel
import org.junit.Assert.assertEquals
import org.junit.Test

class PlusRuleTest {

    @Test fun `drop the plus is the default and fixes +1 numbers`() {
        assertEquals(PlusRule.DropPlus, NumberDraft().plusRule)
        assertEquals("15551234567", PlusRule.DropPlus.apply("+15551234567"))
        assertEquals("tel:15551234567", PlusRule.DropPlus.apply("tel:+15551234567"))
    }

    @Test fun `keep and replace`() {
        assertEquals("tel:+15551234567", PlusRule.AsIs.apply("tel:+15551234567"))
        assertEquals("01144201234567", PlusRule.ReplaceWith("011").apply("+44201234567"))
        assertEquals("tel:0044201234567", PlusRule.ReplaceWith("00").apply("tel:+44201234567"))
    }

    @Test fun `numbers without a plus are never touched`() {
        for (rule in listOf(PlusRule.DropPlus, PlusRule.AsIs, PlusRule.ReplaceWith("011"))) {
            assertEquals("104", rule.apply("104"))
            assertEquals("sip:anna@pbx.example.org", rule.apply("sip:anna@pbx.example.org"))
            assertEquals("*97", rule.apply(" *97 "))
            assertEquals("tel:5551234", rule.apply("tel:5551234"))
        }
    }

    @Test fun `stored as a key and prefix`() {
        for (rule in listOf(PlusRule.DropPlus, PlusRule.AsIs, PlusRule.ReplaceWith("011"))) {
            assertEquals(rule, PlusRule.of(rule.key(), (rule as? PlusRule.ReplaceWith)?.prefix))
        }
        assertEquals(PlusRule.DropPlus, PlusRule.of(null, null)) // numbers added before this setting
        assertEquals(PlusRule.ReplaceWith("011"), PlusRule.of("replace", "0-1 1"))
    }

    @Test fun `contacts are asked once, after the service is up, then switched on`() {
        val bridge = FakeBridge(running = false)
        bridge.contactsAreOn = false
        val vm = HomeViewModel(bridge)
        assertEquals(ContactsStep.Wait, vm.contactsStep(asked = false, hasPermission = false))
        bridge.running = true
        assertEquals(ContactsStep.Ask, vm.contactsStep(asked = false, hasPermission = false))
        assertEquals(ContactsStep.Nothing, vm.contactsStep(asked = true, hasPermission = false)) // refused: never nag
        assertEquals(ContactsStep.SwitchOn, vm.contactsStep(asked = true, hasPermission = true))
        bridge.contactsAreOn = true
        assertEquals(ContactsStep.Nothing, vm.contactsStep(asked = true, hasPermission = true))
        vm.permissionsDenied = true
        assertEquals(ContactsStep.Wait, vm.contactsStep(asked = false, hasPermission = false))
    }
}
