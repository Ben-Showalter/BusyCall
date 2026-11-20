package com.busycall.app.bridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CallFilterTest {

    private val now = 1_700_000_000_000L
    private val annaKey = CallFilter.keyOf("tel:+13782343189")

    private fun decide(
        mode: BlockMode,
        uri: String,
        name: String = "",
        contacts: Set<String> = setOf(annaKey),
        allowed: Set<String> = emptySet(),
        blocked: Set<String> = emptySet(),
        earlier: List<Pair<String, Long>> = emptyList(),
    ) = CallFilter.decide(mode, uri, name, contacts, allowed, blocked, earlier, now)

    @Test fun `off lets everything ring`() {
        assertNull(decide(BlockMode.Off, "sip:anonymous@anonymous.invalid", "SPAM LIKELY", blocked = setOf("anonymous")))
    }

    @Test fun `known spam blocks marked, hidden and fake numbers, not unknown ones`() {
        assertEquals(BlockReason.Spam, decide(BlockMode.KnownSpam, "tel:+15550001111", "Scam Likely"))
        assertEquals(BlockReason.Spam, decide(BlockMode.KnownSpam, "tel:+15550001111", "Potential SPAM"))
        assertEquals(BlockReason.Hidden, decide(BlockMode.KnownSpam, "sip:anonymous@anonymous.invalid"))
        assertEquals(BlockReason.Hidden, decide(BlockMode.KnownSpam, "tel:"))
        assertEquals(BlockReason.Impossible, decide(BlockMode.KnownSpam, "tel:0000000000"))
        assertNull(decide(BlockMode.KnownSpam, "tel:+15550001111")) // unknown, but not spam
        assertNull(decide(BlockMode.KnownSpam, "sip:104@pbx")) // an extension
    }

    @Test fun `your lists and contacts come first`() {
        val key = CallFilter.keyOf("sip:5550001111@10.1.10.247")
        assertEquals(BlockReason.YourList, decide(BlockMode.KnownSpam, "tel:+15550001111", blocked = setOf(key)))
        assertNull(decide(BlockMode.NotInContacts, "tel:5550001111", allowed = setOf(key)))
        // A contact rings even with a spam-looking caller name.
        assertNull(decide(BlockMode.NotInContacts, "sip:3782343189@10.1.10.247", "Spam Likely"))
    }

    @Test fun `not in contacts - a second call within 5 minutes rings`() {
        val uri = "sip:5550001111@10.1.10.247"
        assertEquals(BlockReason.NotInContacts, decide(BlockMode.NotInContacts, uri))
        assertNull(decide(BlockMode.NotInContacts, uri, earlier = listOf("tel:+15550001111" to now - 299_000)))
        assertEquals(BlockReason.NotInContacts, decide(BlockMode.NotInContacts, uri, earlier = listOf(uri to now - 301_000)))
        // A different number's attempt doesn't count.
        assertEquals(BlockReason.NotInContacts, decide(BlockMode.NotInContacts, uri, earlier = listOf("tel:5550002222" to now - 1_000)))
    }

    @Test fun `modes are stored by key`() {
        for (m in BlockMode.values()) assertEquals(m, BlockMode.of(m.key))
        assertEquals(BlockMode.Off, BlockMode.of(null))
        for (r in BlockReason.values()) assertEquals(r, BlockReason.of(r.key))
    }
}
