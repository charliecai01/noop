package com.noop.data

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.roundToLong
import kotlin.math.sqrt

/**
 * The 0–100 Effort axis, and the exact conversion from the axis it replaced.
 *
 * Effort is cardiovascular load (TRIMP, in zone-weighted minutes) mapped onto 0–100 by
 *
 *     effort = 100 × √(TRIMP / C),   clamped to 0…100,   C = (D − 1) / 6
 *
 * where D − 1 is the scoring method's DAILY ceiling (Edwards: 24 h in zone 5 = 7200), so C is four of
 * those 24 hours: 1200 for Edwards. A load ratio of 4 is a score ratio of 2: an easy hour on the bike
 * (TRIMP ≈ 90) is about 27, a hard four-hour ride (TRIMP ≈ 590) about 70.
 *
 * The axis it replaced was 100 × ln(TRIMP + 1) / ln(D). Both are monotone in the same TRIMP, so any value
 * or threshold on the old axis has one exact counterpart here ([effortFromLegacy]) and back
 * ([legacyFromEffort]). Above the new ceiling the clamp is the only information lost.
 *
 * In `data` rather than `analytics` because Room MIGRATION_42_43 needs it. Swift twin: `EffortCurve`
 * (WhoopStore); `EffortCurveTest` pins the same literals as `EffortCurveTests`.
 */
object EffortCurve {

    const val maxEffort: Double = 100.0

    /** The Edwards log-map denominator the legacy axis used: 24 h in zone 5 (7200) + 1. */
    const val legacyDenominator: Double = 7201.0

    /** Hours of the method's daily ceiling that make a full 100: four of the twenty-four. */
    const val ceilingFractionOfDay: Double = 1.0 / 6.0

    /** TRIMP that scores 100 for a method whose legacy log-map denominator is [denominator]. Swift twin: `EffortCurve.ceilingTrimp`. */
    fun ceilingTrimp(denominator: Double = legacyDenominator): Double =
        (denominator - 1.0) * ceilingFractionOfDay

    /** Swift twin: `EffortCurve.round2`. */
    private fun round2(v: Double): Double = (v * 100).roundToLong().toDouble() / 100.0

    /**
     * TRIMP → Effort, rounded to 2 dp. TRIMP ≤ 0 and a ceiling ≤ 0 score 0; +∞ clamps to 100; NaN stays
     * NaN, as it did through the log map.
     * Swift twin: `EffortCurve.effortFromTrimp`.
     */
    fun effortFromTrimp(trimp: Double, ceiling: Double = ceilingTrimp()): Double {
        if (trimp.isNaN()) return Double.NaN
        if (!(trimp > 0) || !ceiling.isFinite() || !(ceiling > 0)) return 0.0
        val value = min(maxEffort, maxEffort * sqrt(trimp / ceiling))
        return round2(value)
    }

    /** Effort → the TRIMP it represents (unrounded). For consumers defined on load, not on a score. Swift twin: `EffortCurve.trimpFromEffort`. */
    fun trimpFromEffort(effort: Double, ceiling: Double = ceilingTrimp()): Double {
        if (!effort.isFinite() || !(effort > 0)) return 0.0
        val f = min(effort, maxEffort) / maxEffort
        return ceiling * f * f
    }

    /** A value on the legacy log axis → the same TRIMP on this axis. What MIGRATION_42_43 applies. Swift twin: `EffortCurve.effortFromLegacy`. */
    fun effortFromLegacy(legacy: Double): Double {
        if (!legacy.isFinite() || !(legacy > 0)) return 0.0
        val trimp = exp(min(legacy, maxEffort) / maxEffort * ln(legacyDenominator)) - 1.0
        return effortFromTrimp(trimp)
    }

    /** This axis → the legacy log axis, for consumers whose constants were fitted on it. 2 dp. Swift twin: `EffortCurve.legacyFromEffort`. */
    fun legacyFromEffort(effort: Double): Double {
        val trimp = trimpFromEffort(effort)
        if (!(trimp > 0)) return 0.0
        return round2(maxEffort * ln(trimp + 1.0) / ln(legacyDenominator))
    }

    /** WHOOP's 0–21 Day Strain → this axis, read as legacy × 100/21 and converted through its load. Swift twin: `EffortCurve.effortFromWhoopStrain`. */
    fun effortFromWhoopStrain(strain: Double): Double = effortFromLegacy(strain * (maxEffort / 21.0))

    /** This axis → WHOOP's 0–21 Day Strain, 2 dp. Swift twin: `EffortCurve.whoopStrainFromEffort`. */
    fun whoopStrainFromEffort(effort: Double): Double =
        round2(legacyFromEffort(effort) * (21.0 / maxEffort))
}
