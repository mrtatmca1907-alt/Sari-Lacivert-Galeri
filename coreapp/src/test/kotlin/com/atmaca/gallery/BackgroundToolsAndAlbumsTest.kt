package com.atmaca.gallery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BackgroundToolsAndAlbumsTest {
    @Test fun everyToolUsesBackgroundWork() {
        AtmacaToolPage.entries.forEach { assertTrue(toolUsesBackgroundWorker(it)) }
    }

    @Test fun leavingDialogNeverCancelsWork() {
        assertFalse(shouldCancelMediaToolWork(explicitCancel = false))
        assertTrue(shouldCancelMediaToolWork(explicitCancel = true))
    }

    @Test fun fileQueueDeduplicatesLargeSelections() {
        val file = File.createTempFile("atmaca-media-tool", ".queue")
        try {
            assertTrue(writeMediaToolQueue(file, listOf("content://a", "content://a", "content://b")))
            assertEquals(listOf("content://a", "content://b"), readMediaToolQueue(file))
            assertFalse(mediaToolInputKeys().contains("uris"))
        } finally {
            file.delete()
        }
    }

    @Test fun completeFailureDoesNotInventAlbums() {
        val result = albumQueryOutcome(emptyList(), imageFailed = true, videoFailed = true)
        assertTrue(result.albums.isEmpty())
        assertTrue(result.completelyFailed)
    }

    @Test fun hiosAlbumQueryFallsBackFromCompoundSortToSimpleAndUnsorted() {
        assertEquals(
            listOf("date_added DESC, _id DESC", "date_added DESC", null),
            albumQuerySortFallbacks()
        )
    }

    @Test fun fileBackedAlbumScanContinuesOnlyAfterAFullPage() {
        assertTrue(albumScanShouldContinue(received = 600, pageSize = 600))
        assertFalse(albumScanShouldContinue(received = 599, pageSize = 600))
        assertFalse(albumScanShouldContinue(received = 0, pageSize = 600))
    }

    @Test fun hiosAlbumPagesAdvanceByOffset() {
        assertEquals(600, nextAlbumPageOffset(currentOffset = 0, received = 600))
        assertEquals(1200, nextAlbumPageOffset(currentOffset = 600, received = 600))
    }
}
