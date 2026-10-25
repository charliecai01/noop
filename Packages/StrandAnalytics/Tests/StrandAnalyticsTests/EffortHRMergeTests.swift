import XCTest
import WhoopProtocol
@testable import StrandAnalytics

final class EffortHRMergeTests: XCTestCase {
    private func primary(_ ts: [Int]) -> [HRSample] {
        ts.enumerated().map { HRSample(ts: $0.element, bpm: 60 + $0.offset) }
    }
    private func supplement(_ ts: [Int]) -> [HRSample] {
        ts.enumerated().map { HRSample(ts: $0.element, bpm: 150 + $0.offset) }
    }

    /// The oracle the Kotlin `EffortHrMergeTest` pastes verbatim. Primary bpm start at 60, supplemental at
    /// 150, so each output names its source. Covers the inclusive 120 s edge (1240 dropped, 1241 kept), an
    /// empty owner, an empty supplement, a supplement on both sides of a primary run, and ordering.
    func testGapFillMatchesTheKotlinOracle() {
        let scenarios: [([HRSample], [HRSample])] = [
            (primary([1000, 1060, 1120]), supplement([1100, 1240, 1241, 1300])),
            (primary([]), supplement([5, 6])),
            (primary([7, 8]), supplement([])),
            (primary([0, 1000]), supplement([100, 500, 880, 900, 1121])),
            (primary([3000]), supplement([10, 20, 2879, 2880, 3120, 3121])),
        ]
        let out = scenarios.map { a, b in
            EffortHRMerge.gapFill(primary: a, supplement: b).map { "\($0.ts):\($0.bpm)" }.joined(separator: ",")
        }
        XCTAssertEqual(out, [
            "1000:60,1060:61,1120:62,1241:152,1300:153",
            "5:150,6:151",
            "7:60,8:61",
            "0:60,500:151,1000:61,1121:154",
            "10:150,20:151,2879:152,3000:60,3121:155",
        ])
    }

    /// A ride the owner already measured adds nothing: every supplemental sample sits inside the owner's
    /// coverage, so the day scores exactly as the owner alone. This is the no-double-count guarantee.
    func testSupplementInsideOwnerCoverageLeavesEffortUnchanged() {
        let owner = (0..<7_200).map { HRSample(ts: 1_792_000_000 + $0, bpm: 150) }
        // Every other second 1000...6998, all inside the owner's 0...7199.
        let ride = (0..<3_000).map { HRSample(ts: 1_792_001_000 + $0 * 2, bpm: 170) }
        let merged = EffortHRMerge.gapFill(primary: owner, supplement: ride)
        XCTAssertEqual(merged, owner)
        XCTAssertEqual(StrainScorer.strain(merged, maxHR: 190, restingHR: 55, method: .edwards, sex: "male"),
                       StrainScorer.strain(owner, maxHR: 190, restingHR: 55, method: .edwards, sex: "male"))
    }

    /// The case this exists for: WHOOP worn before and after, a strap-recorded ride in the gap between. The
    /// filled day scores above the WHOOP-only day, and the ride's samples are all kept.
    func testRideInTheOwnersGapRaisesTheDaysEffort() {
        let morning = (0..<3_600).map { HRSample(ts: 1_792_000_000 + $0, bpm: 70) }
        let evening = (0..<3_600).map { HRSample(ts: 1_792_014_400 + $0, bpm: 70) }
        let ride = (0..<7_200).map { HRSample(ts: 1_792_005_000 + $0, bpm: 160) }
        let owner = morning + evening
        let merged = EffortHRMerge.gapFill(primary: owner, supplement: ride)
        XCTAssertEqual(merged.count, owner.count + ride.count)
        XCTAssertEqual(merged.map(\.ts), merged.map(\.ts).sorted())
        let ownerOnly = StrainScorer.strain(owner, maxHR: 190, restingHR: 55, method: .edwards, sex: "male") ?? 0
        let filled = StrainScorer.strain(merged, maxHR: 190, restingHR: 55, method: .edwards, sex: "male") ?? 0
        XCTAssertGreaterThan(filled, ownerOnly)
    }

    /// Day Effort fills from each supplemental source in turn (a recorded chest strap, then imported workout
    /// HR), feeding each pass the previous one's output as its primary. The second source therefore yields
    /// to the first exactly as it yields to the owner, so no stretch is counted twice. Owner bpm start at 60,
    /// strap at 150, imported at 200. Kotlin `EffortHrMergeTest` pastes this literal.
    func testChainedGapFillMatchesTheKotlinOracle() {
        let owner = primary([0, 60, 5000])
        let strap = supplement([1000, 1001, 1002, 1500])
        let imported = [990, 1100, 1700, 4950, 6000].enumerated().map { HRSample(ts: $0.element, bpm: 200 + $0.offset) }
        let merged = EffortHRMerge.gapFill(primary: EffortHRMerge.gapFill(primary: owner, supplement: strap),
                                           supplement: imported)
        XCTAssertEqual(merged.map { "\($0.ts):\($0.bpm)" }.joined(separator: ","),
                       "0:60,60:61,1000:150,1001:151,1002:152,1500:153,1700:202,5000:62,6000:204")
    }
}
