package com.busycall.app.bridge

/**
 * Building and reading SIP addresses (AORs) for numbers. Pure Kotlin, so it is unit-tested;
 * the protocol work itself stays in baresip.
 */
object SipAddress {

    private val hostRegex = Regex("^(\\[[0-9A-Fa-f:.]+]|[A-Za-z0-9]([A-Za-z0-9-]{0,62})?(\\.[A-Za-z0-9]([A-Za-z0-9-]{0,62})?)*)$")
    // RFC 3261 user part: unreserved / escaped / user-unreserved characters.
    private val userRegex = Regex("^[A-Za-z0-9\\-_.!~*'()&=+$,%]{1,64}$")
    // Account.checkDisplayName in baresip-studio: what it accepts as a nickname.
    private val nameRegex = Regex("^([* .!%_`'~]|[+]|[-a-zA-Z0-9]){1,64}$")

    fun isValidHost(host: String): Boolean = hostRegex.matches(host)
    fun isValidUser(user: String): Boolean = userRegex.matches(user)
    fun isValidPort(port: Int?): Boolean = port == null || port in 1..65535
    fun isValidName(name: String): Boolean = name.isEmpty() || nameRegex.matches(name)

    /** baresip-studio's Account.checkAuthPass: printable ASCII, 1–64 chars, no double quote. */
    fun isValidPassword(password: String): Boolean =
        password.isEmpty() || (password.length <= 64 && password.all { it in ' '..'~' } && '"' !in password)

    /** `sip:104@pbx.example.org:5061;transport=tls` */
    fun aorOf(draft: NumberDraft): String {
        val port = draft.port?.let { ":$it" } ?: ""
        return "sip:${draft.user.trim()}@${draft.server.trim()}$port;transport=${draft.transport.param}"
    }

    /** A voicemail number ("*97") as a SIP URI on the same server; already a URI is kept. */
    fun voicemailUri(draft: NumberDraft): String {
        val vm = draft.voicemail.trim()
        if (vm.isEmpty()) return ""
        if (vm.startsWith("sip:")) return vm
        return "sip:$vm@${draft.server.trim()}"
    }

    data class Parts(val user: String, val host: String, val port: Int?, val transport: Transport?)

    /** Splits `sip:user@host:port;transport=x` (also with `<…>` around it). */
    fun parse(aor: String): Parts {
        val inner = aor.trim().removePrefix("<").substringBefore(">")
        val noScheme = inner.removePrefix("sip:").removePrefix("sips:")
        val params = noScheme.substringAfter(';', "")
        val userHost = noScheme.substringBefore(';')
        val user = if ('@' in userHost) userHost.substringBefore('@') else ""
        val hostPort = userHost.substringAfter('@')
        val (host, port) = if (hostPort.startsWith("[")) {
            val h = hostPort.substringBefore(']') + "]"
            h to hostPort.substringAfter("]:", "").toIntOrNull()
        } else {
            hostPort.substringBefore(':') to hostPort.substringAfter(':', "").toIntOrNull()
        }
        val transport = params.split(';')
            .firstOrNull { it.startsWith("transport=", ignoreCase = true) }
            ?.substringAfter('=')
            ?.let { Transport.fromParam(it) }
        return Parts(user, host, port, transport)
    }
}
