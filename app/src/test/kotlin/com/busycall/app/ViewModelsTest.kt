package com.busycall.app

import com.busycall.app.bridge.CallDirection
import com.busycall.app.bridge.NumberDraft
import com.busycall.app.bridge.NumberStatus
import com.busycall.app.bridge.NumberUi
import com.busycall.app.bridge.RecentUi
import com.busycall.app.bridge.SaveProblem
import com.busycall.app.bridge.SaveResult
import com.busycall.app.bridge.Transport
import com.busycall.app.home.HomeProblem
import com.busycall.app.home.HomeViewModel
import com.busycall.app.numbers.NumberAction
import com.busycall.app.numbers.NumberSetupViewModel
import com.busycall.app.numbers.NumbersViewModel
import com.busycall.app.numbers.PasteOutcome
import com.busycall.app.numbers.SetupField
import com.busycall.app.numbers.setup.SetupCodeParser
import com.busycall.app.ui.TimeLabel
import com.busycall.app.ui.TimeText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class ViewModelsTest {

    private val office = NumberUi("sip:104@pbx;transport=tls", "Office", "104", "pbx", Transport.TLS, NumberStatus.Registered, 2, false)
    private val school = NumberUi("sip:2201@10.0.4.2;transport=udp", "School", "2201", "10.0.4.2", Transport.UDP, NumberStatus.NoConnection, 0, false)

    // ---- Home ----

    @Test fun `home while the service starts`() {
        val vm = HomeViewModel(FakeBridge(running = false))
        assertEquals(HomeProblem.Starting, vm.state().problem)
    }

    @Test fun `home after permissions were refused`() {
        val vm = HomeViewModel(FakeBridge())
        vm.permissionsDenied = true
        assertEquals(HomeProblem.PermissionsDenied, vm.state().problem)
    }

    @Test fun `home lists numbers with the first as default, and recent calls with times`() {
        val zone = ZoneId.of("America/New_York")
        val now = ZonedDateTime.of(2026, 11, 4, 12, 0, 0, 0, zone).toInstant().toEpochMilli()
        val bridge = FakeBridge(
            numbers = mutableListOf(office, school),
            recents = mutableListOf(RecentUi("Mom", "sip:5550142@pbx", "Home", CallDirection.In, true, now - 10 * 60_000)),
        )
        val state = HomeViewModel(bridge, now = { now }, zone = { zone }).state()
        assertNull(state.problem)
        assertEquals(listOf("Office", "School"), state.numbers.map { it.label })
        assertTrue(state.numbers[0].isDefault)
        assertEquals(2, state.numbers[0].voicemailNew)
        assertEquals(TimeLabel.MinutesAgo(10), state.recents.single().time)
        assertTrue(state.recents.single().missed)
    }

    // ---- Numbers ----

    @Test fun `number menu offers what makes sense`() {
        val vm = NumbersViewModel(FakeBridge(numbers = mutableListOf(office, school)))
        val rows = vm.state(hasTelephony = true).numbers
        assertEquals(listOf(NumberAction.Edit, NumberAction.Delete), vm.actionsFor(rows[0]))
        assertEquals(listOf(NumberAction.Edit, NumberAction.MakeDefault, NumberAction.Retry, NumberAction.Delete), vm.actionsFor(rows[1]))
    }

    @Test fun `number actions reach the bridge`() {
        val bridge = FakeBridge(numbers = mutableListOf(office, school))
        val vm = NumbersViewModel(bridge)
        val school = vm.state(true).numbers[1]
        vm.perform(NumberAction.MakeDefault, school)
        vm.perform(NumberAction.Retry, school)
        assertEquals(school.id, bridge.defaultId)
        assertEquals(listOf(school.id), bridge.retried)
        vm.perform(NumberAction.Delete, school)
        assertEquals(listOf("Office"), vm.state(true).numbers.map { it.label })
    }

    @Test fun `sim row only with telephony`() {
        val vm = NumbersViewModel(FakeBridge())
        assertTrue(vm.state(hasTelephony = true).showSim)
        assertEquals(false, vm.state(hasTelephony = false).showSim)
    }

    @Test fun `paste outcomes`() {
        val vm = NumbersViewModel(FakeBridge())
        assertEquals(PasteOutcome.NothingCopied, vm.paste(null))
        assertEquals(PasteOutcome.NothingCopied, vm.paste("  "))
        assertEquals(PasteOutcome.NotACode, vm.paste("hello"))
        assertEquals(PasteOutcome.TooNew, vm.paste("busycall:add?v=9&server=x.org&user=1"))
        assertEquals(PasteOutcome.Incomplete(SetupCodeParser.Field.User), vm.paste("busycall:add?v=1&server=x.org"))
        assertTrue(vm.paste("busycall:add?v=1&server=x.org&user=1") is PasteOutcome.Found)
    }

    // ---- Number setup ----

    @Test fun `setup checks fields before the backend`() {
        val vm = NumberSetupViewModel(FakeBridge())
        val good = NumberDraft(server = "pbx.example.org", user = "104", password = "x")
        assertNull(vm.check(good, ""))
        assertEquals(SaveProblem.InvalidServer, vm.check(good.copy(server = ""), ""))
        assertEquals(SaveProblem.InvalidPort, vm.check(good, "abc"))
        assertEquals(SaveProblem.InvalidPort, vm.check(good, "99999"))
        assertEquals(SaveProblem.InvalidUser, vm.check(good.copy(user = " "), ""))
        assertEquals(SaveProblem.InvalidPassword, vm.check(good.copy(password = "a\"b"), ""))
        assertEquals(SaveProblem.InvalidName, vm.check(good.copy(name = "a;b"), ""))
        assertEquals(SetupField.Port, vm.fieldOf(SaveProblem.InvalidPort))
        assertEquals(SetupField.None, vm.fieldOf(SaveProblem.ServiceNotRunning))
    }

    @Test fun `setup saves new numbers with the typed port, and reports backend problems`() {
        val bridge = FakeBridge()
        val vm = NumberSetupViewModel(bridge)
        val result = vm.save(null, NumberDraft(server = "pbx.example.org", user = "104"), " 5061 ")
        assertTrue(result is SaveResult.Saved)
        assertEquals(5061, bridge.saved.single().port)
        bridge.nextProblem = SaveProblem.NameTaken
        assertEquals(SaveResult.Failed(SaveProblem.NameTaken), vm.save(null, NumberDraft(server = "x.org", user = "1"), ""))
    }

    @Test fun `setup starts from an existing number when editing`() {
        val vm = NumberSetupViewModel(FakeBridge(numbers = mutableListOf(office)))
        assertEquals("Office", vm.start(office.id, null).name)
        assertEquals("given", vm.start(office.id, NumberDraft(name = "given")).name)
        assertEquals(NumberDraft(), vm.start(null, null))
    }

    // ---- Time labels ----

    @Test fun `time labels`() {
        val zone = ZoneId.of("UTC")
        val now = ZonedDateTime.of(2026, 11, 4, 12, 0, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals(TimeLabel.Unknown, TimeText.label(null, now, zone))
        assertEquals(TimeLabel.JustNow, TimeText.label(now - 30_000, now, zone))
        assertEquals(TimeLabel.MinutesAgo(59), TimeText.label(now - 59 * 60_000, now, zone))
        val morning = now - 3 * 3_600_000
        assertEquals(TimeLabel.Today(morning), TimeText.label(morning, now, zone))
        assertEquals(TimeLabel.Yesterday, TimeText.label(now - 20 * 3_600_000, now, zone))
        val old = now - 5L * 24 * 3_600_000
        assertEquals(TimeLabel.OnDate(old), TimeText.label(old, now, zone))
    }
}
