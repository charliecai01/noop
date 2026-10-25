import Foundation
import WhoopProtocol

/// Heart rate an external workout carried into the platform health store: a bike computer's ride synced
/// to Apple Health by its companion app, an Apple Watch session, a Health Connect exercise session.
///
/// Persisted as its own import source so the day-owner resolver can pick it on a day no strap covers,
/// the same way an imported activity file does (#137): without stored samples a ride scored its own
/// Effort but left the DAY's Effort dark. Registered as an `.activityFile` device, so it ranks at
/// priority 3, below whole-day imports, and a strap or full-day import with HR for the day always wins.
///
/// Only the sample → row rule and the registry row live here, so both are testable without HealthKit
/// or Health Connect. Kotlin twin: `com.noop.data.HealthWorkoutHr`.
public enum HealthWorkoutHR {
    /// The `hrSample.deviceId` and registry id. Shared with Android, which stores Health Connect workout
    /// HR under the same id, just as both platforms store the health-platform import under "apple-health".
    public static let deviceId = "apple-health-workouts"

    /// Plausible bpm bounds, inclusive, applied to the RAW value before rounding.
    public static let minBpm: Double = 25
    public static let maxBpm: Double = 240

    /// The stored row for one health-store sample, or nil when its bpm is not plausible.
    ///
    /// `ts` is the whole unix second the sample started (floored) and `bpm` is rounded half away from
    /// zero. Two samples landing in one second keep the first, because the store ignores a repeated
    /// `(deviceId, ts)`.
    public static func sample(startEpochSeconds: Double, bpm: Double) -> HRSample? {
        guard startEpochSeconds.isFinite, bpm.isFinite, bpm >= minBpm, bpm <= maxBpm else { return nil }
        return HRSample(ts: Int(startEpochSeconds.rounded(.down)), bpm: Int(bpm.rounded()))
    }

    /// The sources whose stored HR may fill a day owner's gaps for Effort, in precedence order: every
    /// generic BLE heart-rate strap (a non-WHOOP `.liveBLE` row, such as a Wahoo or Polar chest strap)
    /// oldest-added first, then this import source. A strap NOOP recorded at 1 Hz outranks the sparser
    /// copy a companion app wrote into the health store. Any status counts: a removed strap's recordings
    /// were kept, and they are still the rider's heart rate. `owner` is left out, so a day is never filled
    /// from itself. Ties on `addedAt` break on id, so both platforms agree. Kotlin twin:
    /// `HealthWorkoutHr.supplementIds`.
    public static func supplementIds(rows: [PairedDevice], excluding owner: String?) -> [String] {
        let straps = rows
            .filter { $0.sourceKind == .liveBLE && !SourceIdentity.isWhoop($0) }
            .sorted { ($0.addedAt, $0.id) < ($1.addedAt, $1.id) }
            .map(\.id)
        return (straps + [deviceId]).filter { $0 != owner }
    }

    /// The registry row that makes the source visible to the day-owner resolver. `paired`, never
    /// `active`, so it cannot displace the live strap; `.hr` is the only metric it can provide.
    public static func registryDevice(brand: String, now: Int) -> PairedDevice {
        PairedDevice(id: deviceId, brand: brand, model: "", sourceKind: .activityFile,
                     capabilities: [.hr], status: .paired, addedAt: now, lastSeenAt: now)
    }
}
