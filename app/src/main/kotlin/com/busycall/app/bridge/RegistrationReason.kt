package com.busycall.app.bridge

/** Why a number couldn't register, as the user is told it (PLAN S10). */
sealed class NumberProblem {
    /** 401/403/407: the server refused the extension, Auth ID or password. */
    object WrongPassword : NumberProblem()
    /** 408 or a timeout: nothing came back from the server. */
    object NoAnswer : NumberProblem()
    /** The server's name couldn't be looked up (baresip reports "Invalid argument"). */
    object ServerNotFound : NumberProblem()
    /** The phone has no network. */
    object NoNetwork : NumberProblem()
    /** Anything else: shown as baresip worded it. */
    data class Other(val text: String) : NumberProblem()
}

/** Turns baresip's registration failure text ("401 Unauthorized", …) into a [NumberProblem]. Pure. */
object RegistrationReason {

    fun of(text: String?): NumberProblem? {
        val t = text?.trim().orEmpty()
        if (t.isEmpty()) return null
        val code = t.takeWhile { it.isDigit() }.toIntOrNull()
        val lower = t.lowercase()
        return when {
            code == 401 || code == 403 || code == 407 -> NumberProblem.WrongPassword
            code == 408 || "timed out" in lower || "timeout" in lower -> NumberProblem.NoAnswer
            lower == "invalid argument" || "dns" in lower || "no address" in lower ->
                NumberProblem.ServerNotFound
            "network is unreachable" in lower || "no route to host" in lower -> NumberProblem.NoNetwork
            else -> NumberProblem.Other(t)
        }
    }
}
