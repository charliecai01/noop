import XCTest
import GRDB
@testable import WhoopStore

/// The square-root Effort axis (`EffortCurve`) and the v49 migration onto it.
///
/// Expected literals are the stdout of `EffortCurve.swift` compiled standalone
/// (`swiftc -O EffortCurve.swift main.swift`). The Kotlin twin `EffortCurveTest` pins the same literals.
final class EffortCurveTests: XCTestCase {

    func testTrimpToEffort() {
        let cases: [(Double, Double)] = [(-5, 0), (0, 0), (1, 2.89), (50, 20.41), (75, 25), (92, 27.69),
                                         (100, 28.87), (300, 50), (590, 70.12), (675, 75), (1200, 100),
                                         (2000, 100), (7200, 100)]
        for (trimp, effort) in cases {
            XCTAssertEqual(EffortCurve.effortFromTrimp(trimp), effort, accuracy: 0, "TRIMP \(trimp)")
        }
        XCTAssertEqual(EffortCurve.effortFromTrimp(.infinity), 100)
        XCTAssertTrue(EffortCurve.effortFromTrimp(.nan).isNaN)
    }

    func testLegacyToEffort() {
        let cases: [(Double, Double)] = [(-5, 0), (0, 0), (0.01, 0.09), (7.8, 2.89), (25, 8.27), (30, 10.55),
                                         (40, 16.81), (44.27, 20.41), (51, 27.65), (51.96, 28.87), (60, 41.36),
                                         (65, 51.69), (71.8, 69.96), (75, 80.66), (79, 96.36), (80, 100),
                                         (100, 100), (150, 100)]
        for (legacy, effort) in cases {
            XCTAssertEqual(EffortCurve.effortFromLegacy(legacy), effort, accuracy: 0, "legacy \(legacy)")
        }
    }

    func testEffortToLegacy() {
        let cases: [(Double, Double)] = [(0, 0), (5, 15.61), (10, 28.88), (20, 43.82), (25, 48.76),
                                         (27.65, 51), (30, 52.82), (50, 64.25), (70, 71.81), (100, 79.83)]
        for (effort, legacy) in cases {
            XCTAssertEqual(EffortCurve.legacyFromEffort(effort), legacy, accuracy: 0, "effort \(effort)")
        }
    }

    func testWhoopStrainBothWays() {
        let toEffort: [(Double, Double)] = [(0, 0), (1, 2.09), (4, 6.08), (6, 9.85), (10, 23.75), (14, 55.67),
                                            (17.5, 100), (21, 100)]
        for (whoop, effort) in toEffort {
            XCTAssertEqual(EffortCurve.effortFromWhoopStrain(whoop), effort, accuracy: 0, "WHOOP \(whoop)")
        }
        let toWhoop: [(Double, Double)] = [(0, 0), (10, 6.06), (25, 10.24), (50, 13.49), (70, 15.08), (100, 16.76)]
        for (effort, whoop) in toWhoop {
            XCTAssertEqual(EffortCurve.whoopStrainFromEffort(effort), whoop, accuracy: 0, "effort \(effort)")
        }
    }

    func testTrimpFromEffortInvertsTheAxis() {
        XCTAssertEqual(EffortCurve.trimpFromEffort(25), 75, accuracy: 1e-9)
        XCTAssertEqual(EffortCurve.trimpFromEffort(50), 300, accuracy: 1e-9)
        XCTAssertEqual(EffortCurve.trimpFromEffort(100), 1200, accuracy: 1e-9)
        XCTAssertEqual(EffortCurve.trimpFromEffort(150), 1200, accuracy: 1e-9)
        XCTAssertEqual(EffortCurve.trimpFromEffort(-1), 0)
    }

    /// The point of the axis: a load ratio of 4 reads as a score ratio of 2.
    func testFourTimesTheLoadIsTwiceTheScore() {
        XCTAssertEqual(EffortCurve.effortFromTrimp(300) / EffortCurve.effortFromTrimp(75), 2, accuracy: 1e-9)
    }

    func testMonotoneAndBounded() {
        var previous = -1.0
        for i in 0...10_000 {
            let e = EffortCurve.effortFromLegacy(Double(i) / 100)
            XCTAssertGreaterThanOrEqual(e, previous)
            XCTAssertTrue((0...100).contains(e))
            previous = e
        }
    }

    // MARK: - v49 migration

    func testV49ConvertsStoredEffortThroughItsLoad() async throws {
        let dbQueue = try DatabaseQueue()
        try WhoopStore.makeMigrator().migrate(dbQueue, upTo: "v48-ecg-waveform")
        try await dbQueue.write { db in
            try db.execute(sql: "INSERT INTO dailyMetric (deviceId, day, strain) VALUES ('d', '2026-11-05', 51)")
            try db.execute(sql: "INSERT INTO dailyMetric (deviceId, day, strain) VALUES ('d', '2026-10-31', 71.8)")
            try db.execute(sql: "INSERT INTO dailyMetric (deviceId, day, strain) VALUES ('d', '2026-10-30', NULL)")
            try db.execute(sql: """
                INSERT INTO workout (deviceId, startTs, endTs, sport, source, strain)
                VALUES ('d', 100, 200, 'Cycling', 'apple-health', 51)
                """)
            try db.execute(sql: "INSERT INTO metricSeries (deviceId, day, key, value) VALUES ('w', '2026-01-01', 'strain', 66.67)")
            try db.execute(sql: "INSERT INTO metricSeries (deviceId, day, key, value) VALUES ('w', '2026-01-01', 'recovery', 66.67)")
        }
        try WhoopStore.makeMigrator().migrate(dbQueue)
        try await dbQueue.read { db in
            XCTAssertEqual(try Double.fetchOne(db, sql: "SELECT strain FROM dailyMetric WHERE day = '2026-11-05'"), 27.65)
            XCTAssertEqual(try Double.fetchOne(db, sql: "SELECT strain FROM dailyMetric WHERE day = '2026-10-31'"), 69.96)
            XCTAssertNil(try Double.fetchOne(db, sql: "SELECT strain FROM dailyMetric WHERE day = '2026-10-30'"))
            XCTAssertEqual(try Double.fetchOne(db, sql: "SELECT strain FROM workout WHERE startTs = 100"), 27.65)
            XCTAssertEqual(try Double.fetchOne(db, sql: "SELECT value FROM metricSeries WHERE key = 'strain'"), 55.68)
            XCTAssertEqual(try Double.fetchOne(db, sql: "SELECT value FROM metricSeries WHERE key = 'recovery'"), 66.67,
                           "only the strain series is on the Effort axis")
        }
    }
}
