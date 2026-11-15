import Foundation
import WhoopProtocol

/// Which heart-rate stream an imported workout's Effort is scored from, judged by how much of the session
/// each stream actually measured.
///
/// The iOS Apple Health import has up to three streams for one ride: a chest strap NOOP recorded itself,
/// the heart rate HealthKit associates with the workout, and the WHOOP trace. It used to take the first
/// stream, in that order, that cleared a sample-count and first-to-last span floor. A strap NOOP only
/// caught in bursts clears both: on one three-hour ride it held 1 263 readings spread from minute 23 to
/// minute 190, so it outranked a complete 13 178-reading HealthKit stream, and the ride scored 20.7 from
/// the 21 minutes it held where the whole ride scores about 63.
///
/// Coverage is the time TRIMP integrates: the sum of `StrainScorer.sampleDurationsMinutes`, each
/// reading's gap to the next capped at `StrainScorer.maxSampleGapMin`. A stream's span says nothing about
/// the holes inside it; this does.
public enum WorkoutHRCoverage {
    /// A stream earlier in precedence order keeps its place while it covers at least this share of what
    /// the best-covered stream does. A 1 Hz strap and a watch's thinner copy of the same ride cover the
    /// session alike, and the strap should still answer; a strap that missed most of the ride should not.
    public static let nearBestFraction = 0.9

    /// The share of the workout the chosen stream must cover for Effort to be scored at all. Below it the
    /// session was not measured, and a blank Effort is more honest than a fraction of one.
    public static let minimumWorkoutFraction = 0.5

    /// Minutes of `hr` that TRIMP integrates. Zero for an empty stream.
    public static func coveredMinutes(_ hr: [HRSample]) -> Double {
        guard hr.count >= 2 else { return 0 }
        return StrainScorer.sampleDurationsMinutes(hr).reduce(0, +)
    }

    /// The index into `coverage` (covered minutes per candidate stream, in precedence order) of the stream
    /// to score from, or nil when none covers enough of a `workoutMinutes`-long session.
    ///
    /// The first candidate within `nearBestFraction` of the best coverage wins, so precedence still
    /// decides between streams that measured the session equally well.
    public static func pick(coverage: [Double], workoutMinutes: Double) -> Int? {
        guard let best = coverage.max(), best > 0 else { return nil }
        guard workoutMinutes <= 0 || best >= minimumWorkoutFraction * workoutMinutes else { return nil }
        return coverage.firstIndex { $0 >= nearBestFraction * best }
    }
}
