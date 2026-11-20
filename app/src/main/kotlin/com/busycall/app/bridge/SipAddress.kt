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

    private val stunRegex = Regex("^stuns?:([A-Za-z0-9]([A-Za-z0-9-]{0,62})?(\\.[A-Za-z0-9]([A-Za-z0-9-]{0,62})?)*|\\[[0-9A-Fa-f:.]+])(:[0-9]{1,5})?$")

    /**
     * A STUN server as baresip wants it: `nat-a.gdms.cloud` → `stun:nat-a.gdms.cloud`; a
     * `stun:`/`stuns:` URI is kept. Null if it isn't a valid host (and optional port).
     */
    fun stunUri(server: String): String? {
        val s = server.trim()
        if (s.isEmpty()) return null
        val uri = if (s.startsWith("stun:", true) || s.startsWith("stuns:", true)) s else "stun:$s"
        return uri.takeIf { stunRegex.matches(it) }
    }

    /**
     * Gives a call address on the number's own server the port and transport the number signs
     * in with. Upstream builds `sip:*97@host` from the host alone (Utils.uriComplete/telToSip),
     * so a number on `host:5061;transport=tls` (Grandstream RemoteConnect) sent its calls to
     * UDP 5060, where nothing answered: "Calling…" forever, and the UCM never saw the call.
     * Addresses on other servers, or that already name a port or transport, are left alone.
     */
    fun withAccountRoute(uri: String, accountAor: String): String {
        if (!uri.startsWith("sip:", ignoreCase = true)) return uri
        val account = parse(accountAor)
        if (account.port == null && (account.transport == null || account.transport == Transport.UDP)) return uri
        val rest = uri.substring(4)
        val userHost = rest.substringBefore(';')
        val params = rest.substringAfter(';', "")
        if ('@' !in userHost) return uri
        val user = userHost.substringBeforeLast('@')
        val hostPort = userHost.substringAfterLast('@')
        if (hostPort.startsWith("[") || ':' in hostPort) return uri // already has a port (or IPv6)
        if (!hostPort.equals(account.host, ignoreCase = true)) return uri
        val port = account.port?.let { ":$it" } ?: ""
        val hasTransport = params.split(';').any { it.startsWith("transport=", ignoreCase = true) }
        val transport = if (!hasTransport && account.transport != null && account.transport != Transport.UDP) {
            "transport=${account.transport.param}"
        } else ""
        val allParams = listOf(params, transport).filter { it.isNotEmpty() }.joinToString(";")
        return "sip:$user@$hostPort$port" + if (allParams.isEmpty()) "" else ";$allParams"
    }
}
