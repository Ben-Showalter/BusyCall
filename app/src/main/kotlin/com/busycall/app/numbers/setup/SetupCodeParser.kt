package com.busycall.app.numbers.setup

import com.busycall.app.AppIdentity
import com.busycall.app.bridge.NumberDraft
import com.busycall.app.bridge.SipAddress
import com.busycall.app.bridge.Transport
import java.net.URLDecoder

/**
 * The one place that reads setup codes (AGENTS.md §6a, PLAN.md §5.1). Pure Kotlin, unit-tested.
 * Never logs the code or anything from it.
 *
 * Accepted:
 * 1. BusyCall links, version 1: `busycall:add?v=1&name=..&server=..&port=..&user=..&auth=..&pass=..&transport=..&vm=..`
 *    (values URL-encoded; only server and user required), also as `https://<host>/<path>#add?…`
 *    and under any scheme in [AppIdentity.OLD_LINK_SCHEMES].
 * 2. baresip account lines: `"Name" <sip:104@pbx;transport=tls>;auth_user="104";auth_pass="secret";…`
 * 3. SIP addresses with a password: `sip:104:secret@pbx.example.org:5061;transport=tls`
 */
object SetupCodeParser {

    sealed class Result {
        data class Ok(val draft: NumberDraft) : Result()
        /** Recognised, but a required field is missing or wrong. */
        data class Incomplete(val missing: Field) : Result()
        /** Not a setup code we know. */
        object NotASetupCode : Result()
        /** A newer link format than this app understands. */
        object TooNew : Result()
    }

    enum class Field { Server, User }

    private const val SUPPORTED_VERSION = 1

    fun parse(text: String?): Result {
        val code = text?.trim()?.removeSurrounding("\"")?.trim().orEmpty()
        if (code.isEmpty()) return Result.NotASetupCode
        linkQuery(code)?.let { return parseLink(it) }
        if (code.contains("<sip:", ignoreCase = true) || code.contains("<sips:", ignoreCase = true)) {
            return parseBaresipLine(code)
        }
        if (code.startsWith("sip:", ignoreCase = true)) return parseSipUri(code)
        return Result.NotASetupCode
    }

    // ---- 1. BusyCall links ----

    /** The `name=value&…` part of a BusyCall link, or null if [code] isn't one. */
    private fun linkQuery(code: String): String? {
        val schemes = listOf(AppIdentity.LINK_SCHEME) + AppIdentity.OLD_LINK_SCHEMES
        for (scheme in schemes) {
            val prefix = "$scheme:add"
            if (code.startsWith(prefix, ignoreCase = true)) {
                return code.substring(prefix.length).removePrefix("?").removePrefix("//")
            }
        }
        // https://host/anything#add?… (the part after # never reaches a web server)
        if (code.startsWith("https://", ignoreCase = true) || code.startsWith("http://", ignoreCase = true)) {
            val fragment = code.substringAfter('#', "")
            if (fragment.startsWith("add?")) return fragment.removePrefix("add?")
        }
        return null
    }

    private fun parseLink(query: String): Result {
        val fields = HashMap<String, String>()
        for (pair in query.split('&')) {
            if (pair.isEmpty()) continue
            val key = decode(pair.substringBefore('=')).lowercase()
            val value = decode(pair.substringAfter('=', ""))
            fields[key] = value
        }
        val version = fields["v"]?.toIntOrNull() ?: SUPPORTED_VERSION
        if (version > SUPPORTED_VERSION) return Result.TooNew

        val server = fields["server"]?.trim().orEmpty()
        val user = fields["user"]?.trim().orEmpty()
        if (server.isEmpty()) return Result.Incomplete(Field.Server)
        if (user.isEmpty()) return Result.Incomplete(Field.User)
        return Result.Ok(
            NumberDraft(
                name = fields["name"]?.trim().orEmpty(),
                server = server,
                port = fields["port"]?.trim()?.toIntOrNull(),
                user = user,
                authUser = fields["auth"]?.trim().orEmpty(),
                password = fields["pass"].orEmpty(),
                transport = Transport.fromParam(fields["transport"]) ?: Transport.UDP,
                voicemail = fields["vm"]?.trim().orEmpty(),
            )
        )
    }

    // ---- 2. baresip account lines ----

    private fun parseBaresipLine(line: String): Result {
        val displayName = Regex("^\\s*\"([^\"]*)\"\\s*<").find(line)?.groupValues?.get(1).orEmpty()
        val address = line.substringAfter('<').substringBefore('>')
        val params = line.substringAfter('>', "")
        val parts = SipAddress.parse(address)
        if (parts.host.isEmpty()) return Result.Incomplete(Field.Server)
        if (parts.user.isEmpty()) return Result.Incomplete(Field.User)
        val nickname = param(params, "extra")?.let { extra -> param(";$extra", "nickname") }.orEmpty()
        val vm = param(params, "vm_uri").orEmpty()
        val vmParts = SipAddress.parse(vm)
        return Result.Ok(
            NumberDraft(
                name = nickname.ifEmpty { displayName },
                server = parts.host,
                port = parts.port,
                user = parts.user,
                authUser = param(params, "auth_user").orEmpty().takeIf { it != parts.user }.orEmpty(),
                password = param(params, "auth_pass").orEmpty(),
                transport = parts.transport ?: Transport.UDP,
                voicemail = when {
                    vm.isEmpty() -> ""
                    vmParts.host == parts.host -> vmParts.user
                    else -> vm
                },
            )
        )
    }

    /** Value of `;name=value` or `;name="value"` in a baresip parameter list. */
    private fun param(params: String, name: String): String? {
        val match = Regex(";\\s*${Regex.escape(name)}\\s*=\\s*(\"([^\"]*)\"|([^;]*))", RegexOption.IGNORE_CASE)
            .find(params) ?: return null
        return (match.groups[2]?.value ?: match.groups[3]?.value)?.trim()
    }

    // ---- 3. sip:user:password@host ----

    private fun parseSipUri(uri: String): Result {
        val noScheme = uri.substringAfter(':')
        val userInfo = noScheme.substringBefore('@', "")
        if (userInfo.isEmpty()) return Result.Incomplete(Field.User)
        val user = decode(userInfo.substringBefore(':'))
        val password = decode(userInfo.substringAfter(':', ""))
        val parts = SipAddress.parse("sip:" + noScheme.substringAfter('@').let { "x@$it" })
        if (parts.host.isEmpty()) return Result.Incomplete(Field.Server)
        if (user.isEmpty()) return Result.Incomplete(Field.User)
        return Result.Ok(
            NumberDraft(
                server = parts.host,
                port = parts.port,
                user = user,
                password = password,
                transport = parts.transport ?: Transport.UDP,
            )
        )
    }

    /** URL-decodes, keeping a literal `+` (encodeURIComponent writes spaces as %20). */
    private fun decode(value: String): String =
        try {
            URLDecoder.decode(value.replace("+", "%2B"), "UTF-8")
        } catch (e: IllegalArgumentException) {
            value
        }
}
