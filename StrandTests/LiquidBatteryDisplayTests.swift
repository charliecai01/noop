import XCTest
import StrandDesign
@testable import Strand

/// Pins the Liquid Today strap-battery ring's truth table (A3/B2,
/// docs/bugs/2026-07-15-strap-battery-backfill-observability.md).
///
/// The three inputs are independent live signals with independent sources, so the interesting cases are
/// the MIXED ones — charge % without a charging bit, a charging bit without a charge %, and a % that
/// outlived its link. Each of the three regressions below shipped, and each was visible on a wearer's
/// phone during a real strap incident. `resolve` is pure, so all of it pins with no strap and no BLE.
final class LiquidBatteryDisplayTests: XCTestCase {

    private typealias Display = LiquidTodayView.StrapBatteryDisplay

    // MARK: - A3: "charging" must not be hostage to "charge %"

    /// THE regression. `charging` rides the strap's BATTERY_LEVEL event (~every 8 min); the % rides a
    /// different characteristic entirely. So the strap can tell us it is charging long before (or without
    /// ever) telling us a number. The old view nested the bolt inside `if let pct`, making this state
    /// unrenderable — it drew `bolt.slash` at a wearer who was sitting on the charger.
    func testChargingIsReportedEvenWithNoChargeReadingYet() {
        let d = Display.resolve(activeIsWhoop: true, connected: true, batteryPct: nil, charging: true, ringPct: nil, ringCharging: false, sourcePct: nil)
        XCTAssertEqual(d, .pending(charging: true),
                       "a known charging state must survive a missing % — it is the wearer's live question")
    }

    /// The same state without the charging bit is "no reading yet", NOT "not charging" and NOT "dead".
    /// `.pending(charging: false)` and `.offline` must stay distinguishable so the view can render one as
    /// a pending ellipsis and the other as a crossed-out bolt.
    func testConnectedWithNoReadingIsPendingNotOffline() {
        let d = Display.resolve(activeIsWhoop: true, connected: true, batteryPct: nil, charging: nil, ringPct: nil, ringCharging: false, sourcePct: nil)
        XCTAssertEqual(d, .pending(charging: false))
        XCTAssertNotEqual(d, .offline, "connected-but-silent is not the same claim as no link")
    }

    // MARK: - B2: a reading must not outlive its link

    /// `LiveState.batteryPct` is never cleared — `clearBiometrics()` deliberately leaves it set (that is
    /// what makes a nil % proof the 0x2A19 read never landed). So the LAST reading survives disconnect
    /// forever, and a view keying off `batteryPct` alone shows a dead strap's stale charge as if live.
    /// During the incident that rendered a 21 h old 11% identically to a fresh one.
    func testStaleChargeIsNotShownOnceTheLinkIsGone() {
        let d = Display.resolve(activeIsWhoop: true, connected: false, batteryPct: 11, charging: false, ringPct: nil, ringCharging: false, sourcePct: nil)
        XCTAssertEqual(d, .offline, "a % with no link behind it must not render as a live reading")
    }

    /// Disconnect must also drop a charging bit — nothing about the old link is still true.
    func testStaleChargingFlagIsNotShownOnceTheLinkIsGone() {
        XCTAssertEqual(Display.resolve(activeIsWhoop: true, connected: false, batteryPct: nil, charging: true, ringPct: nil, ringCharging: false, sourcePct: nil), .offline)
    }

    // MARK: - The normal path still reads normally

    func testConnectedReadingCarriesPctAndChargingThrough() {
        XCTAssertEqual(Display.resolve(activeIsWhoop: true, connected: true, batteryPct: 87.4, charging: true, ringPct: nil, ringCharging: false, sourcePct: nil),
                       .charge(pct: 87.4, charging: true, isRing: false))
        XCTAssertEqual(Display.resolve(activeIsWhoop: true, connected: true, batteryPct: 87.4, charging: false, ringPct: nil, ringCharging: false, sourcePct: nil),
                       .charge(pct: 87.4, charging: false, isRing: false))
    }

    /// `charging` is `Bool?` — nil means "the strap hasn't said" (no BATTERY_LEVEL event this session),
    /// which must read as not-charging, never as charging. Same `== true` posture as the rest of the app.
    func testUnknownChargingReadsAsNotCharging() {
        XCTAssertEqual(Display.resolve(activeIsWhoop: true, connected: true, batteryPct: 50, charging: nil, ringPct: nil, ringCharging: false, sourcePct: nil),
                       .charge(pct: 50, charging: false, isRing: false))
    }

    // MARK: - #2208 whose charge is this

    /// The reported bug. `connected` goes true the moment ANY source streams, and the strap's percentage
    /// is never cleared, so under an active ring both halves of the old gate passed and Today drew the
    /// strap's charge. A non-WHOOP active device must show no charge rather than someone else's number.
    func testRingActiveShowsNoChargeEvenWithAStalestrapCharge() {
        XCTAssertEqual(Display.resolve(activeIsWhoop: false, connected: true, batteryPct: 72.4, charging: false, ringPct: nil, ringCharging: false, sourcePct: nil),
                       .notActiveDevice)
    }

    /// Charging is the strap's BATTERY_LEVEL event, so it is strap-only for the same reason. A ring must
    /// not inherit the strap's charger state either.
    func testRingActiveShowsNoChargeWhileTheStrapCharges() {
        XCTAssertEqual(Display.resolve(activeIsWhoop: false, connected: true, batteryPct: 88, charging: true, ringPct: nil, ringCharging: false, sourcePct: nil),
                       .notActiveDevice)
    }

