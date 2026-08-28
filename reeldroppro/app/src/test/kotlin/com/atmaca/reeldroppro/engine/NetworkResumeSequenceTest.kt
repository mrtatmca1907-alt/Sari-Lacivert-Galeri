package com.atmaca.reeldroppro.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkResumeSequenceTest {
    @Test
    fun `download job returns to queue after reconnect`() {
        val lost = QueueStatePolicy.onNetworkLost(JobState.DOWNLOADING)
        assertEquals(JobState.RETRY_WAIT, lost)
        assertEquals(JobState.QUEUED, ResumePolicy.onConnectivityRestored(lost))
    }
}
