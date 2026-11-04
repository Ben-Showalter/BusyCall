package com.busycall.app

import android.app.Activity
import com.busycall.app.bridge.Bridge
import com.busycall.app.bridge.NumberDraft
import com.busycall.app.bridge.NumberUi
import com.busycall.app.bridge.RecentUi
import com.busycall.app.bridge.SaveProblem
import com.busycall.app.bridge.SaveResult
import com.busycall.app.bridge.SipAddress

/** In-memory stand-in for baresip's backend (AGENTS.md §11: tests never touch the real service). */
class FakeBridge(
    var running: Boolean = true,
    val numbers: MutableList<NumberUi> = mutableListOf(),
    val recents: MutableList<RecentUi> = mutableListOf(),
) : Bridge {
    val saved = mutableListOf<NumberDraft>()
    val deleted = mutableListOf<String>()
    var defaultId: String? = null
    val retried = mutableListOf<String>()
    var nextProblem: SaveProblem? = null

    override fun isRunning() = running
    override fun startService(activity: Activity) {}
    override fun numbers(): List<NumberUi> = numbers.map { it.copy(isDefault = it.id == (defaultId ?: numbers.firstOrNull()?.id)) }
    override fun recents(limit: Int) = recents.take(limit)
    override fun openClassicScreens(activity: Activity, dial: String?) {}
    override fun draftOf(id: String): NumberDraft? = numbers.firstOrNull { it.id == id }?.let {
        NumberDraft(name = it.label, server = it.server, user = it.number, transport = it.transport)
    }

    override fun addNumber(draft: NumberDraft): SaveResult {
        nextProblem?.let { return SaveResult.Failed(it) }
        saved.add(draft)
        return SaveResult.Saved(SipAddress.aorOf(draft))
    }

    override fun updateNumber(id: String, draft: NumberDraft): SaveResult = addNumber(draft)
    override fun deleteNumber(id: String) { deleted.add(id); numbers.removeAll { it.id == id } }
    override fun setDefault(id: String) { defaultId = id }
    override fun retry(id: String) { retried.add(id) }
}
