import XCTest
import StrandDesign
import WhoopStore
@testable import Strand

/// The gauge's band edges are literals because StrandDesign cannot import WhoopStore. This pins them to
/// WHOOP's 6 / 10 / 14 / 18 converted through load (`EffortCurve`), so the words cannot drift from the
/// curve they were derived from.
final class StrainGaugeBandTests: XCTestCase {
    func testBandEdgesAreWhoopEdgesConvertedThroughLoad() {
        let derived = [6.0, 10, 14, 18].map { (EffortCurve.effortFromWhoopStrain($0) / 100 * 10_000).rounded() / 10_000 }
        XCTAssertEqual(StrainGauge.bandEdges, derived)
    }

    /// The rides this change was made for keep the words they had on the log axis.
    func testSameLoadKeepsItsWord() {
        XCTAssertEqual(StrainGauge.stateLabel(forFraction: 0.2765), "STRENUOUS")   // easy hour, was 51 → WHOOP 10.7
        XCTAssertEqual(StrainGauge.stateLabel(forFraction: 0.6996), "HIGH")        // 4 h ride, was 71.8 → WHOOP 15.1
    }
}

/// The coupled screen's optimal bands, pinned to WHOOP's 14–18 / 10–14 / 4–10 converted through load and
/// put on the screen's 0–21 dial (× 0.21, `UnitFormatter.effortValue(_:scale: .whoop)`).
final class CoupledOptimalBandTests: XCTestCase {
    private func dial(_ whoop: Double) -> Int {
        Int(UnitFormatter.effortValue(EffortCurve.effortFromWhoopStrain(whoop), scale: .whoop).rounded())
    }

    func testBandsAreWhoopBandsConvertedThroughLoad() {
        XCTAssertEqual(CoupledView.optimalStrainRange(recovery: 80), dial(14)...dial(18))
        XCTAssertEqual(CoupledView.optimalStrainRange(recovery: 50), dial(10)...dial(14))
        XCTAssertEqual(CoupledView.optimalStrainRange(recovery: 20), dial(4)...dial(10))
        XCTAssertEqual(CoupledView.optimalStrainRange(recovery: 80), 12...21)
        XCTAssertEqual(CoupledView.optimalStrainRange(recovery: 50), 5...12)
        XCTAssertEqual(CoupledView.optimalStrainRange(recovery: 20), 1...5)
        XCTAssertNil(CoupledView.optimalStrainRange(recovery: nil))
    }
}
