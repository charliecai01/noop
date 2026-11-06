package com.noop.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Log-map denominator behavior outside the map's domain, against the compiled Swift twin.
 *
 * D ≤ 1 has no ln-based score: ln(1) = 0 divides to ±∞, ln(D) < 0 below 1 flips the sign, and ln(D)
 * is NaN at or below 0. Before the guard, Swift returned `+Inf` for D = 1 while this side's
 * `roundToLong()` saturated to 9.223372036854776E16 — the same input, two different answers.
 *
 * Every expected value below is the verbatim stdout of `StrainScorer.swift`'s `trimpToStrain`,
 * extracted and compiled standalone on Linux (`swiftc -O main.swift -o t && ./t`), printed as
 * `trimp|D|effort` with `%.17g`:
 *
 * ```
 * 1.0000|1|0
 * 1.0000|0.5|0
 * 1.0000|0|0
 * 1.0000|-3|0
 * 1.0000|nan|0
 * 0.0000|7201|0
 * 1.0000|7201|7.7999999999999998
 * 100.0000|7201|51.960000000000001
 * 7200.0000|7201|100
 * 1.0000|1.0000000000000002|3.1216573840826803e+17
 * 1.0000|2|100
 * ```
 */
class StrainScorerDenominatorDomainTest {

    @Test
    fun `denominator at or below one scores zero`() {
        for (denominator in listOf(1.0, 0.5, 0.0, -3.0, Double.NaN)) {
            val s = StrainScorer.trimpToStrain(1.0, denominator)
            assertTrue("D = $denominator produced a non-finite Effort", s.isFinite())
            assertEquals("D = $denominator", 0.0, s, 0.0)
        }
    }

    @Test
    fun `valid denominators are unchanged`() {
        // Square-root axis since MIGRATION_42_43 (EffortCurve); Swift twin's stdout.
        assertEquals(2.89, StrainScorer.trimpToStrain(1.0, 7201.0), 1e-9)
        assertEquals(28.87, StrainScorer.trimpToStrain(100.0, 7201.0), 1e-9)
        assertEquals(100.0, StrainScorer.trimpToStrain(7200.0, 7201.0), 1e-9)
        assertEquals(100.0, StrainScorer.trimpToStrain(1.0, 2.0), 1e-9)
    }

    /**
     * Just above the boundary the session ceiling is a vanishing positive TRIMP, so any load saturates.
     * Under the log map this was a finite 3.1e17; the square-root axis clamps it to its top.
     */
    @Test
    fun `denominator just above one stays finite`() {
        val s = StrainScorer.trimpToStrain(1.0, 1.0000000000000002)
        assertTrue(s.isFinite())
        assertEquals(100.0, s, 0.0)
    }

    /**
     * Non-finite TRIMP propagates rather than being caught by the domain guard — the guard is about
     * D, not TRIMP. Both platforms agree here only because this side no longer rounds through Long
     * (`roundToLong()` mapped +∞ to Long.MAX), so pin the non-finite propagation behavior. Swift
     * oracle, same formula:
     * `inf|7201.0|inf`, `nan|7201.0|nan`.
     */
    @Test
    fun `non-finite trimp propagates`() {
        // +∞ load sits at the axis' ceiling; NaN still propagates so corrupt input stays visible.
        val inf = StrainScorer.trimpToStrain(Double.POSITIVE_INFINITY, 7201.0)
        assertEquals(100.0, inf, 0.0)
        assertTrue(StrainScorer.trimpToStrain(Double.NaN, 7201.0).isNaN())
    }
}
