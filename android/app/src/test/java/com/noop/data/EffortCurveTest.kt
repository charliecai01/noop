package com.noop.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Kotlin twin of the Swift `EffortCurveTests`. Every literal is the stdout of `EffortCurve.swift` compiled
 * standalone (`swiftc -O EffortCurve.swift main.swift`), per the parity contract in AGENTS.md.
 */
class EffortCurveTest {

    @Test
    fun trimpToEffort() {
        val cases = listOf(
            -5.0 to 0.0, 0.0 to 0.0, 1.0 to 2.89, 50.0 to 20.41, 75.0 to 25.0, 92.0 to 27.69,
            100.0 to 28.87, 300.0 to 50.0, 590.0 to 70.12, 675.0 to 75.0, 1200.0 to 100.0,
            2000.0 to 100.0, 7200.0 to 100.0,
        )
        for ((trimp, effort) in cases) assertEquals("TRIMP $trimp", effort, EffortCurve.effortFromTrimp(trimp), 0.0)
        assertEquals(100.0, EffortCurve.effortFromTrimp(Double.POSITIVE_INFINITY), 0.0)
        assertTrue(EffortCurve.effortFromTrimp(Double.NaN).isNaN())
    }

    @Test
    fun legacyToEffort() {
        val cases = listOf(
            -5.0 to 0.0, 0.0 to 0.0, 0.01 to 0.09, 7.8 to 2.89, 25.0 to 8.27, 30.0 to 10.55,
            40.0 to 16.81, 44.27 to 20.41, 51.0 to 27.65, 51.96 to 28.87, 60.0 to 41.36,
            65.0 to 51.69, 71.8 to 69.96, 75.0 to 80.66, 79.0 to 96.36, 80.0 to 100.0,
            100.0 to 100.0, 150.0 to 100.0,
        )
        for ((legacy, effort) in cases) assertEquals("legacy $legacy", effort, EffortCurve.effortFromLegacy(legacy), 0.0)
    }

    @Test
    fun effortToLegacy() {
        val cases = listOf(
            0.0 to 0.0, 5.0 to 15.61, 10.0 to 28.88, 20.0 to 43.82, 25.0 to 48.76,
            27.65 to 51.0, 30.0 to 52.82, 50.0 to 64.25, 70.0 to 71.81, 100.0 to 79.83,
        )
        for ((effort, legacy) in cases) assertEquals("effort $effort", legacy, EffortCurve.legacyFromEffort(effort), 0.0)
    }

    @Test
    fun whoopStrainBothWays() {
        val toEffort = listOf(
            0.0 to 0.0, 1.0 to 2.09, 4.0 to 6.08, 6.0 to 9.85, 10.0 to 23.75, 14.0 to 55.67,
            17.5 to 100.0, 21.0 to 100.0,
        )
        for ((whoop, effort) in toEffort) assertEquals("WHOOP $whoop", effort, EffortCurve.effortFromWhoopStrain(whoop), 0.0)
        val toWhoop = listOf(0.0 to 0.0, 10.0 to 6.06, 25.0 to 10.24, 50.0 to 13.49, 70.0 to 15.08, 100.0 to 16.76)
        for ((effort, whoop) in toWhoop) assertEquals("effort $effort", whoop, EffortCurve.whoopStrainFromEffort(effort), 0.0)
    }

    @Test
    fun trimpFromEffortInvertsTheAxis() {
        assertEquals(75.0, EffortCurve.trimpFromEffort(25.0), 1e-9)
        assertEquals(300.0, EffortCurve.trimpFromEffort(50.0), 1e-9)
        assertEquals(1200.0, EffortCurve.trimpFromEffort(100.0), 1e-9)
        assertEquals(1200.0, EffortCurve.trimpFromEffort(150.0), 1e-9)
        assertEquals(0.0, EffortCurve.trimpFromEffort(-1.0), 0.0)
    }

    @Test
    fun fourTimesTheLoadIsTwiceTheScore() {
        assertEquals(2.0, EffortCurve.effortFromTrimp(300.0) / EffortCurve.effortFromTrimp(75.0), 1e-9)
    }

    @Test
    fun monotoneAndBounded() {
        var previous = -1.0
        for (i in 0..10_000) {
            val e = EffortCurve.effortFromLegacy(i / 100.0)
            assertTrue(e >= previous)
            assertTrue(e in 0.0..100.0)
            previous = e
        }
    }
}
