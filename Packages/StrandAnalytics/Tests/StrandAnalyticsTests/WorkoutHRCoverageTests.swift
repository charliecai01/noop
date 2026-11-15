import XCTest
@testable import StrandAnalytics
import WhoopProtocol

/// An imported workout scores from the stream that measured the session, not the first stream that
/// cleared a sample floor. The fixture is the reported ride: a strap NOOP caught in bursts beside a
/// complete HealthKit stream.
final class WorkoutHRCoverageTests: XCTestCase {

    /// 1 Hz readings over `[from, to)` seconds.
    private func run(_ from: Int, _ to: Int, bpm: Int = 140) -> [HRSample] {
        stride(from: from, to: to, by: 1).map { HRSample(ts: $0, bpm: bpm) }
    }

    func testCoveredMinutesCapsEachGap() {
        // Two 60-second runs with a 30-minute hole: 59 s + 2 min (the capped hole) + 59 s + 1 s reused.
        let hr = run(0, 60) + run(1860, 1920)
        XCTAssertEqual(WorkoutHRCoverage.coveredMinutes(hr), (59 + 120 + 59 + 1) / 60.0, accuracy: 1e-9)
        XCTAssertEqual(WorkoutHRCoverage.coveredMinutes([]), 0)
        XCTAssertEqual(WorkoutHRCoverage.coveredMinutes([HRSample(ts: 0, bpm: 120)]), 0)
    }

    /// The report: nine strap bursts totalling ~21 minutes across a 191-minute ride lose to the full
    /// HealthKit stream even though the strap comes first in precedence.
    func testBurstyStrapLosesToCompleteHealthStream() {
        var strap: [HRSample] = []
        for burst in 0..<9 { strap += run(1380 + burst * 1250, 1380 + burst * 1250 + 140) }
        let health = run(0, 191 * 60)
        let coverage = [strap, health].map(WorkoutHRCoverage.coveredMinutes)
        XCTAssertEqual(WorkoutHRCoverage.pick(coverage: coverage, workoutMinutes: 191), 1)
    }

    /// Two streams that both measured the ride: the strap keeps precedence over a thinner watch copy.
    func testStrapKeepsPrecedenceWhenBothCoverTheRide() {
        let strap = run(0, 3600)
        let watch = stride(from: 0, to: 3600, by: 5).map { HRSample(ts: $0, bpm: 140) }
        let coverage = [strap, watch].map(WorkoutHRCoverage.coveredMinutes)
        XCTAssertEqual(WorkoutHRCoverage.pick(coverage: coverage, workoutMinutes: 60), 0)
    }

    /// Nothing covers half the session: no stream is picked, so Effort stays blank instead of low.
    func testNoStreamPickedWhenNoneCoversHalfTheWorkout() {
        let coverage = [WorkoutHRCoverage.coveredMinutes(run(0, 1200))]   // 20 of 191 minutes
        XCTAssertNil(WorkoutHRCoverage.pick(coverage: coverage, workoutMinutes: 191))
        XCTAssertNil(WorkoutHRCoverage.pick(coverage: [], workoutMinutes: 60))
        XCTAssertNil(WorkoutHRCoverage.pick(coverage: [0, 0], workoutMinutes: 60))
    }
}
