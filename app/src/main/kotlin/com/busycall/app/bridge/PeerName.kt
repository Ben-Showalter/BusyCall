package com.busycall.app.bridge

/**
 * How a caller is shown, when the address is a phone number. Pure, unit-tested.
 *
 * Upstream's `Utils.friendlyUri` drops the host only when it equals the number's own server, and
 * matches contacts on the exact address. A UCM reached through RemoteConnect still sends calls
 * from its LAN IP, and without a `+1`, so callers showed as `3782343189@10.1.10.247`. Here a
 * phone-number caller shows as its number, whatever server it came through, and is matched to
 * contacts by its digits.
 */
object PeerName {

    private val phoneLike = Regex("^\\+?[0-9]{7,15}$")

    /** The user part of `sip:…`, `sips:…` or `tel:…` (also `"Name" <sip:…>`), with visual separators removed. */
    fun userPart(uri: String): String {
        val inner = uri.trim().let { if ('<' in it) it.substringAfter('<').substringBefore('>') else it }
        val noScheme = inner.substringAfter(':', inner)
        val user = noScheme.substringBefore('@').substringBefore(';')
        return user.replace("%2B", "+").replace("%2b", "+").filter { it !in " -(). " }
    }

    /** The caller's phone number (`+13782343189`, `3782343189`), or null if the address isn't one. */
    fun phoneNumber(uri: String): String? = userPart(uri).takeIf { phoneLike.matches(it) }

    /**
     * Digits for matching: no `+`, and North American numbers without their leading 1, so
     * `+1 378 234 3189`, `13782343189` and `3782343189` all match.
     */
    fun matchKey(number: String): String {
        val digits = number.filter { it.isDigit() }
        return if (digits.length == 11 && digits.startsWith("1")) digits.substring(1) else digits
    }

    /** `(378) 234-3189` for North American numbers; anything else as dialed. */
    fun format(number: String): String {
        val digits = number.filter { it.isDigit() }
        val nanp = when {
            digits.length == 10 && !number.startsWith("+") -> digits
            digits.length == 11 && digits.startsWith("1") -> digits.substring(1)
            else -> null
        }
        return if (nanp != null) "(${nanp.substring(0, 3)}) ${nanp.substring(3, 6)}-${nanp.substring(6)}" else number
    }

    /** match key → contact name, for every contact number that is a phone number. */
    fun index(contacts: List<ContactUi>): Map<String, String> {
        val map = HashMap<String, String>()
        for (c in contacts) for (n in c.numbers) {
            val number = phoneNumber(n.uri) ?: continue
            map.putIfAbsent(matchKey(number), c.name)
        }
        return map
    }

    /**
     * The name to show for [uri]: the contact's name if its number is in [contactIndex], else the
     * formatted number. Null when the address isn't a phone number (the caller then uses
     * upstream's own naming).
     */
    fun of(uri: String, contactIndex: Map<String, String>): String? {
        val number = phoneNumber(uri) ?: return null
        return contactIndex[matchKey(number)] ?: format(number)
    }
}
