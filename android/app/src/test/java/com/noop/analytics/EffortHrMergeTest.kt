package com.noop.analytics

import com.noop.data.HrSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class EffortHrMergeTest {
    private fun primary(vararg ts: Long) = ts.mapIndexed { i, t -> HrSample("my-whoop", t, 60 + i) }
    private fun supplement(vararg ts: Long) = ts.mapIndexed { i, t -> HrSample("apple-health-workouts", t, 150 + i) }

    /**
     * The expected lines are the stdout of the Swift twin (`EffortHRMerge.gapFill`, compiled standalone)
     * over the same scenarios, pasted verbatim; `EffortHRMergeTests` asserts the same literal on the Swift
     * side. Primary bpm start at 60, supplemental at 150, so each output names its source.
     */
    @Test
    fun gapFillMatchesTheSwiftOracle() {
        val scenarios = listOf(
            primary(1000, 1060, 1120) to supplement(1100, 1240, 1241, 1300),
            primary() to supplement(5, 6),
            primary(7, 8) to supplement(),
            primary(0, 1000) to supplement(100, 500, 880, 900, 1121),
            primary(3000) to supplement(10, 20, 2879, 2880, 3120, 3121),
        )
        val out = scenarios.map { (a, b) ->
            EffortHrMerge.gapFill(a, b).joinToString(",") { "${it.ts}:${it.bpm}" }
        }
        assertEquals(
            listOf(
                "1000:60,1060:61,1120:62,1241:152,1300:153",
                "5:150,6:151",
                "7:60,8:61",
                "0:60,500:151,1000:61,1121:154",
                "10:150,20:151,2879:152,3000:60,3121:155",
            ),
            out,
        )
    }

    @Test
    fun noSupplementReturnsTheOwnerStreamItself() {
        val owner = primary(1, 2, 3)
        assertSame(owner, EffortHrMerge.gapFill(owner, emptyList()))
    }

    /** A ride the owner already measured adds nothing, so the day scores exactly as the owner alone. */
    @Test
    fun supplementInsideOwnerCoverageLeavesEffortUnchanged() {
        val owner = (0 until 7_200).map { HrSample("my-whoop", 1_792_000_000L + it, 150) }
        // Every other second 1000..6998, all inside the owner's 0..7199.
        val ride = (0 until 3_000).map { HrSample("apple-health-workouts", 1_792_001_000L + it * 2L, 170) }
        val merged = EffortHrMerge.gapFill(owner, ride)
        assertEquals(owner, merged)
    }

    /** WHOOP worn before and after, a strap-recorded ride in the gap: every ride sample is kept, in order,
     *  and the filled day scores above the WHOOP-only day. */
    @Test
    fun rideInTheOwnersGapRaisesTheDaysEffort() {
        val morning = (0 until 3_600).map { HrSample("my-whoop", 1_792_000_000L + it, 70) }
        val evening = (0 until 3_600).map { HrSample("my-whoop", 1_792_014_400L + it, 70) }
        val ride = (0 until 7_200).map { HrSample("apple-health-workouts", 1_792_005_000L + it, 160) }
        val owner = morning + evening
        val merged = EffortHrMerge.gapFill(owner, ride)
        assertEquals(owner.size + ride.size, merged.size)
        assertEquals(merged.map { it.ts }.sorted(), merged.map { it.ts })
        val ownerOnly = StrainScorer.strain(owner, 190.0, 55.0, StrainScorer.Method.EDWARDS, "male") ?: 0.0
        val filled = StrainScorer.strain(merged, 190.0, 55.0, StrainScorer.Method.EDWARDS, "male") ?: 0.0
        assertTrue(filled > ownerOnly)
    }

    /** Day Effort fills from each supplemental source in turn (a recorded chest strap, then imported workout
     *  HR), feeding each pass the previous one's output as its primary, so no stretch is counted twice. The
     *  expected line is the Swift twin's output (`EffortHRMergeTests.testChainedGapFillMatchesTheKotlinOracle`),
     *  pasted verbatim. Owner bpm start at 60, strap at 150, imported at 200. */
    @Test
    fun chainedGapFillMatchesTheSwiftOracle() {
        val owner = primary(0, 60, 5000)
        val strap = listOf(1000L, 1001L, 1002L, 1500L).mapIndexed { i, t -> HrSample("strap-1", t, 150 + i) }
        val imported = listOf(990L, 1100L, 1700L, 4950L, 6000L)
            .mapIndexed { i, t -> HrSample("apple-health-workouts", t, 200 + i) }
        val merged = EffortHrMerge.gapFill(EffortHrMerge.gapFill(owner, strap), imported)
        assertEquals(
            "0:60,60:61,1000:150,1001:151,1002:152,1500:153,1700:202,5000:62,6000:204",
            merged.joinToString(",") { "${it.ts}:${it.bpm}" },
        )
    }
}
