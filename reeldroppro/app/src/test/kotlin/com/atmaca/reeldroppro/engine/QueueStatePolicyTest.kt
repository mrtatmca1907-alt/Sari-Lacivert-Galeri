package com.atmaca.reeldroppro.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class QueueStatePolicyTest {
    @Test
    fun `network loss moves active job to retry wait without failing it`() {
        assertEquals(JobState.RETRY_WAIT, QueueStatePolicy.onNetworkLost(JobState.DOWNLOADING))
        assertEquals(JobState.RETRY_WAIT, QueueStatePolicy.onNetworkLost(JobState.RESOLVING))
    }
}
