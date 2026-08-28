package com.atmaca.reeldroppro.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class QueueSelectionPolicyTest {
    @Test
    fun `selects retry wait only when due otherwise queued first`() {
        val jobs = listOf(
            QueueCandidate("a", JobState.RETRY_WAIT, nextAttemptAt = 2_000),
            QueueCandidate("b", JobState.QUEUED, nextAttemptAt = null)
        )
        assertEquals("b", QueueSelectionPolicy.next(jobs, nowMs = 1_000)?.id)
        assertEquals("a", QueueSelectionPolicy.next(jobs, nowMs = 3_000)?.id)
    }
}
