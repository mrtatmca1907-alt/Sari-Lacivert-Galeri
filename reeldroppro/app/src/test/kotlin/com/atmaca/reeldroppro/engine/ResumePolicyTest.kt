package com.atmaca.reeldroppro.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class ResumePolicyTest {
    @Test
    fun `retry wait resumes to queued after connectivity returns`() {
        assertEquals(JobState.QUEUED, ResumePolicy.onConnectivityRestored(JobState.RETRY_WAIT))
        assertEquals(JobState.COMPLETED, ResumePolicy.onConnectivityRestored(JobState.COMPLETED))
    }
}
