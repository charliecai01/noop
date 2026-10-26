package com.noop.ui

import java.text.Normalizer

/**
 * The free-text filter behind the More page and Settings search bars. A query matches when every
 * whitespace-separated word in it appears somewhere in the candidate's fields, ignoring case and
 * accents, so "notif live" finds "Live notifications" and "reglages" finds "Réglages".
 *
 * Twin of the Swift `SearchMatch` (Strand/System/SearchMatch.swift). Both fold the same way (canonical
 * decomposition, nonspacing marks dropped, then lowercased) so one query filters the same screens on
 * both platforms; [SearchMatchTest] pins the Swift implementation's own output for the same cases.
 */
internal object SearchMatch {
    private val nonspacingMarks = Regex("\\p{Mn}+")
    /** Unicode White_Space, the set Swift's `Character.isWhitespace` splits on. */
    private val whitespace = Regex("(?U)\\s+")

    /**
     * Case- and accent-insensitive form of [text].
     *
     * Lowercased one code point at a time, not with `String.lowercase`: that applies the contextual
     * final-sigma rule ("ΣΟΦΟΣ" ends in "ς") and Swift's `lowercased()` does not ("σ"), so the two
     * platforms would fold the same word differently. Decomposing first already split the one code point
     * whose full lowercase mapping differs from its simple one (U+0130 İ).
     */
    fun fold(text: String): String {
        val stripped = Normalizer.normalize(text, Normalizer.Form.NFD).replace(nonspacingMarks, "")
        val out = StringBuilder(stripped.length)
        stripped.codePoints().forEach { out.appendCodePoint(Character.toLowerCase(it)) }
        return out.toString()
    }

    /** The query's folded words. Empty for a blank query. */
    fun terms(query: String): List<String> =
        fold(query).split(whitespace).filter { it.isNotEmpty() }

    /**
     * True when every word of [query] occurs in at least one of [fields]. A blank query matches
     * everything, so an empty search bar hides nothing.
     */
    fun matches(query: String, fields: List<String>): Boolean {
        val words = terms(query)
        if (words.isEmpty()) return true
        val folded = fields.map(::fold)
        return words.all { word -> folded.any { it.contains(word) } }
    }
}
