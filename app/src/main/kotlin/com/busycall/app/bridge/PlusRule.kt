package com.busycall.app.bridge

/**
 * What to do with numbers that start with `+` (contacts usually store `+1 555…`), per number.
 * Many phone systems' outbound routes don't match a leading `+` (a Grandstream UCM answers
 * 604, docs/PBX-NOTES.md), so the default drops it. A setting any system can use (AGENTS §0.6).
 */
sealed class PlusRule {
    /** `+15551234567` → `15551234567`. The default. */
    object DropPlus : PlusRule()
    /** Send it as stored, `+` included. */
    object AsIs : PlusRule()
    /** `+44…` → `<prefix>44…`, e.g. `011` or `00`. */
    data class ReplaceWith(val prefix: String) : PlusRule()

    /**
     * Applies the rule to what's being dialed: a number, or a `tel:` URI. Anything without a
     * leading `+` (extensions, SIP addresses, star codes) comes back unchanged.
     */
    fun apply(dial: String): String {
        val text = dial.trim()
        val scheme = if (text.startsWith("tel:", ignoreCase = true)) text.take(4) else ""
        val number = text.substring(scheme.length)
        if (!number.startsWith("+")) return text
        val rest = number.substring(1)
        return scheme + when (this) {
            DropPlus -> rest
            AsIs -> number
            is ReplaceWith -> prefix.filter { it.isDigit() } + rest
        }
    }

    /** How it's stored in Prefs. */
    fun key(): String = when (this) {
        DropPlus -> KEY_DROP
        AsIs -> KEY_AS_IS
        is ReplaceWith -> KEY_REPLACE
    }

    companion object {
        const val KEY_DROP = "drop"
        const val KEY_AS_IS = "as_is"
        const val KEY_REPLACE = "replace"

        fun of(key: String?, prefix: String?): PlusRule = when (key) {
            KEY_AS_IS -> AsIs
            KEY_REPLACE -> ReplaceWith(prefix.orEmpty().filter { it.isDigit() })
            else -> DropPlus
        }
    }
}
