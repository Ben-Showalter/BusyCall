package com.busycall.app.bridge

/** How a number blocks incoming calls (owner, 2026-11-20). Stored per number in BusyCall's Prefs. */
enum class BlockMode(val key: String) {
    Off("off"),
    /** Calls the carrier or phone system marks as spam, hidden numbers, impossible numbers, your list. */
    KnownSpam("spam"),
    /** Everything above, plus callers not in contacts, unless they called in the last 5 minutes. */
    NotInContacts("contacts");

    companion object {
        fun of(key: String?): BlockMode = values().firstOrNull { it.key == key } ?: Off
    }
}

/** Why a call was blocked, shown in Blocked calls. */
enum class BlockReason(val key: String) {
    Spam("spam"), Hidden("hidden"), YourList("list"), Impossible("impossible"), NotInContacts("contacts");

    companion object {
        fun of(key: String?): BlockReason? = values().firstOrNull { it.key == key }
    }
}

/**
 * BusyCall's call blocking decision. Pure and unit-tested; the bridge feeds it, and upstream
 * rejects and logs the call when it returns a reason (UPSTREAM.md, the callFilter hook).
 * A rejected call normally reaches voicemail (the phone system's or carrier's busy setting).
 */
object CallFilter {

    /** A second call within this long from the same number rings ("not in contacts" mode). */
    const val CALLBACK_WINDOW_MS = 5 * 60_000L

    private val spamWords = Regex("spam|scam|fraud|telemarket|robocall", RegexOption.IGNORE_CASE)
    private val hiddenUsers = setOf("anonymous", "unknown", "restricted", "private", "unavailable")

    /** The comparison key for a caller: phone-number digits (NANP without the 1), or the SIP user. */
    fun keyOf(peerUri: String): String =
        PeerName.phoneNumber(peerUri)?.let { PeerName.matchKey(it) } ?: PeerName.userPart(peerUri).lowercase()

    /**
     * Null means the call rings. [contactKeys], [allowed] and [blocked] hold [keyOf] keys;
     * [earlierRejects] are earlier blocked attempts to this number: (peerUri, epoch millis).
     */
    fun decide(
        mode: BlockMode,
        peerUri: String,
        callerName: String,
        contactKeys: Set<String>,
        allowed: Set<String>,
        blocked: Set<String>,
        earlierRejects: List<Pair<String, Long>>,
        now: Long,
    ): BlockReason? {
        if (mode == BlockMode.Off) return null
        val key = keyOf(peerUri)
        // Your own choices first: your block list, then your allowed list and contacts.
        if (key in blocked) return BlockReason.YourList
        if (key in allowed || key in contactKeys) return null

        val user = PeerName.userPart(peerUri).lowercase()
        if (user.isEmpty() || user in hiddenUsers || "anonymous" in peerUri.lowercase()) return BlockReason.Hidden
        if (spamWords.containsMatchIn(callerName)) return BlockReason.Spam
        if (isImpossible(user)) return BlockReason.Impossible

        if (mode == BlockMode.NotInContacts) {
            val calledBack = earlierRejects.any { (uri, time) -> keyOf(uri) == key && now - time in 0..CALLBACK_WINDOW_MS }
            return if (calledBack) null else BlockReason.NotInContacts
        }
        return null
    }

    /** A digits-only caller that can't be a real number: over 15 digits, or 7+ of the same digit. */
    private fun isImpossible(user: String): Boolean {
        val digits = user.removePrefix("+")
        if (digits.isEmpty() || !digits.all { it.isDigit() }) return false
        return digits.length > 15 || (digits.length >= 7 && digits.all { it == digits[0] })
    }
}
