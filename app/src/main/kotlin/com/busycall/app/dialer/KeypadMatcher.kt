package com.busycall.app.dialer

import com.busycall.app.bridge.ContactUi
import java.text.Normalizer

/** One Dialer suggestion: a contact's number (or a number from the call log). */
data class DialMatch(
    val name: String,
    /** The number's label ("Mobile"), or empty. */
    val label: String,
    /** What to dial. */
    val uri: String,
    val display: String,
    /** The part of [name] the keys spelled, to underline; null if the number matched. */
    val nameHighlight: IntRange?,
    /** The part of [display] the digits matched, to underline; null if the name matched. */
    val numberHighlight: IntRange?,
)

/**
 * Keypad "smart dial" (PLAN.md §3.3): digits match numbers containing them **and** names whose
 * letters are on those keys (2=ABC … 9=WXYZ), at the start of any word. Accented letters match
 * their plain letter. There is no letter-typing mode.
 *
 * Built once per contact list (pre-converting every word to its digits), then queried per
 * keypress; the Dialer does both off the main thread.
 */
class KeypadMatcher(contacts: List<ContactUi>, extra: List<DialEntry> = emptyList()) {

    /** A callable entry that isn't a contact (a call-log number). */
    data class DialEntry(val name: String, val uri: String, val display: String)

    private class Word(val start: Int, val digits: String)

    private class Entry(
        val name: String,
        val label: String,
        val uri: String,
        val display: String,
        val words: List<Word>,
        /** Digits of [display] only, with each digit's position in [display]. */
        val numberDigits: String,
        val numberPositions: IntArray,
        val order: Int,
    )

    private val entries: List<Entry>

    init {
        val list = ArrayList<Entry>()
        val seen = HashSet<String>()
        contacts.forEach { c ->
            val words = wordsOf(c.name)
            c.numbers.forEach { n ->
                if (seen.add(digitsOnly(n.display).ifEmpty { n.uri })) {
                    list.add(entry(c.name, n.label, n.uri, n.display, words, list.size))
                }
            }
        }
        extra.forEach { e ->
            if (seen.add(digitsOnly(e.display).ifEmpty { e.uri })) {
                list.add(entry(e.name, "", e.uri, e.display, wordsOf(e.name), list.size))
            }
        }
        entries = list
    }

    private fun entry(name: String, label: String, uri: String, display: String, words: List<Word>, order: Int): Entry {
        val positions = display.indices.filter { display[it].isDigit() }.toIntArray()
        return Entry(name, label, uri, display, words, positions.map { display[it] }.joinToString(""), positions, order)
    }

    /** Up to [max] matches for [typed] (what the Dialer holds), best first. */
    fun match(typed: String, max: Int = MAX_MATCHES): List<DialMatch> {
        val digits = digitsOnly(typed)
        // `*`, `#` or a lone `+` can't spell a name or start a useful number search.
        if (digits.isEmpty() || digits.length != typed.trimStart('+').length) return emptyList()
        val found = ArrayList<Pair<Int, DialMatch>>()
        for (e in entries) {
            val wordIndex = e.words.indexOfFirst { it.digits.startsWith(digits) }
            val rank: Int
            val match: DialMatch
            if (wordIndex >= 0) {
                val w = e.words[wordIndex]
                rank = if (wordIndex == 0) 0 else 1
                match = DialMatch(e.name, e.label, e.uri, e.display, w.start until w.start + digits.length, null)
            } else if (digits.length >= MIN_NUMBER_DIGITS) {
                val at = e.numberDigits.indexOf(digits)
                if (at < 0) continue
                rank = if (at == 0 || (at == 1 && e.display.startsWith("+"))) 2 else 3
                match = DialMatch(
                    e.name, e.label, e.uri, e.display, null,
                    e.numberPositions[at]..e.numberPositions[at + digits.length - 1],
                )
            } else {
                continue
            }
            found.add(rank * 1_000_000 + e.order to match)
        }
        return found.sortedBy { it.first }.take(max).map { it.second }
    }

    companion object {
        const val MAX_MATCHES = 3

        /** Fewer digits than this match names only: "55" in every number is noise. */
        const val MIN_NUMBER_DIGITS = 3

        private const val KEYS = "22233344455566677778889999"

        /** The keypad digit for a letter or digit (accents folded), or null. */
        fun digitOf(c: Char): Char? {
            if (c in '0'..'9') return c
            val plain = fold(c)
            if (plain in 'a'..'z') return KEYS[plain - 'a']
            return null
        }

        /** "Becker" → "232537". Characters with no key are dropped. */
        fun toDigits(text: String): String = text.mapNotNull { digitOf(it) }.joinToString("")

        private fun fold(c: Char): Char {
            val lower = c.lowercaseChar()
            if (lower in 'a'..'z') return lower
            val base = Normalizer.normalize(lower.toString(), Normalizer.Form.NFD).firstOrNull() ?: return lower
            return when (lower) {
                'ß' -> 's'
                'ø' -> 'o'
                'æ' -> 'a'
                'ł' -> 'l'
                else -> base
            }
        }

        private fun digitsOnly(text: String) = text.filter { it in '0'..'9' }

        /** Each run of letters/digits in [name], with its digits; every char maps to exactly one digit. */
        private fun wordsOf(name: String): List<Word> {
            val words = ArrayList<Word>()
            var start = -1
            val digits = StringBuilder()
            for (i in 0..name.length) {
                val d = if (i < name.length) digitOf(name[i]) else null
                if (d != null) {
                    if (start < 0) start = i
                    digits.append(d)
                } else if (start >= 0) {
                    words.add(Word(start, digits.toString()))
                    digits.setLength(0)
                    start = -1
                }
            }
            return words
        }
    }
}
