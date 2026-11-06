import XCTest
@testable import StrandAnalytics

/// Log-map denominator behavior outside the map's domain.
///
/// D ≤ 1 has no ln-based score: ln(1) = 0 divides to ±∞, ln(D) < 0 below 1 flips the sign, and ln(D)
/// is NaN at or below 0. Before the guard, Swift returned `+Inf` for D = 1 while Kotlin's
/// `roundToLong()` saturated to 9.223372036854776E16 — the same input, two different answers.
/// The Kotlin twin is `StrainScorerDenominatorDomainTest`; its expected literals are the stdout of
/// this file's formula compiled standalone.
final class StrainDenominatorDomainTests: XCTestCase {

    func testDenominatorAtOrBelowOneScoresZero() {
        for denominator in [1.0, 0.5, 0.0, -3.0, Double.nan] {
            let s = StrainScorer.trimpToStrain(1, denominator: denominator)
            XCTAssertTrue(s.isFinite, "D = \(denominator) produced a non-finite Effort")
            XCTAssertEqual(s, 0.0, accuracy: 0, "D = \(denominator)")
        }
    }

    func testValidDenominatorsAreUnchanged() {
        XCTAssertEqual(StrainScorer.trimpToStrain(1, denominator: 7201), 2.89, accuracy: 1e-9)
        XCTAssertEqual(StrainScorer.trimpToStrain(100, denominator: 7201), 28.87, accuracy: 1e-9)
        XCTAssertEqual(StrainScorer.trimpToStrain(7200, denominator: 7201), 100.0, accuracy: 1e-9)
        XCTAssertEqual(StrainScorer.trimpToStrain(1, denominator: 2), 100.0, accuracy: 1e-9)
    }

    /// Just above the boundary the ceiling is a vanishing positive TRIMP, so any load saturates. Under
    /// the log map this was a finite 3.1e17; the square-root axis clamps it to its top instead.
    func testDenominatorJustAboveOneStaysFinite() {
        let s = StrainScorer.trimpToStrain(1, denominator: 1.0000000000000002)
        XCTAssertTrue(s.isFinite)
        XCTAssertEqual(s, 100.0, accuracy: 0)
    }

    /// Non-finite TRIMP propagates rather than being caught by the domain guard — the guard is about
    /// D, not TRIMP. Both platforms agree here only because Kotlin no longer rounds through Long
    /// (`roundToLong()` mapped +∞ to Long.MAX), so pin the non-finite propagation behavior.
    func testNonFiniteTrimpPropagates() {
        // +∞ load sits at the axis' ceiling; NaN still propagates so corrupt input stays visible.
        let inf = StrainScorer.trimpToStrain(.infinity, denominator: 7201)
        XCTAssertEqual(inf, 100.0, accuracy: 0)
        XCTAssertTrue(StrainScorer.trimpToStrain(.nan, denominator: 7201).isNaN)
    }
}
