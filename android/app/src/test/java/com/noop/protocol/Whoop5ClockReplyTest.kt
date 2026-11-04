package com.noop.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [Whoop5ClockReply] over real GET_CLOCK replies captured from one WHOOP 5.0/MG (firmware 50.41.1.0). Same
 * frames and answers as the Swift twin's `Whoop5ClockReplyTests`, whose passing run fixed these values;
 * each equals the phone's wall clock when the request went out.
 */
class Whoop5ClockReplyTest {
    private fun bytes(hex: String) = ByteArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    private val evening = "aa011400010021b124b80b0301cbcbea6a000000000000006a13afe7"   // 2026-11-03 22:08:11 PST
    private val morning = "aa011400010021b124920b03016f4eeb6a000000000000002e4f965e"   // 2026-11-04 07:25:35 PST
    private val later = "aa011400010021b124280b0301825beb6a000000000000005ec79f96"     // 2026-11-04 08:21:22 PST

    /** Three captured replies, an evening and two mornings apart: the decode tracks a moving clock. */
    @Test fun `captured replies decode to the wall clock they were sent at`() {
        assertEquals(1_793_772_491L, Whoop5ClockReply.deviceUnix(bytes(evening)))
        assertEquals(1_793_805_935L, Whoop5ClockReply.deviceUnix(bytes(morning)))
        assertEquals(1_793_809_282L, Whoop5ClockReply.deviceUnix(bytes(later)))
    }

    /** A SET_CLOCK reply carries no clock: its command byte is 10, so it must not decode. */
    @Test fun `set clock reply is not a clock reading`() {
        assertNull(Whoop5ClockReply.deviceUnix(bytes("aa010c000100271124b70a020100000088d52b9e")))
    }

    /** A non-SUCCESS result, a short frame, or a non-response type each read as no reading. */
    @Test fun `any other shape reads as no reading`() {
        assertNull(Whoop5ClockReply.deviceUnix(bytes(later).also { it[12] = 0 }))
        assertNull(Whoop5ClockReply.deviceUnix(bytes(later).also { it[8] = 35 }))
        assertNull(Whoop5ClockReply.deviceUnix(bytes(later).copyOf(20)))
        assertNull(Whoop5ClockReply.deviceUnix(ByteArray(0)))
    }
}
