package com.noop.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins [SearchMatch] to the Swift twin's own output. The expected block is the stdout of the Swift
 * `SearchMatch` compiled standalone (`swiftc -O SearchMatch.swift main.swift`) over the cases below,
 * pasted verbatim, so a query folds, splits and matches the same way on both platforms. Strings are
 * written as hex code points so invisible characters (NBSP, ZWSP, combining marks) stay readable.
 *
 * It already earned its keep: `String.lowercase` applies the Greek final-sigma rule and Swift's
 * `lowercased()` does not, which the "ΣΟΦΟΣ" line caught before either side shipped.
 */
class SearchMatchTest {

    private fun hex(s: String): String =
        s.codePoints().toArray().joinToString(" ") { Integer.toHexString(it) }

    private val foldCases = listOf(
        "Réglages", "LIVE Notifications", "Ångström", "İstanbul", "Straße", "Ελληνικά ΣΟΦΟΣ", "Café",
        "Cafe\u0301", "ﬁle", "Ⅻ", "日本語", "Ｆｕｌｌ", "Ǆ", "ŉ", "Ñandú", "Œuvre", "Ǳ", "ﾊﾟ", "가",
        "\uD83D\uDC4D\uD83C\uDFFD", "Ωmega",
    )
    private val termCases = listOf(
        "  live   notif ", "a\u00A0b", "a\u2003b", "a\u200Bb", "a\tb\nc", "a\u0085b", "a\u2028b",
        "a\u180Eb", "a\u3000b", "a\uFEFFb", "a\r\nb", "", "   ",
    )
    private val matchCases: List<Pair<String, List<String>>> = listOf(
        "notif live" to listOf("Live notifications"), "reglages" to listOf("Réglages"),
        "hr" to listOf("Live heart rate"), "heart" to listOf("Strap", "Live heart rate"),
        "" to emptyList(), "ai coach" to listOf("Appearance", "AI Coach"),
        "coach xyz" to listOf("AI Coach"), "STRASSE" to listOf("Straße"), "straße" to listOf("STRASSE"),
        "ÉTÉ" to listOf("ete"), "live notif" to listOf("Live", "notifications"), "x" to emptyList(),
        "data" to listOf("Backup & Sync", "Data"),
    )

    /** Swift `SearchMatch` stdout over the same cases, verbatim. */
    private val swiftOracle = listOf(
            "F 52 e9 67 6c 61 67 65 73 => 72 65 67 6c 61 67 65 73",
            "F 4c 49 56 45 20 4e 6f 74 69 66 69 63 61 74 69 6f 6e 73 => 6c 69 76 65 20 6e 6f 74 69 66 69 63 61 74 69 6f 6e 73",
            "F c5 6e 67 73 74 72 f6 6d => 61 6e 67 73 74 72 6f 6d",
            "F 130 73 74 61 6e 62 75 6c => 69 73 74 61 6e 62 75 6c",
            "F 53 74 72 61 df 65 => 73 74 72 61 df 65",
            "F 395 3bb 3bb 3b7 3bd 3b9 3ba 3ac 20 3a3 39f 3a6 39f 3a3 => 3b5 3bb 3bb 3b7 3bd 3b9 3ba 3b1 20 3c3 3bf 3c6 3bf 3c3",
            "F 43 61 66 e9 => 63 61 66 65",
            "F 43 61 66 65 301 => 63 61 66 65",
            "F fb01 6c 65 => fb01 6c 65",
            "F 216b => 217b",
            "F 65e5 672c 8a9e => 65e5 672c 8a9e",
            "F ff26 ff55 ff4c ff4c => ff46 ff55 ff4c ff4c",
            "F 1c4 => 1c6",
            "F 149 => 149",
            "F d1 61 6e 64 fa => 6e 61 6e 64 75",
            "F 152 75 76 72 65 => 153 75 76 72 65",
            "F 1f1 => 1f3",
            "F ff8a ff9f => ff8a ff9f",
            "F ac00 => 1100 1161",
            "F 1f44d 1f3fd => 1f44d 1f3fd",
            "F 3a9 6d 65 67 61 => 3c9 6d 65 67 61",
            "T 20 20 6c 69 76 65 20 20 20 6e 6f 74 69 66 20 => 6c 69 76 65 | 6e 6f 74 69 66",
            "T 61 a0 62 => 61 | 62",
            "T 61 2003 62 => 61 | 62",
            "T 61 200b 62 => 61 200b 62",
            "T 61 9 62 a 63 => 61 | 62 | 63",
            "T 61 85 62 => 61 | 62",
            "T 61 2028 62 => 61 | 62",
            "T 61 180e 62 => 61 180e 62",
            "T 61 3000 62 => 61 | 62",
            "T 61 feff 62 => 61 feff 62",
            "T 61 d a 62 => 61 | 62",
            "T  => ",
            "T 20 20 20 => ",
            "M 6e 6f 74 69 66 20 6c 69 76 65 [4c 69 76 65 20 6e 6f 74 69 66 69 63 61 74 69 6f 6e 73] => true",
            "M 72 65 67 6c 61 67 65 73 [52 e9 67 6c 61 67 65 73] => true",
            "M 68 72 [4c 69 76 65 20 68 65 61 72 74 20 72 61 74 65] => false",
            "M 68 65 61 72 74 [53 74 72 61 70,4c 69 76 65 20 68 65 61 72 74 20 72 61 74 65] => true",
            "M  [] => true",
            "M 61 69 20 63 6f 61 63 68 [41 70 70 65 61 72 61 6e 63 65,41 49 20 43 6f 61 63 68] => true",
            "M 63 6f 61 63 68 20 78 79 7a [41 49 20 43 6f 61 63 68] => false",
            "M 53 54 52 41 53 53 45 [53 74 72 61 df 65] => false",
            "M 73 74 72 61 df 65 [53 54 52 41 53 53 45] => false",
            "M c9 54 c9 [65 74 65] => true",
            "M 6c 69 76 65 20 6e 6f 74 69 66 [4c 69 76 65,6e 6f 74 69 66 69 63 61 74 69 6f 6e 73] => true",
            "M 78 [] => false",
            "M 64 61 74 61 [42 61 63 6b 75 70 20 26 20 53 79 6e 63,44 61 74 61] => true",
    )

    @Test
    fun foldTermsAndMatchesAgreeWithTheSwiftTwin() {
        val actual = foldCases.map { "F ${hex(it)} => ${hex(SearchMatch.fold(it))}" } +
            termCases.map { "T ${hex(it)} => ${SearchMatch.terms(it).joinToString(" | ") { w -> hex(w) }}" } +
            matchCases.map { (q, f) ->
                "M ${hex(q)} [${f.joinToString(",") { hex(it) }}] => ${SearchMatch.matches(q, f)}"
            }
        assertEquals(swiftOracle.joinToString("\n"), actual.joinToString("\n"))
    }
}
