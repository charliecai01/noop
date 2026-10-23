import Foundation
import WhoopProtocol

/// The heart rate a day's EFFORT integrates: the day owner's stream, with its gaps filled from a
/// supplemental source (the HR of workouts imported from the health store, `HealthWorkoutHR`).
///
/// The one deliberate exception to invariant I2 (a day's scores come from one device). A rider who wears a
/// chest strap on the bike and the WHOOP the rest of the day has two halves of one day's load, and I2 alone
/// drops the ride. Effort only: sleep, Charge, workout detection and calories still read the owner alone.
///
/// Gap-fill, never interleave. A supplemental sample is kept only where the owner has NO sample within
/// `coverageSeconds` of it, so no stretch of time is counted twice. `coverageSeconds` is Effort's own
/// per-sample duration cap (`StrainScorer.maxSampleGapMin`): within it the owner's samples already account
/// for the time, beyond it they cannot. Kotlin twin: `com.noop.analytics.EffortHrMerge`.
public enum EffortHRMerge {
    public static let coverageSeconds = Int(StrainScorer.maxSampleGapMin * 60)

    /// Both inputs ts-ascending, as the store returns them. Returns `primary` itself when there is nothing
    /// to add, so a day with no supplemental HR scores byte-identically to before. On equal timestamps the
    /// primary sample comes first, though a kept supplemental sample is never within the window to tie.
    public static func gapFill(primary: [HRSample], supplement: [HRSample]) -> [HRSample] {
        guard !supplement.isEmpty else { return primary }
        var out: [HRSample] = []
        out.reserveCapacity(primary.count + supplement.count)
        var emitted = 0      // next primary sample to copy into `out`
        var nearest = 0      // first primary sample not older than the current coverage window
        for s in supplement {
            while nearest < primary.count, primary[nearest].ts < s.ts - coverageSeconds { nearest += 1 }
            if nearest < primary.count, primary[nearest].ts <= s.ts + coverageSeconds { continue }
            while emitted < primary.count, primary[emitted].ts <= s.ts {
                out.append(primary[emitted]); emitted += 1
            }
            out.append(s)
        }
        out.append(contentsOf: primary[emitted...])
        return out
    }
}
