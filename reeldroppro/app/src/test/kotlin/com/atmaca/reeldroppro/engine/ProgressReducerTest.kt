package com.atmaca.reeldroppro.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class ProgressReducerTest {
    @Test
    fun `aggregates photo video failure and byte counters`() {
        val state = ProgressReducer.applyAll(
            DownloadProgress(),
            listOf(
                ProgressEvent.PhotoCompleted(120),
                ProgressEvent.VideoCompleted(300),
                ProgressEvent.Failed,
                ProgressEvent.BytesDownloaded(1024)
            )
        )
        assertEquals(1, state.photos)
        assertEquals(1, state.videos)
        assertEquals(1, state.failed)
        assertEquals(1024L, state.bytesDownloaded)
    }
}
