package com.noop.ble

import com.noop.data.PairedDeviceRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Which device a live link's samples belong to (#1881) — the ORACLE for the Swift twin
 * `SourceIdentityTests`, asserting the same inputs and the same answers on both platforms.
 *
 * The reported failure is the first case here: with an Oura ring active and a WHOOP merely paired, a
 * WHOOP connection filed 770 `stepSample` and 770 `gravitySample` rows under the ring's id. A ring has
 * no pedometer and no accelerometer, so 100% of those rows were misattributed — and because `hrSample`
 * is written by both paths under one id, contamination there cannot be separated after the fact.
 */
class SourceIdentityTest {

    private fun row(id: String, brand: String, peripheralId: String?) = PairedDeviceRow(
        id = id, brand = brand, model = "m", nickname = null, peripheralId = peripheralId,
        sourceKind = "liveBLE", capabilities = "hr", status = "paired", addedAt = 0L, lastSeenAt = 0L,
    )

    private val ring = row("oura-abc", "Oura", "AA:BB:CC:DD:EE:01")
    private val strap = row("whoop-123", "WHOOP", "AA:BB:CC:DD:EE:02")

    /** The reported bug: the ring is active, the STRAP connected, so the strap's id must win. */
    @Test
    fun `a strap connecting while a ring is active is attributed to the strap`() {
        assertEquals("whoop-123",
            SourceIdentity.resolve("AA:BB:CC:DD:EE:02", listOf(ring, strap), currentId = "oura-abc"))
    }

    /**
     * The legacy single-WHOOP path, and the reason this returns null rather than guessing: the seeded row
     * has not adopted an address yet. The coordinator adopts one on this same connect, so the NEXT connect
     * resolves — and until then the id stays exactly what it is today.
     */
    @Test
    fun `an unadopted row leaves the id alone`() {
        val legacy = row(WhoopBleClient.DEFAULT_DEVICE_ID, "WHOOP", null)
        assertNull(SourceIdentity.resolve("AA:BB:CC:DD:EE:02", listOf(legacy),
            currentId = WhoopBleClient.DEFAULT_DEVICE_ID))
    }

    /** No row carries this address — an unknown strap must never claim an existing device's id. */
    @Test
    fun `an unknown address leaves the id alone`() {
        assertNull(SourceIdentity.resolve("FF:FF:FF:FF:FF:FF", listOf(ring, strap), currentId = "oura-abc"))
    }

    /**
     * The inverse of the bug. If a non-WHOOP row somehow matches, re-pointing the WHOOP path's id at it
     * would file strap samples under a ring — which is exactly what #1881 reports, arrived at from the
     * other direction.
     */
    @Test
    fun `a non-WHOOP match never claims the strap's samples`() {
        assertNull(SourceIdentity.resolve("AA:BB:CC:DD:EE:01", listOf(ring, strap), currentId = "whoop-123"))
    }

    /** Already correct — the common path — must not write. */
    @Test
    fun `an id already correct resolves to nothing`() {
        assertNull(SourceIdentity.resolve("AA:BB:CC:DD:EE:02", listOf(ring, strap), currentId = "whoop-123"))
    }

    /** Neither OS guarantees the case of a UUID string or a MAC, and old rows may carry either. */
    @Test
    fun `address matching is case-insensitive`() {
        assertEquals("whoop-123",
            SourceIdentity.resolve("aa:bb:cc:dd:ee:02", listOf(ring, strap), currentId = "oura-abc"))
    }

    /** A blank address is a missing address, not a wildcard that matches a blank stored value. */
    @Test
    fun `a blank address leaves the id alone`() {
        val blank = row("whoop-blank", "WHOOP", "")
        assertNull(SourceIdentity.resolve("", listOf(blank), currentId = "oura-abc"))
        assertNull(SourceIdentity.resolve(null, listOf(blank), currentId = "oura-abc"))
        // Whitespace is blank on both platforms — the Swift twin was aligned to this.
        val spaces = row("whoop-spaces", "WHOOP", "   ")
        assertNull(SourceIdentity.resolve("   ", listOf(spaces), currentId = "oura-abc"))
    }

    /** The legacy id is a WHOOP by id even though its brand was never guaranteed. */
    @Test
    fun `the legacy row counts as a WHOOP`() {
        val legacy = row(WhoopBleClient.DEFAULT_DEVICE_ID, "", "AA:BB:CC:DD:EE:03")
        assertEquals(WhoopBleClient.DEFAULT_DEVICE_ID,
            SourceIdentity.resolve("AA:BB:CC:DD:EE:03", listOf(legacy), currentId = "oura-abc"))
    }

    /** A WHOOP paired after a Wahoo still lists first; each group keeps its registry order. Same inputs and
     *  answer as the Swift twin's `testWhoopFirstPutsEveryWhoopAheadAndKeepsOrderWithinGroups`. */
    @Test
    fun `whoopFirst puts every WHOOP ahead and keeps order within groups`() {
        val wahoo = row("strap-1", "Wahoo", null)
        val whoopA = row("whoop-a", "WHOOP", null)
        val polar = row("strap-2", "Polar", null)
        val legacy = row(WhoopBleClient.DEFAULT_DEVICE_ID, "", null)
        assertEquals(listOf("whoop-a", WhoopBleClient.DEFAULT_DEVICE_ID, "strap-1", "strap-2"),
            SourceIdentity.whoopFirst(listOf(wahoo, whoopA, polar, legacy)).map { it.id })
        assertEquals(emptyList<String>(), SourceIdentity.whoopFirst(emptyList()).map { it.id })
    }

    /** An import paired before the Wahoo must not sit between the WHOOP and the Wahoo. Same shape as the
     *  Swift twin's `testBluetoothDevicesComeBeforeHealthKitAndImports` (Android has no Apple Watch row). */
    @Test
    fun `bluetooth devices come before imports`() {
        val csv = row("whoop-csv", "Import", null).copy(sourceKind = "fileImport")
        val activity = row("activity-file", "Import", null).copy(sourceKind = "activityFile")
        val wahoo = row("strap-1", "Wahoo", null)
        val whoop = row("whoop-a", "WHOOP", null)
        assertEquals(listOf("whoop-a", "strap-1", "whoop-csv", "activity-file"),
            SourceIdentity.whoopFirst(listOf(csv, activity, wahoo, whoop)).map { it.id })
    }
}
