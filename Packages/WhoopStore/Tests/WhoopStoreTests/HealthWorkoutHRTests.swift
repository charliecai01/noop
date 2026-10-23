import XCTest
import WhoopProtocol
@testable import WhoopStore

final class HealthWorkoutHRTests: XCTestCase {
    /// The oracle the Kotlin `HealthWorkoutHrTest` pastes verbatim. Covers flooring a fractional start,
    /// rounding half away from zero, both inclusive bounds, a raw value that would round INTO range but
    /// starts outside it, and non-finite input.
    func testSampleRuleMatchesTheKotlinOracle() {
        let cases: [(Double, Double)] = [
            (1_792_000_000.0, 72.0), (1_792_000_000.999, 72.5), (1_792_000_001.4, 72.49),
            (1_792_000_002.0, 24.999), (1_792_000_003.0, 25.0), (1_792_000_004.0, 240.0),
            (1_792_000_005.0, 240.0001), (1_792_000_006.0, 239.5), (1_792_000_007.0, 24.6),
            (1_792_000_008.0, 180.5), (1_792_000_009.0, 0.0), (1_792_000_010.0, -5.0),
            (1_792_000_011.0, .nan), (1_792_000_012.0, .infinity), (1_792_000_013.0, 25.4),
        ]
        let out = cases.map { c in
            HealthWorkoutHR.sample(startEpochSeconds: c.0, bpm: c.1).map { "\($0.ts):\($0.bpm)" } ?? "nil"
        }.joined(separator: ",")
        XCTAssertEqual(out, "1792000000:72,1792000000:73,1792000001:72,nil,1792000003:25,1792000004:240,nil,"
                          + "1792000006:240,nil,1792000008:181,nil,nil,nil,nil,1792000013:25")
    }

    func testNonFiniteStartIsDropped() {
        XCTAssertNil(HealthWorkoutHR.sample(startEpochSeconds: .nan, bpm: 80))
    }

    /// Ranked by the day-owner resolver as an activity file (priority 3), never as the active device.
    func testRegistryDeviceIsAPairedActivityFileSourceForHROnly() {
        let d = HealthWorkoutHR.registryDevice(brand: "Apple Health workouts", now: 1_792_000_000)
        XCTAssertEqual(d.id, "apple-health-workouts")
        XCTAssertEqual(d.brand, "Apple Health workouts")
        XCTAssertEqual(d.sourceKind, .activityFile)
        XCTAssertEqual(d.capabilities, [.hr])
        XCTAssertEqual(d.status, .paired)
        XCTAssertEqual(d.addedAt, 1_792_000_000)
        XCTAssertEqual(d.lastSeenAt, 1_792_000_000)
        XCTAssertNil(d.peripheralId)
    }

    /// Re-reading a sync window must not duplicate or rewrite: the insert ignores a repeated (deviceId, ts),
    /// and the samples are visible to the resolver's presence probe under the source's own id.
    func testReinsertingTheSameWindowIsANoOpAndTheResolverProbeSeesIt() async throws {
        let store = try await WhoopStore.inMemory()
        let hr = [HRSample(ts: 1_792_000_000, bpm: 140), HRSample(ts: 1_792_000_001, bpm: 141)]
        let first = try await store.insert(Streams(hr: hr), deviceId: HealthWorkoutHR.deviceId)
        let second = try await store.insert(Streams(hr: hr), deviceId: HealthWorkoutHR.deviceId)
        XCTAssertEqual(first.hr, 2)
        XCTAssertEqual(second.hr, 0)
        let present = try await store.hasHrInWindow(deviceId: HealthWorkoutHR.deviceId,
                                                    from: 1_792_000_000, to: 1_792_000_001)
        XCTAssertTrue(present)
    }
}
