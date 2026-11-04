import XCTest
@testable import Strand

/// A generic HR strap's (a Wahoo's) own charge lives in `LiveState.sourceBatteryPct`, never in the
/// WHOOP's `batteryPct`, and readouts that name the ACTIVE device show it through
/// `activeOtherBatteryPct` and `LiveConsoleReadout.batteryPercent`.
@MainActor
final class SourceBatteryPctTests: XCTestCase {

    func testWahooActiveShowsTheWahoosChargeNotTheWhoops() {
        let live = LiveState()
        live.batteryPct = 42          // the WHOOP's last reading, deliberately kept across a switch
        live.sourceBatteryPct = 80    // the Wahoo's own 0x2A19
        XCTAssertEqual(live.activeOtherBatteryPct, 80)
        XCTAssertEqual(LiveConsoleReadout.batteryPercent(activeIsWhoop: false, whoopPct: live.batteryPct,
                                                         ringPct: live.activeOtherBatteryPct), 80)
    }

    func testWhoopActiveIgnoresTheWahoosCharge() {
        let live = LiveState()
        live.batteryPct = 42
        live.sourceBatteryPct = 80
        XCTAssertEqual(LiveConsoleReadout.batteryPercent(activeIsWhoop: true, whoopPct: live.batteryPct,
                                                         ringPct: live.activeOtherBatteryPct), 42)
    }

    func testRingChargeWinsWhenARingIsTheLiveSource() {
        let live = LiveState()
        live.ouraBatteryPct = 93
        XCTAssertEqual(live.activeOtherBatteryPct, 93)
    }

    func testNoOtherSourceReportedMeansNoCharge() {
        let live = LiveState()
        live.batteryPct = 42
        XCTAssertNil(live.activeOtherBatteryPct)
        XCTAssertNil(LiveConsoleReadout.batteryPercent(activeIsWhoop: false, whoopPct: live.batteryPct,
                                                       ringPct: live.activeOtherBatteryPct))
    }
}
