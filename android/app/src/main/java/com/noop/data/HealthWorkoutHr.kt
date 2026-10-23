package com.noop.data

/**
 * Heart rate an external workout carried into the platform health store: a bike computer's ride synced
 * to Health Connect by its companion app, a watch's exercise session, an Apple Health workout on iOS.
 *
 * Persisted as its own import source so the day-owner resolver can pick it on a day no strap covers, the
 * same way an imported activity file does (#137): without stored samples a ride carried its own HR but
 * left the DAY's Effort dark. Registered as an [SourceKind.activityFile] device, so it ranks at priority 3,
 * below whole-day imports, and a strap or full-day import with HR for the day always wins.
 *
 * Only the sample → row rule and the registry row live here, so both are testable without Health Connect.
 * Twin of Swift `HealthWorkoutHR` (WhoopStore).
 */
object HealthWorkoutHr {
    /** The `hrSample.deviceId` and registry id. Shared with iOS, just as both platforms store the
     *  health-platform import under "apple-health". */
    const val DEVICE_ID = "apple-health-workouts"

    /** Plausible bpm bounds, inclusive, applied to the RAW value before rounding. */
    const val MIN_BPM = 25.0
    const val MAX_BPM = 240.0

    /**
     * The stored row for one health-store sample, or null when its bpm is not plausible.
     *
     * [epochSecond] is the whole unix second the sample started (Health Connect's `Instant.epochSecond`
     * already floors, as the Swift twin does) and the bpm is rounded half up, which for these positive
     * values is the Swift twin's half away from zero. `Math.round`, not `kotlin.math.round`: the latter
     * rounds a tie to even and would store 72 where iOS stores 73. Two samples in one second keep the
     * first, because [WhoopRepository.insertHr] ignores a repeated `(deviceId, ts)`.
     */
    fun sample(epochSecond: Long, bpm: Double): HrSample? {
        if (!bpm.isFinite() || bpm < MIN_BPM || bpm > MAX_BPM) return null
        return HrSample(deviceId = DEVICE_ID, ts = epochSecond, bpm = Math.round(bpm).toInt())
    }

    /** The registry row that makes the source visible to the day-owner resolver. `paired`, never
     *  `active`, so it cannot displace the live strap; `hr` is the only metric it can provide. */
    fun registryDevice(brand: String, now: Long): PairedDeviceRow = PairedDeviceRow(
        id = DEVICE_ID,
        brand = brand,
        model = "",
        nickname = null,
        peripheralId = null,
        sourceKind = SourceKind.activityFile.name,
        capabilities = Metric.hr.name,
        status = DeviceStatus.paired.name,
        addedAt = now,
        lastSeenAt = now,
    )
}
