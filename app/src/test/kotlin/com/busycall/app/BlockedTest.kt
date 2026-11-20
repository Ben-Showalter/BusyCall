package com.busycall.app

import com.busycall.app.blocking.BlockedViewModel
import com.busycall.app.bridge.BlockReason
import com.busycall.app.bridge.BlockedUi
import com.busycall.app.ui.TimeLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class BlockedTest {
    @Test fun `blocked rows, unblock and delete`() {
        val now = 1_700_000_000_000L
        val bridge = FakeBridge()
        val a = BlockedUi("sip:104@pbx", "Office", "tel:5550001111", "(555) 000-1111", now - 120_000, BlockReason.NotInContacts)
        val b = BlockedUi("sip:104@pbx", "Office", "sip:anonymous@anonymous.invalid", "Anonymous", now - 60_000, BlockReason.Hidden)
        bridge.blockedList.addAll(listOf(b, a))
        val vm = BlockedViewModel(bridge, now = { now }, zone = { ZoneId.of("UTC") })
        val rows = vm.rows()
        assertEquals(2, rows.size)
        assertEquals(TimeLabel.MinutesAgo(1), rows[0].time)
        vm.unblock(rows[1])
        assertEquals(listOf("tel:5550001111"), bridge.allowedCallers)
        assertEquals(listOf(b), bridge.blockedList)
        vm.delete(vm.rows()[0])
        assertTrue(vm.rows().isEmpty())
        assertTrue(BlockedViewModel(FakeBridge(running = false)).rows().isEmpty())
    }
}
