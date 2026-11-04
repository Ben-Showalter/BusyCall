package com.busycall.app.contacts

import com.busycall.app.bridge.Bridge
import com.busycall.app.bridge.Bridges
import com.busycall.app.bridge.ContactNumberUi
import com.busycall.app.bridge.ContactUi
import com.busycall.app.dialer.KeypadMatcher

/** What the Contacts screen shows (PLAN.md S3). */
data class ContactsState(
    /** The phone's contacts are being read; false shows "Contacts are off". */
    val on: Boolean,
    val running: Boolean,
    val contacts: List<ContactUi>,
)

/** Contacts screen logic; plain class (see HomeViewModel). */
class ContactsViewModel(private val bridge: Bridge = Bridges.current) {

    fun state(): ContactsState {
        val running = bridge.isRunning()
        val list = if (running) bridge.contacts()
            .filter { it.numbers.isNotEmpty() }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }) else emptyList()
        return ContactsState(on = bridge.contactsOn(), running = running, contacts = list)
    }

    /**
     * 2–9 jump (PLAN S3): the first contact whose name starts with one of that key's letters,
     * else the first one after them; null if there's none. [contacts] is sorted by name.
     */
    fun jumpIndex(contacts: List<ContactUi>, key: Char): Int? {
        if (key !in '2'..'9') return null
        val firstAtOrAfter = contacts.indexOfFirst { c ->
            val d = c.name.firstNotNullOfOrNull { KeypadMatcher.digitOf(it) } ?: return@indexOfFirst false
            d in '2'..'9' && d >= key
        }
        return firstAtOrAfter.takeIf { it >= 0 }
    }

    /** CALL on a contact calls its first number. */
    fun firstNumber(contact: ContactUi): ContactNumberUi? = contact.numbers.firstOrNull()
}
