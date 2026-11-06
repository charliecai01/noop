import XCTest
@testable import StrandAnalytics

/// The workout Effort curve: a stored day-axis Effort re-mapped to `100 × √(TRIMP / 1200)`.
///
/// The Kotlin twin is `WorkoutEffortCurveTest`; its expected literals are the stdout of this formula
/// compiled standalone (`swiftc -O twin.swift main.swift`), per the parity contract in AGENTS.md.
final class WorkoutEffortCurveTests: XCTestCase {

    func testOracleSpread() {
        let cases: [(Double, Double)] = [
            (-5, 0), (0, 0), (0.01, 0.09), (7.8, 2.89), (25, 8.27), (40, 16.81), (51, 27.65),
            (51.96, 28.87), (60, 41.36), (65, 51.69), (71.8, 69.96), (75, 80.66), (79, 96.36),
            (80, 100), (90, 100), (100, 100), (150, 100),
        ]
        for (day, workout) in cases {
            XCTAssertEqual(StrainScorer.workoutEffort(fromDayScale: day), workout, accuracy: 0, "day \(day)")
        }
    }

    func testNonFiniteScoresZero() {
        XCTAssertEqual(StrainScorer.workoutEffort(fromDayScale: .nan), 0)
        XCTAssertEqual(StrainScorer.workoutEffort(fromDayScale: .infinity), 0)
    }

    /// The point of the curve: a session carrying ~6.4x the load must read far more than ~1.4x the score.
    func testLoadRatioSurvivesTheCurve() {
        let easy = StrainScorer.trimpToStrain(92)    // an easy hour on the bike
        let hard = StrainScorer.trimpToStrain(590)   // a hard four-hour ride
        XCTAssertLessThan(hard / easy, 1.5, "the day axis compresses the pair")
        let ratio = StrainScorer.workoutEffort(fromDayScale: hard) / StrainScorer.workoutEffort(fromDayScale: easy)
        XCTAssertGreaterThan(ratio, 2.4)
    }

    /// Exact inverse of the day map: TRIMP at the ceiling lands on 100, a quarter of it on 50.
    func testCeilingAndQuarterCeiling() {
        let full = StrainScorer.trimpToStrain(StrainScorer.workoutEffortCeilingTrimp)
        XCTAssertEqual(StrainScorer.workoutEffort(fromDayScale: full), 100, accuracy: 0.1)
        let quarter = StrainScorer.trimpToStrain(StrainScorer.workoutEffortCeilingTrimp / 4)
        XCTAssertEqual(StrainScorer.workoutEffort(fromDayScale: quarter), 50, accuracy: 0.1)
    }

    func testMonotoneAndBounded() {
        var previous = -1.0
        for i in 0...10_000 {
            let w = StrainScorer.workoutEffort(fromDayScale: Double(i) / 100)
            XCTAssertGreaterThanOrEqual(w, previous)
            XCTAssertTrue((0...100).contains(w))
            previous = w
        }
    }
}
