package com.noop.protocol

/**
 * Reads the strap RTC's unix seconds out of a WHOOP 5/MG `GET_CLOCK` (command 11) COMMAND_RESPONSE.
 *
 * UNVALIDATED CANDIDATE, re-derived clean-room from captured frames. The puffin COMMAND_RESPONSE inner
 * starts at offset 8 as `[type 36][seq][cmd][origin_seq][result][payload…]`, the same shape the 4.0 inner
 * has at offset 4. On a 5/MG the reply to GET_CLOCK carries `result = 1` (the 5/MG SUCCESS polarity) and the
 * seconds as the first payload word, u32 little-endian at offset 13. Observed on one WHOOP 5.0/MG, firmware
 * 50.41.1.0: eleven consecutive connects (2026-11-03/04), each reply matching the phone's wall clock to the
 * second at the moment NOOP sent SET_CLOCK + GET_CLOCK.
 *
 * READOUT ONLY. A 5/MG's history and live records already carry the RTC's real-unix seconds, so nothing
 * decodes data against this value (see `WhoopBleClient.derivesClockCorrelation`). It feeds the Devices /
 * Test Centre "Clock latched" readout and nothing else.
 *
 * Every gate has to pass or the answer is null, so a reply of another shape reads as "no reading" rather
 * than as a confident wrong clock. Swift twin: `WhoopProtocol.Whoop5ClockReply`.
 */
object Whoop5ClockReply {
    /** COMMAND_RESPONSE packet type, at the puffin inner's first byte. */
    const val COMMAND_RESPONSE_TYPE = 36
    /** GET_CLOCK's command number. */
    const val GET_CLOCK_COMMAND = 11
    /** The 5/MG CommandResult SUCCESS value. */
    const val SUCCESS_RESULT = 1

    private const val TYPE_OFFSET = 8
    private const val COMMAND_OFFSET = 10
    private const val RESULT_OFFSET = 12
    private const val SECONDS_OFFSET = 13
    /** Header (8) + inner through the seconds word (9) + the trailing CRC32 (4). */
    private const val MINIMUM_LENGTH = 21

    /** The strap RTC in unix seconds, or null when [frame] is not a successful 5/MG GET_CLOCK reply.
     *  Swift twin: `Whoop5ClockReply.deviceUnix`. */
    fun deviceUnix(frame: ByteArray): Long? {
        if (frame.size < MINIMUM_LENGTH) return null
        if (frame[TYPE_OFFSET].toInt() and 0xFF != COMMAND_RESPONSE_TYPE) return null
        if (frame[COMMAND_OFFSET].toInt() and 0xFF != GET_CLOCK_COMMAND) return null
        if (frame[RESULT_OFFSET].toInt() and 0xFF != SUCCESS_RESULT) return null
        val s = SECONDS_OFFSET
        return (frame[s].toLong() and 0xFF) or
            ((frame[s + 1].toLong() and 0xFF) shl 8) or
            ((frame[s + 2].toLong() and 0xFF) shl 16) or
            ((frame[s + 3].toLong() and 0xFF) shl 24)
    }
}
