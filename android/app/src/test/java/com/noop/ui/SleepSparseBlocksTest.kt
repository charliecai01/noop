package com.noop.ui

import com.noop.data.SleepSession
import org.junit.Assert.assertEquals
import org.junit.Test

/** Which stored blocks the sleep caveats read ([sleepDayBlocks]). */
class SleepSparseBlocksTest {

    private fun block(sparse: Boolean?, start: Long = 0L) =
        SleepSession(deviceId = "test", startTs = start, endTs = start + 3600, stagingSparse = sparse)

    // ── which blocks get read ─────────────────────────────────────────────────────────────────────

    @Test fun `the bridged group is what gets read when there is one`() {
        val main = block(null)
        val group = listOf(block(null, 1), block(true, 2))
        assertEquals(group, sleepDayBlocks(main, group))
    }

    @Test fun `the main block is the fallback when the group is empty`() {
        val main = block(true)
        assertEquals(listOf(main), sleepDayBlocks(main, emptyList()))
    }

    @Test fun `no session and no group reads nothing rather than throwing`() {
        assertEquals(emptyList<SleepSession>(), sleepDayBlocks(null, emptyList()))
    }
}
