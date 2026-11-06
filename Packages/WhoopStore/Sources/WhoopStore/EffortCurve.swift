import Foundation

// EffortCurve.swift — the 0–100 Effort axis, and the exact conversion from the axis it replaced.
//
// Effort is cardiovascular load (TRIMP, in zone-weighted minutes) mapped onto 0–100 by
//
//     effort = 100 × √(TRIMP / C),   clamped to 0…100,   C = (D − 1) / 6
//
// where D − 1 is the scoring method's DAILY ceiling (Edwards: 24 h in zone 5 = 7200), so C is four of
// those 24 hours: 1200 for Edwards. Under the square root a load ratio of 4 is a score ratio of 2, so
// the number keeps reading like a magnitude: an easy hour on the bike (TRIMP ≈ 90) is about 27, a hard
// four-hour ride (TRIMP ≈ 590) about 70, and only a day carrying more than four hours flat out tops out.
//
// The axis it replaced was 100 × ln(TRIMP + 1) / ln(D). That log map compressed the same two rides to 51
// and 72, a 6.4× load difference read as 1.4×. Both axes are monotone functions of the same TRIMP, so
// any value or threshold on the old axis has ONE exact counterpart here, `effortFromLegacy`, and back,
// `legacyFromEffort`. The inverse is exact below the new axis' ceiling; above it the clamp is the only
// information lost (a legacy value past ≈ 79.8 converts to 100).
//
// Lives in WhoopStore rather than StrandAnalytics because the v49 data migration needs it and the
// store cannot import analytics. Kotlin twin: `com.noop.data.EffortCurve`.
public enum EffortCurve {

    /// Top of the axis.
    public static let maxEffort: Double = 100.0

    /// The Edwards log-map denominator the legacy axis used: 24 h in zone 5 (7200) + 1.
    public static let legacyDenominator: Double = 7201.0

    /// Hours of the method's daily ceiling that make a full 100: four of the twenty-four.
    public static let ceilingFractionOfDay: Double = 1.0 / 6.0

    /// TRIMP that scores 100 for a method whose legacy log-map denominator is `denominator`
    /// (daily ceiling + 1). Edwards: 7200 / 6 = 1200.
    /// Kotlin twin: `EffortCurve.ceilingTrimp`.
    public static func ceilingTrimp(denominator: Double = legacyDenominator) -> Double {
        (denominator - 1.0) * ceilingFractionOfDay
    }

    /// Round to 2 dp, the precision every Effort is stored at on both platforms.
    /// Kotlin twin: `EffortCurve.round2`.
    static func round2(_ v: Double) -> Double { (v * 100).rounded() / 100 }

    /// TRIMP → Effort, rounded to 2 dp. TRIMP ≤ 0 and a ceiling ≤ 0 score 0; +∞ TRIMP clamps to 100;
    /// a NaN TRIMP stays NaN, as it did through the log map, so corrupt input is not hidden as a score.
    /// Kotlin twin: `EffortCurve.effortFromTrimp`.
    public static func effortFromTrimp(_ trimp: Double, ceiling: Double = ceilingTrimp()) -> Double {
        if trimp.isNaN { return .nan }
        guard trimp > 0, ceiling.isFinite, ceiling > 0 else { return 0 }
        return round2(min(maxEffort, maxEffort * (trimp / ceiling).squareRoot()))
    }

    /// Effort → the TRIMP it represents (the inverse of `effortFromTrimp`, unrounded). Used where a
    /// consumer is defined on LOAD rather than on a score: acute:chronic ratios, monotony.
    /// Kotlin twin: `EffortCurve.trimpFromEffort`.
    public static func trimpFromEffort(_ effort: Double, ceiling: Double = ceilingTrimp()) -> Double {
        guard effort.isFinite, effort > 0 else { return 0 }
        let f = min(effort, maxEffort) / maxEffort
        return ceiling * f * f
    }

    /// A value on the legacy log axis (`100 × ln(TRIMP+1) / ln(7201)`) → the same TRIMP on this axis.
    /// The conversion the v49 migration applies to stored rows, and the one every threshold that was
    /// tuned on the old axis passes through so it keeps meaning the same load.
    /// Kotlin twin: `EffortCurve.effortFromLegacy`.
    public static func effortFromLegacy(_ legacy: Double) -> Double {
        guard legacy.isFinite, legacy > 0 else { return 0 }
        let trimp = exp(min(legacy, maxEffort) / maxEffort * log(legacyDenominator)) - 1.0
        return effortFromTrimp(trimp)
    }

    /// This axis → the legacy log axis, for consumers whose constants were fitted on it (the recovery
    /// forecast, fitness age, the WHOOP-style 0–21 strain bands). Rounded to 2 dp.
    /// Kotlin twin: `EffortCurve.legacyFromEffort`.
    public static func legacyFromEffort(_ effort: Double) -> Double {
        let trimp = trimpFromEffort(effort)
        guard trimp > 0 else { return 0 }
        return round2(maxEffort * log(trimp + 1.0) / log(legacyDenominator))
    }

    /// WHOOP's 0–21 Day Strain → this axis. WHOOP's strain is logarithmic like the legacy axis was, so
    /// it is read as legacy × 100/21 and converted through the load it stands for.
    /// Kotlin twin: `EffortCurve.effortFromWhoopStrain`.
    public static func effortFromWhoopStrain(_ strain: Double) -> Double {
        effortFromLegacy(strain * (maxEffort / 21.0))
    }

    /// This axis → WHOOP's 0–21 Day Strain (the inverse of `effortFromWhoopStrain`), 2 dp.
    /// Kotlin twin: `EffortCurve.whoopStrainFromEffort`.
    public static func whoopStrainFromEffort(_ effort: Double) -> Double {
        round2(legacyFromEffort(effort) * (21.0 / maxEffort))
    }
}
