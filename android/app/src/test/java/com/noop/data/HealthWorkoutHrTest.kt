package com.noop.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HealthWorkoutHrTest {
    /**
     * The expected string is the stdout of the Swift twin (`HealthWorkoutHR.sample`, compiled standalone)
     * over the same cases, pasted verbatim; `HealthWorkoutHRTests` asserts the same literal on the Swift
     * side. Health Connect hands over `Instant.epochSecond`, so each fractional Swift start appears here
     * already floored. Pins rounding half up (72.5 → 73, where `kotlin.math.round` would give 72), both
     * inclusive bounds, and a raw value outside the range that would round into it.
     */
    @Test
    fun sampleRuleMatchesTheSwiftOracle() {
        val cases = listOf(
            1_792_000_000L to 72.0, 1_792_000_000L to 72.5, 1_792_000_001L to 72.49,
            1_792_000_002L to 24.999, 1_792_000_003L to 25.0, 1_792_000_004L to 240.0,
            1_792_000_005L to 240.0001, 1_792_000_006L to 239.5, 1_792_000_007L to 24.6,
            1_792_000_008L to 180.5, 1_792_000_009L to 0.0, 1_792_000_010L to -5.0,
            1_792_000_011L to Double.NaN, 1_792_000_012L to Double.POSITIVE_INFINITY, 1_792_000_013L to 25.4,
        )
        val out = cases.joinToString(",") { (ts, bpm) ->
            HealthWorkoutHr.sample(ts, bpm)?.let { "${it.ts}:${it.bpm}" } ?: "nil"
        }
        assertEquals(
            "1792000000:72,1792000000:73,1792000001:72,nil,1792000003:25,1792000004:240,nil," +
                "1792000006:240,nil,1792000008:181,nil,nil,nil,nil,1792000013:25",
            out,
        )
    }

    @Test
    fun sampleIsStoredUnderTheSharedSourceId() {
        assertEquals("apple-health-workouts", HealthWorkoutHr.sample(1_792_000_000L, 140.0)?.deviceId)
        assertNull(HealthWorkoutHr.sample(1_792_000_000L, Double.NaN))
    }

    /** Ranked by the day-owner resolver as an activity file (priority 3), never as the active device. */
    @Test
    fun registryDeviceIsAPairedActivityFileSourceForHrOnly() {
        assertEquals(
            PairedDeviceRow(
                id = "apple-health-workouts",
                brand = "Health Connect workouts",
                model = "",
                nickname = null,
                peripheralId = null,
                sourceKind = "activityFile",
                capabilities = "hr",
                status = "paired",
                addedAt = 1_792_000_000L,
                lastSeenAt = 1_792_000_000L,
            ),
            HealthWorkoutHr.registryDevice("Health Connect workouts", 1_792_000_000L),
        )
    }
}
