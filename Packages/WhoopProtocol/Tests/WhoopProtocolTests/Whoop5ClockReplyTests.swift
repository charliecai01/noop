import XCTest
@testable import WhoopProtocol

/// `Whoop5ClockReply` over real GET_CLOCK replies captured from one WHOOP 5.0/MG (firmware 50.41.1.0).
/// Each expected value equals the phone's wall clock when the request went out. The same frames and
/// answers are pinned by the Kotlin twin's `Whoop5ClockReplyTest`.
final class Whoop5ClockReplyTests: XCTestCase {
    private func bytes(_ hex: String) -> [UInt8] {
        stride(from: 0, to: hex.count, by: 2).map {
            let a = hex.index(hex.startIndex, offsetBy: $0)
            return UInt8(hex[a..<hex.index(a, offsetBy: 2)], radix: 16)!
        }
    }

    /// Three captured replies, an evening and two mornings apart: the decode tracks a moving clock.
    func testCapturedRepliesDecodeToTheWallClockTheyWereSentAt() {
        XCTAssertEqual(Whoop5ClockReply.deviceUnix(frame: bytes(
            "aa011400010021b124b80b0301cbcbea6a000000000000006a13afe7")), 1_793_772_491)   // 2026-11-03 22:08:11 PST
        XCTAssertEqual(Whoop5ClockReply.deviceUnix(frame: bytes(
            "aa011400010021b124920b03016f4eeb6a000000000000002e4f965e")), 1_793_805_935)   // 2026-11-04 07:25:35 PST
        XCTAssertEqual(Whoop5ClockReply.deviceUnix(frame: bytes(
            "aa011400010021b124280b0301825beb6a000000000000005ec79f96")), 1_793_809_282)   // 2026-11-04 08:21:22 PST
    }

    /// A SET_CLOCK reply carries no clock: its command byte is 10, so it must not decode.
    func testSetClockReplyIsNotAClockReading() {
        XCTAssertNil(Whoop5ClockReply.deviceUnix(frame: bytes(
            "aa010c000100271124b70a020100000088d52b9e")))
    }

    /// A non-SUCCESS result, a short frame, or a non-response type each read as no reading.
    func testAnyOtherShapeReadsAsNoReading() {
        var failed = bytes("aa011400010021b124280b0301825beb6a000000000000005ec79f96")
        failed[12] = 0
        XCTAssertNil(Whoop5ClockReply.deviceUnix(frame: failed))
        var notResponse = bytes("aa011400010021b124280b0301825beb6a000000000000005ec79f96")
        notResponse[8] = 35
        XCTAssertNil(Whoop5ClockReply.deviceUnix(frame: notResponse))
        XCTAssertNil(Whoop5ClockReply.deviceUnix(frame: Array(bytes(
            "aa011400010021b124280b0301825beb6a000000000000005ec79f96").prefix(20))))
        XCTAssertNil(Whoop5ClockReply.deviceUnix(frame: []))
    }
}
