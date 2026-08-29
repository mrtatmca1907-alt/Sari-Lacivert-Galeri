package com.sarilacivert.galeri.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BatchProgressTest {
    @Test
    fun progressNeverExceedsTotal() {
        assertEquals(10, BatchProgress(total = 10, processed = 15, succeeded = 12, failed = 3).processed)
    }

    @Test
    fun failuresStillCountAsProcessed() {
        val p = BatchProgress(total = 10, processed = 7, succeeded = 5, failed = 2)
        assertEquals(7, p.processed)
        assertEquals(2, p.failed)
    }

    @Test
    fun completeOnlyWhenProcessedReachesTotal() {
        assertFalse(BatchProgress(total = 10, processed = 9, succeeded = 9, failed = 0).isComplete)
        assertTrue(BatchProgress(total = 10, processed = 10, succeeded = 9, failed = 1).isComplete)
    }
}
