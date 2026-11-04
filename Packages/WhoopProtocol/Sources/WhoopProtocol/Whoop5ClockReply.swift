import Foundation

/// Reads the strap RTC's unix seconds out of a WHOOP 5/MG `GET_CLOCK` (command 11) COMMAND_RESPONSE.
///
/// UNVALIDATED CANDIDATE, re-derived clean-room from captured frames. The puffin COMMAND_RESPONSE inner
/// starts at offset 8 as `[type 36][seq][cmd][origin_seq][result][payload…]`, the same shape the 4.0 inner
/// has at offset 4. On a 5/MG the reply to GET_CLOCK carries `result = 1` (the 5/MG SUCCESS polarity) and
/// the seconds as the first payload word, u32 little-endian at offset 13. Observed on one WHOOP 5.0/MG,
/// firmware 50.41.1.0: eleven consecutive connects (2026-11-03/04), each reply matching the phone's wall
/// clock to the second at the moment NOOP sent SET_CLOCK + GET_CLOCK.
///
/// READOUT ONLY. A 5/MG's history and live records already carry the RTC's real-unix seconds, so nothing
/// decodes data against this value (see `BackfillContinuation.derivesClockCorrelation`). It feeds the
/// Devices / Test Centre "Clock latched" readout and nothing else.
///
/// Every gate below has to pass or the answer is nil, so a reply of another shape reads as "no reading"
/// rather than as a confident wrong clock. Kotlin twin: `com.noop.protocol.Whoop5ClockReply`.
public enum Whoop5ClockReply {
    /// COMMAND_RESPONSE packet type, at the puffin inner's first byte.
    public static let commandResponseType = 36
    /// GET_CLOCK's command number.
    public static let getClockCommand = 11
    /// The 5/MG CommandResult SUCCESS value.
    public static let successResult = 1
    /// Offsets inside the whole puffin frame (inner at 8).
    static let typeOffset = 8
    static let commandOffset = 10
    static let resultOffset = 12
    static let secondsOffset = 13
    /// Header (8) + inner through the seconds word (9) + the trailing CRC32 (4).
    static let minimumLength = 21

    /// The strap RTC in unix seconds, or nil when `frame` is not a successful 5/MG GET_CLOCK reply.
    /// Kotlin twin: `Whoop5ClockReply.deviceUnix`.
    public static func deviceUnix(frame: [UInt8]) -> Int? {
        guard frame.count >= minimumLength,
              Int(frame[typeOffset]) == commandResponseType,
              Int(frame[commandOffset]) == getClockCommand,
              Int(frame[resultOffset]) == successResult else { return nil }
        let s = secondsOffset
        return Int(frame[s]) | Int(frame[s + 1]) << 8 | Int(frame[s + 2]) << 16 | Int(frame[s + 3]) << 24
    }
}
