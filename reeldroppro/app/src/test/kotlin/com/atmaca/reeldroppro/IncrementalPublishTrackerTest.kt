package com.atmaca.reeldroppro

import com.atmaca.reeldroppro.storage.IncrementalPublishTracker
import org.junit.Assert.assertEquals
import org.junit.Test

class IncrementalPublishTrackerTest {
    @Test
    fun `returns each completed file only once`() {
        val tracker = IncrementalPublishTracker()
        val first = tracker.unpublished(listOf("a.jpg", "b.mp4"))
        assertEquals(listOf("a.jpg", "b.mp4"), first)
        tracker.markPublished(first)
        assertEquals(emptyList<String>(), tracker.unpublished(listOf("a.jpg", "b.mp4")))
        assertEquals(listOf("c.jpg"), tracker.unpublished(listOf("a.jpg", "b.mp4", "c.jpg")))
    }
}
