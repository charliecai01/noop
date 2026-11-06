package com.noop.ui

import com.noop.data.EffortCurve
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pins the pure display-only helpers behind the Coupled view (task #43) so they stay byte-identical to the
 * Swift [CoupledView] twin:
 *  - the OPTIMAL recovery->strain band mapping (green >= 67 -> 14-18, yellow 34-66 -> 10-14, red < 34 -> 4-10),
 *  - the strain band word thresholds (LIGHT/MODERATE/STRENUOUS/HIGH/ALL-OUT at 6/10/14/18 of 21),
 *  - the hours:minutes formatter.
 * These are display-only reads (never fed back into scoring), so the values here ARE the contract.
 */
class CoupledScreenTest {

    @Test fun optimalRange_greenDay_suggests14to18() {
        assertEquals("14 to 18", optimalStrainRangeText(90.0))
        assertEquals("14 to 18", optimalStrainRangeText(67.0)) // lower boundary of green is inclusive
    }

    @Test fun optimalRange_yellowDay_suggests10to14() {
        assertEquals("10 to 14", optimalStrainRangeText(66.9))
        assertEquals("10 to 14", optimalStrainRangeText(50.0))
        assertEquals("10 to 14", optimalStrainRangeText(34.0)) // lower boundary of yellow is inclusive
    }

    @Test fun optimalRange_redDay_suggests4to10() {
        assertEquals("4 to 10", optimalStrainRangeText(33.9))
        assertEquals("4 to 10", optimalStrainRangeText(10.0))
        assertEquals("4 to 10", optimalStrainRangeText(0.0))
    }

    @Test fun optimalRange_noRecovery_isNoData() {
        assertNull(optimalStrainRange(null))
        assertEquals("No Data", optimalStrainRangeText(null))
    }

    @Test fun optimalRange_bands_matchTheStruct() {
        // WHOOP's 14-18 / 10-14 / 4-10 converted through load onto the 0-21 dial. Swift twin.
        assertEquals(OptimalStrainRange(12, 21), optimalStrainRange(80.0))
        assertEquals(OptimalStrainRange(5, 12), optimalStrainRange(40.0))
        assertEquals(OptimalStrainRange(1, 5), optimalStrainRange(5.0))
    }

    @Test fun strainBandWord_thresholds_matchTheGauge() {
        // WHOOP's 6/10/14/18 converted through load (EffortCurve), matching StrandDesign StrainGauge.bandEdges.
        assertEquals(0.0985, EffortCurve.effortFromWhoopStrain(6.0) / 100, 1e-4)
        assertEquals(0.2375, EffortCurve.effortFromWhoopStrain(10.0) / 100, 1e-4)
        assertEquals(0.5567, EffortCurve.effortFromWhoopStrain(14.0) / 100, 1e-4)
        assertEquals("LIGHT", strainBandWord(0.05))
        assertEquals("MODERATE", strainBandWord(0.15))
        assertEquals("STRENUOUS", strainBandWord(0.2765))   // an easy hour: was 51 → WHOOP 10.7, still STRENUOUS
        assertEquals("HIGH", strainBandWord(0.6996))        // a hard 4 h ride: was 71.8 → WHOOP 15.1, still HIGH
        assertEquals("ALL-OUT", strainBandWord(1.0))
        // Boundaries are exclusive-below.
        assertEquals("MODERATE", strainBandWord(0.0985))
        assertEquals("STRENUOUS", strainBandWord(0.2375))
        assertEquals("HIGH", strainBandWord(0.5567))
    }

    @Test fun hoursMinutes_formatsAsHandM() {
        assertEquals("6h 42m", hoursMinutes(402.0))
        assertEquals("7h 50m", hoursMinutes(470.0))
        assertEquals("0h 5m", hoursMinutes(5.0))
        assertEquals("8h 0m", hoursMinutes(480.0))
    }

    /**
     * The tube fill beside the OPTIMAL range (#43). Twin of Swift CoupledView.optimalUpperFraction, which
     * is `max(0, min(1, Double(band.upperBound) / 21))` - so these expectations are the Swift expression
     * evaluated on the approved bands, not values read back off the Kotlin.
     */
    @Test
    fun optimalUpperFractionMatchesTheSwiftTwin() {
        assertEquals(21.0 / 21.0, optimalUpperFraction(90.0), 1e-12)
        assertEquals(21.0 / 21.0, optimalUpperFraction(67.0), 1e-12)   // green, inclusive lower edge
        assertEquals(12.0 / 21.0, optimalUpperFraction(66.9), 1e-12)
        assertEquals(12.0 / 21.0, optimalUpperFraction(34.0), 1e-12)   // yellow, inclusive lower edge
        assertEquals(5.0 / 21.0, optimalUpperFraction(33.9), 1e-12)
        assertEquals(5.0 / 21.0, optimalUpperFraction(0.0), 1e-12)
    }

    @Test
    fun optimalUpperFractionIsEmptyWhenRecoveryIsUnknown() {
        // A calibrating / unscored day prints the no-data token, so the tube must be EMPTY rather than
        // fabricate a band the text alongside it is refusing to name.
        assertEquals(0.0, optimalUpperFraction(null), 1e-12)
    }

    @Test
    fun optimalUpperFractionNeverLeavesTheAxis() {
        // Every approved band is inside 0..21, so the clamp is defensive - but it is the reason a future
        // band change cannot push the tube past full.
        for (r in listOf(null, -50.0, 0.0, 33.9, 34.0, 66.9, 67.0, 100.0, 1000.0)) {
            val f = optimalUpperFraction(r)
            assertTrue("fraction out of range for recovery=$r: $f", f in 0.0..1.0)
        }
    }

    /**
     * The GlowRing target arc range. Sibling of [optimalUpperFraction], returning the full band as a
     * 0..1 range instead of just the upper bound.
     */
    @Test
    fun optimalFractionRange_greenDay_returns12to21() {
        val range = optimalFractionRange(80.0)
        assertEquals(12.0f / 21.0f, range!!.start, 1e-6f)
        assertEquals(21.0f / 21.0f, range.endInclusive, 1e-6f)
    }

    @Test
    fun optimalFractionRange_yellowDay_returns5to12() {
        val range = optimalFractionRange(50.0)
        assertEquals(5.0f / 21.0f, range!!.start, 1e-6f)
        assertEquals(12.0f / 21.0f, range.endInclusive, 1e-6f)
    }

    @Test
    fun optimalFractionRange_redDay_returns1to5() {
        val range = optimalFractionRange(20.0)
        assertEquals(1.0f / 21.0f, range!!.start, 1e-6f)
        assertEquals(5.0f / 21.0f, range.endInclusive, 1e-6f)
    }

    @Test
    fun optimalFractionRange_noRecovery_returnsNull() {
        assertNull(optimalFractionRange(null))
    }

    @Test
    fun optimalFractionRange_neverLeavesAxis() {
        for (r in listOf(null, -50.0, 0.0, 33.9, 34.0, 66.9, 67.0, 100.0, 1000.0)) {
            val range = optimalFractionRange(r)
            if (range != null) {
                assertTrue("start out of range for recovery=$r: ${range.start}", range.start in 0f..1f)
                assertTrue("end out of range for recovery=$r: ${range.endInclusive}", range.endInclusive in 0f..1f)
                assertTrue("start > end for recovery=$r", range.start <= range.endInclusive)
            }
        }
    }
}