    /// A ring active with no strap charge ever recorded is the same answer, reached the other way.
    func testRingActiveWithNoStrapChargeShowsNoChargeEither() {
        XCTAssertEqual(Display.resolve(activeIsWhoop: false, connected: true, batteryPct: nil, charging: nil, ringPct: nil, ringCharging: false, sourcePct: nil),
                       .notActiveDevice)
    }

    /// The distinction @pipiche38 asked for on #2216. A strap that IS active and disconnected is offline,
    /// a real claim worth making. A strap that is not the active device is a different answer entirely,
    /// and collapsing the two told a wearer with a streaming ring that their strap was not connected.
    func testNotActiveIsNotTheSameAnswerAsOffline() {
        XCTAssertNotEqual(Display.resolve(activeIsWhoop: false, connected: true, batteryPct: 72, charging: false, ringPct: nil, ringCharging: false, sourcePct: nil),
                          Display.resolve(activeIsWhoop: true, connected: false, batteryPct: 72, charging: false, ringPct: nil, ringCharging: false, sourcePct: nil))
        XCTAssertEqual(Display.resolve(activeIsWhoop: true, connected: false, batteryPct: 72, charging: false, ringPct: nil, ringCharging: false, sourcePct: nil),
                       .offline)
    }

    /// The WHOOP path is unchanged: this fix must not blank a strap that IS the active device.
    func testWhoopActiveStillReportsItsCharge() {
        XCTAssertEqual(Display.resolve(activeIsWhoop: true, connected: true, batteryPct: 61, charging: false, ringPct: nil, ringCharging: false, sourcePct: nil),
                       .charge(pct: 61, charging: false, isRing: false))
    }

    // MARK: - The control stays on the header

    /// The circle is the way into Devices from Today, so no answer may remove it: under a non-WHOOP
    /// active device it draws the Devices glyph. Removing a strap made this control vanish when it read
    /// `.notActiveDevice` as "not drawn". It must not read as `.offline` either, per #2216 above.
    func testNotActiveDeviceDrawsTheDevicesGlyphNotOffline() {
        XCTAssertEqual(Display.notActiveDevice.indicatorState, .noBattery)
        XCTAssertNotEqual(Display.notActiveDevice.indicatorState, Display.offline.indicatorState)
    }

    // MARK: - A generic strap's own charge

    /// A Wahoo (or any generic HR strap, machine or band) that is the active device draws its OWN 0x2A19
    /// charge, never the WHOOP's stale one and never a charger state it does not report.
    func testGenericStrapActiveDrawsItsOwnCharge() {
        XCTAssertEqual(Display.resolve(activeIsWhoop: false, connected: true, batteryPct: 42, charging: true,
                                       ringPct: nil, ringCharging: false, sourcePct: 80),
                       .charge(pct: 80, charging: false, isRing: false),
                       "the WHOOP's 42 % and its charger bit must not leak into the Wahoo's reading")
    }

    /// The source's charge is for the source alone: a WHOOP that is active ignores it.
    func testWhoopActiveIgnoresAGenericStrapsCharge() {
        XCTAssertEqual(Display.resolve(activeIsWhoop: true, connected: true, batteryPct: 42, charging: false,
                                       ringPct: nil, ringCharging: false, sourcePct: 80),
                       .charge(pct: 42, charging: false, isRing: false))
    }

    // MARK: - The ring's own charge

    /// The other half of #2208. Hiding the strap's number under a ring was right; hiding the RING's was
    /// only ever a limitation of the control, which could not say whose number it held. A ring that has
    /// reported its charge this link is the active device's own reading, and is drawn as the ring's.
    func testRingActiveDrawsTheRingsOwnCharge() {
        XCTAssertEqual(Display.resolve(activeIsWhoop: false, connected: true, batteryPct: 72.4, charging: true,
                                       ringPct: 93, ringCharging: false, sourcePct: nil),
                       .charge(pct: 93, charging: false, isRing: true),
                       "the strap's 72 % and its charger bit must not leak into the ring's reading")
    }

    /// The ring's charging evidence is its own charger state, never the strap's BATTERY_LEVEL bit.
    func testRingChargingComesFromTheRingsChargerState() {
        XCTAssertEqual(Display.resolve(activeIsWhoop: false, connected: true, batteryPct: nil, charging: nil,
                                       ringPct: 40, ringCharging: true, sourcePct: nil),
                       .charge(pct: 40, charging: true, isRing: true))
    }

    /// `LiveState.ouraBatteryPct` is cleared with the link (#2075), so under a non-WHOOP active device a
    /// nil ring charge means "no ring is live, or it has not reported yet" — and a generic HR strap or a
    /// machine, which never write it, show no charge either.
    func testRingActiveWithNoRingChargeYetShowsNoCharge() {
        XCTAssertEqual(Display.resolve(activeIsWhoop: false, connected: true, batteryPct: 72, charging: false,
                                       ringPct: nil, ringCharging: false, sourcePct: nil),
                       .notActiveDevice)
    }

    /// A ring charge that somehow sits beside an active STRAP is not the strap's reading and must not
    /// replace it: `activeIsWhoop` decides whose number is shown, the same way `batteryPercent` does.
    func testStrapActiveIgnoresARingCharge() {
        XCTAssertEqual(Display.resolve(activeIsWhoop: true, connected: true, batteryPct: 61, charging: false,
                                       ringPct: 93, ringCharging: true, sourcePct: nil),
                       .charge(pct: 61, charging: false, isRing: false))
    }
}
