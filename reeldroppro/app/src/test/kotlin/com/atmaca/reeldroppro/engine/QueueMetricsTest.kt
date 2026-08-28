package com.atmaca.reeldroppro.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class QueueMetricsTest {
    @Test
    fun `counts active completed failed and waiting jobs`() {
        val metrics = QueueMetrics.from(listOf(JobState.DOWNLOADING, JobState.COMPLETED, JobState.FAILED, JobState.RETRY_WAIT, JobState.QUEUED))
        assertEquals(1, metrics.active)
        assertEquals(1, metrics.completed)
        assertEquals(1, metrics.failed)
        assertEquals(2, metrics.waiting)
    }
}
