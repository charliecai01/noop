package com.noop.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Kotlin twin of `WorkoutEffortCurveTests` (Swift). The expected literals are the stdout of the Swift
 * formula compiled standalone (`swiftc -O twin.swift main.swift`), per the parity contract in AGENTS.md.
 */
class WorkoutEffortCurveTest {

    @Test
    fun oracleSpread() {
        val cases = listOf(
            -5.0 to 0.0, 0.0 to 0.0, 0.01 to 0.09, 7.8 to 2.89, 25.0 to 8.27, 40.0 to 16.81,
            51.0 to 27.65, 51.96 to 28.87, 60.0 to 41.36, 65.0 to 51.69, 71.8 to 69.96,
            75.0 to 80.66, 79.0 to 96.36, 80.0 to 100.0, 90.0 to 100.0, 100.0 to 100.0, 150.0 to 100.0,
        )
        for ((day, workout) in cases) {
            assertEquals("day $day", workout, StrainScorer.workoutEffort(day), 0.0)
        }
    }

    @Test
    fun nonFiniteScoresZero() {
        assertEquals(0.0, StrainScorer.workoutEffort(Double.NaN), 0.0)
        assertEquals(0.0, StrainScorer.workoutEffort(Double.POSITIVE_INFINITY), 0.0)
    }

    @Test
    fun monotoneAndBounded() {
        var previous = -1.0
        for (i in 0..10_000) {
            val w = StrainScorer.workoutEffort(i / 100.0)
            assertTrue(w >= previous)
            assertTrue(w in 0.0..100.0)
            previous = w
        }
    }
}
