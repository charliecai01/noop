package com.noop.analytics

import com.noop.data.HrSample

/**
 * The heart rate a day's EFFORT integrates: the day owner's stream, with its gaps filled from a
 * supplemental source (the HR of workouts imported from the health store, [com.noop.data.HealthWorkoutHr]).
 *
 * The one deliberate exception to invariant I2 (a day's scores come from one device). A rider who wears a
 * chest strap on the bike and the WHOOP the rest of the day has two halves of one day's load, and I2 alone
 * drops the ride. Effort only: sleep, Charge, workout detection and calories still read the owner alone.
 *
 * Gap-fill, never interleave. A supplemental sample is kept only where the owner has NO sample within
 * [COVERAGE_SECONDS] of it, so no stretch of time is counted twice. [COVERAGE_SECONDS] is Effort's own
 * per-sample duration cap ([StrainScorer.maxSampleGapMin]): within it the owner's samples already account
 * for the time, beyond it they cannot. Twin of Swift `EffortHRMerge` (StrandAnalytics).
 */
object EffortHrMerge {
    val COVERAGE_SECONDS: Long = (StrainScorer.maxSampleGapMin * 60).toLong()

    /** Both inputs ts-ascending, as the store returns them. Returns [primary] itself when there is nothing
     *  to add, so a day with no supplemental HR scores byte-identically to before. On equal timestamps the
     *  primary sample comes first, though a kept supplemental sample is never within the window to tie. */
    fun gapFill(primary: List<HrSample>, supplement: List<HrSample>): List<HrSample> {
        if (supplement.isEmpty()) return primary
        val out = ArrayList<HrSample>(primary.size + supplement.size)
        var emitted = 0 // next primary sample to copy into `out`
        var nearest = 0 // first primary sample not older than the current coverage window
        for (s in supplement) {
            while (nearest < primary.size && primary[nearest].ts < s.ts - COVERAGE_SECONDS) nearest++
            if (nearest < primary.size && primary[nearest].ts <= s.ts + COVERAGE_SECONDS) continue
            while (emitted < primary.size && primary[emitted].ts <= s.ts) {
                out.add(primary[emitted])
                emitted++
            }
            out.add(s)
        }
        while (emitted < primary.size) out.add(primary[emitted++])
        return out
    }
}
