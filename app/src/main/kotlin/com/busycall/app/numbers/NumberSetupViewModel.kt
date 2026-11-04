package com.busycall.app.numbers

import com.busycall.app.bridge.Bridge
import com.busycall.app.bridge.Bridges
import com.busycall.app.bridge.NumberDraft
import com.busycall.app.bridge.SaveProblem
import com.busycall.app.bridge.SaveResult
import com.busycall.app.bridge.SipAddress

/** Which field a problem belongs to, so the screen can move focus there. */
enum class SetupField { Name, Server, Port, User, AuthUser, Password, Voicemail, None }

/** Number setup logic (PLAN.md S8); plain class (see HomeViewModel). */
class NumberSetupViewModel(private val bridge: Bridge = Bridges.current) {

    /** The draft to start from: an existing number (password left empty) or [initial]. */
    fun start(editId: String?, initial: NumberDraft?): NumberDraft =
        initial ?: editId?.let { bridge.draftOf(it) } ?: NumberDraft()

    /** Checks what can be checked before baresip sees it. Port text is passed raw. */
    fun check(draft: NumberDraft, portText: String): SaveProblem? {
        if (draft.server.isBlank() || !SipAddress.isValidHost(draft.server.trim())) return SaveProblem.InvalidServer
        if (portText.isNotBlank() && portText.trim().toIntOrNull()?.let { SipAddress.isValidPort(it) } != true) {
            return SaveProblem.InvalidPort
        }
        if (draft.user.isBlank() || !SipAddress.isValidUser(draft.user.trim())) return SaveProblem.InvalidUser
        if (!SipAddress.isValidPassword(draft.password)) return SaveProblem.InvalidPassword
        if (!SipAddress.isValidName(draft.name.trim())) return SaveProblem.InvalidName
        return null
    }

    fun save(editId: String?, draft: NumberDraft, portText: String): SaveResult {
        check(draft, portText)?.let { return SaveResult.Failed(it) }
        val clean = draft.copy(port = portText.trim().toIntOrNull())
        return if (editId != null) bridge.updateNumber(editId, clean) else bridge.addNumber(clean)
    }

    fun fieldOf(problem: SaveProblem): SetupField = when (problem) {
        SaveProblem.InvalidServer, SaveProblem.AlreadyExists -> SetupField.Server
        SaveProblem.InvalidPort -> SetupField.Port
        SaveProblem.InvalidUser -> SetupField.User
        SaveProblem.InvalidPassword -> SetupField.Password
        SaveProblem.InvalidName, SaveProblem.NameTaken -> SetupField.Name
        SaveProblem.ServiceNotRunning, SaveProblem.AllocationFailed -> SetupField.None
    }
}
