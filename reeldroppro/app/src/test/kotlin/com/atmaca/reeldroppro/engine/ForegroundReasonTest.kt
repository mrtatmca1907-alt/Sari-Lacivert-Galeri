package com.atmaca.reeldroppro.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class ForegroundReasonTest {
    @Test
    fun `active transfer requires foreground service`() {
        assertEquals(true, ForegroundReason.requiresForeground(JobState.DOWNLOADING))
        assertEquals(false, ForegroundReason.requiresForeground(JobState.COMPLETED))
    }
}
