package com.busycall.app

import com.busycall.app.bridge.CallDirection
import com.busycall.app.bridge.ContactNumberUi
import com.busycall.app.bridge.ContactUi
import com.busycall.app.bridge.NumberStatus
import com.busycall.app.bridge.NumberUi
import com.busycall.app.bridge.PlaceResult
import com.busycall.app.bridge.RecentUi
import com.busycall.app.bridge.Transport
import com.busycall.app.calllog.CallLogViewModel
import com.busycall.app.contacts.ContactsViewModel
import com.busycall.app.dialer.DialOutcome
import com.busycall.app.dialer.DialerViewModel
import com.busycall.app.dialer.KeypadMatcher
import com.busycall.app.ui.TimeLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class ContactsTest {

    private fun tel(label: String, number: String) = ContactNumberUi(label, "tel:$number", number)

    private val anna = ContactUi("Anna Becker", listOf(tel("Mobile", "+15550198"), tel("Work", "5550100")))
    private val connor = ContactUi("Connor O'Neil", listOf(tel("", "5552660")))
    private val eloise = ContactUi("Éloïse Durand", listOf(tel("Home", "5550111")))
    private val bob = ContactUi("Bob", listOf(tel("Mobile", "2661234")))

    // ---- KeypadMatcher (PLAN §3.3) ----

    @Test fun `letters map to their keys, accents folded`() {
        assertEquals("232537", KeypadMatcher.toDigits("Becker"))
        assertEquals("356473", KeypadMatcher.toDigits("Éloïse"))
        assertEquals('7', KeypadMatcher.digitOf('ß'))
        assertEquals('9', KeypadMatcher.digitOf('Z'))
        assertEquals('5', KeypadMatcher.digitOf('5'))
        assertNull(KeypadMatcher.digitOf('-'))
    }

    @Test fun `266 matches names spelled by the keys and numbers containing it`() {
        val annaMobile = anna.copy(numbers = anna.numbers.take(1))
        val m = KeypadMatcher(listOf(annaMobile, connor, bob))
        val found = m.match("266")
        // A-N-N and C-O-N are both on 2-6-6 (first words); Bob only by number.
        assertEquals(listOf("Anna Becker", "Connor O'Neil", "Bob"), found.map { it.name })
        assertEquals(0..2, found[0].nameHighlight) // "Ann"
        assertNull(found[0].numberHighlight)
        // Bob's number starts with 266: matched by number, underlined there.
        assertNull(found[2].nameHighlight)
        assertEquals(0..2, found[2].numberHighlight)
    }

    @Test fun `later words match, ranked after first words`() {
        val m = KeypadMatcher(listOf(anna, ContactUi("Bea", listOf(tel("", "1")))))
        val found = m.match("232") // "Bec"ker and "Bea"
        assertEquals(listOf("Bea", "Anna Becker", "Anna Becker"), found.map { it.name })
        assertEquals(5..7, found[1].nameHighlight)
    }

    @Test fun `accented names match their plain letters`() {
        val found = KeypadMatcher(listOf(eloise)).match("356")
        assertEquals("Éloïse Durand", found.single().name)
        assertEquals(0..2, found.single().nameHighlight)
    }

    @Test fun `each of a contact's numbers is its own row, up to three`() {
        val found = KeypadMatcher(listOf(anna, connor, eloise, bob)).match("2")
        assertEquals(3, found.size)
        assertEquals(listOf("Mobile", "Work"), found.take(2).map { it.label })
        assertEquals("Anna Becker", found[1].name)
    }

    @Test fun `short digit runs don't match numbers, symbols match nothing`() {
        val m = KeypadMatcher(listOf(bob))
        assertTrue(m.match("12").isEmpty()) // no name has 1, and 2 digits never search numbers
        assertTrue(m.match("").isEmpty())
        assertTrue(m.match("*26").isEmpty())
        assertTrue(m.match("+").isEmpty())
        assertEquals("Bob", m.match("+266").single().name)
    }

    @Test fun `number match is underlined in the shown number, plus skipped`() {
        val found = KeypadMatcher(listOf(anna)).match("0198")
        assertEquals("+15550198", found.single().display)
        assertEquals(5..8, found.single().numberHighlight)
    }

    @Test fun `call log numbers are matched too, once`() {
        val m = KeypadMatcher(
            listOf(bob),
            listOf(
                KeypadMatcher.DialEntry("Pizza", "sip:7492@pbx", "7492"),
                KeypadMatcher.DialEntry("2661234", "sip:2661234@pbx", "2661234"), // Bob's, already listed
            ),
        )
        assertEquals("Pizza", m.match("749").single().name)
        assertEquals(listOf("Bob"), m.match("266").map { it.name })
    }

    // ---- Dialer with matches ----

    private val office = NumberUi("sip:104@pbx", "Office", "104", "pbx", Transport.TLS, NumberStatus.Registered, 0, false)

    @Test fun `dialer calls a match from the chosen number, or hands it to the SIM dialer`() {
        val bridge = FakeBridge(numbers = mutableListOf(office))
        bridge.contacts.add(anna)
        val vm = DialerViewModel(bridge, hasTelephony = true)
        val matcher = vm.buildMatcher()
        val match = vm.matchesFor(matcher, "2").first()
        val placed = mutableListOf<Pair<String, String>>()
        assertEquals(DialOutcome.Placed, vm.callMatch(match) { from, dial -> placed.add(from to dial); PlaceResult.Placed })
        assertEquals(office.id to "tel:+15550198", placed.single())
        vm.nextFrom() // Phone (SIM)
        assertEquals(DialOutcome.PhoneDialer("+15550198"), vm.callMatch(match) { _, _ -> error("not a SIP call") })
    }

    @Test fun `dialer without the service matches nothing`() {
        val bridge = FakeBridge(running = false)
        bridge.contacts.add(anna)
        val vm = DialerViewModel(bridge, hasTelephony = false)
        assertTrue(vm.matchesFor(vm.buildMatcher(), "2").isEmpty())
        assertTrue(vm.matchesFor(null, "2").isEmpty())
    }

    // ---- Contacts screen (S3) ----

    @Test fun `contacts are sorted by name, without numberless ones`() {
        val bridge = FakeBridge()
        bridge.contacts.addAll(listOf(bob, anna, ContactUi("Nobody", emptyList()), eloise))
        val state = ContactsViewModel(bridge).state()
        assertTrue(state.on)
        assertEquals(listOf("Anna Becker", "Bob", "Éloïse Durand"), state.contacts.map { it.name })
    }

    @Test fun `contacts off shows the off state`() {
        val bridge = FakeBridge()
        bridge.contactsAreOn = false
        val state = ContactsViewModel(bridge).state()
        assertFalse(state.on)
        assertTrue(state.contacts.isEmpty())
    }

    @Test fun `2 to 9 jump to the first name on that key, or the next one after it`() {
        val vm = ContactsViewModel(FakeBridge())
        val list = listOf(anna, bob, connor, eloise, ContactUi("Zed", listOf(tel("", "1"))))
        assertEquals(0, vm.jumpIndex(list, '2'))
        assertEquals(3, vm.jumpIndex(list, '3')) // É folds to E
        assertEquals(4, vm.jumpIndex(list, '4')) // no G/H/I: next is Zed
        assertEquals(4, vm.jumpIndex(list, '9'))
        assertNull(vm.jumpIndex(list.take(4), '9'))
        assertNull(vm.jumpIndex(list, '1'))
        assertEquals("+15550198", vm.firstNumber(anna)?.display)
    }

    // ---- Call log (S6) ----

    @Test fun `call log lists every call and deletes one`() {
        val now = 1_700_000_000_000L
        val missed = RecentUi("Mom", "sip:5550142@pbx", "Office", office.id, CallDirection.In, true, now - 60_000, null, now - 60_000)
        val out = RecentUi("Desk", "sip:100@pbx", "Office", office.id, CallDirection.Out, false, now - 120_000, now - 120_000, now - 100_000)
        val bridge = FakeBridge(recents = mutableListOf(missed, out))
        val vm = CallLogViewModel(bridge, now = { now }, zone = { ZoneId.of("UTC") })
        val state = vm.state()
        assertTrue(state.running)
        assertEquals(2, state.rows.size)
        assertEquals(TimeLabel.MinutesAgo(1), state.rows[0].time)
        assertEquals(office.id to "sip:5550142@pbx", vm.callBackTarget(state.rows[0]))
        vm.delete(state.rows[0])
        assertEquals(listOf(missed), bridge.deletedRecents)
        assertEquals(1, vm.state().rows.size)
        assertFalse(CallLogViewModel(FakeBridge(running = false)).state().running)
    }
}
