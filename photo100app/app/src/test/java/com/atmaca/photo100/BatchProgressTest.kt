package com.atmaca.photo100

import org.junit.Assert.assertEquals
import org.junit.Test

class BatchProgressTest {
    @Test
    fun nextCursorUsesSmallestShownId() {
        assertEquals(701L, BatchProgress.nextCursor(listOf(800L, 750L, 701L)))
    }

    @Test
    fun emptyBatchKeepsCurrentCursor() {
        assertEquals(500L, BatchProgress.nextCursor(emptyList(), currentCursor = 500L))
    }
}
