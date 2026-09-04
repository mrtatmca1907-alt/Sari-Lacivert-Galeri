package com.atmaca.gallery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VisibilityAndPickerRegressionTest {
    @Test fun allThreeToolsCanUseGalleryInternalAlbumPicker() {
        assertTrue(toolUsesInternalAlbumPicker(AtmacaToolPage.PERSON_CROP))
        assertTrue(toolUsesInternalAlbumPicker(AtmacaToolPage.PACKAGER))
        assertTrue(toolUsesInternalAlbumPicker(AtmacaToolPage.VIDEO_FRAMES))
    }

    @Test fun videoFrameProgressIsVisibleAsFrameCount() {
        assertEquals("25 / 100 kare", videoFrameProgressText(25, 100))
        assertEquals("Kareler hazırlanıyor", videoFrameProgressText(0, 0))
    }

    @Test fun screenshotFlowRequiresExplicitSourceSelection() {
        assertTrue(screenshotSourceSelectionEnabled())
    }

    @Test fun albumOpenMustUseSeparateImageAndVideoCollections() {
        assertTrue(albumOpenUsesSeparateMediaCollections())
    }
    @Test fun rotatedPhotoStillAllowsHorizontalAlbumPagingAfterRelease() {
        assertTrue(shouldEnablePager(scale = 1f, rotation = 90f))
        assertTrue(!shouldPhotoConsumeGesture(pointerCount = 1, scale = 1f, rotation = 90f))
    }
    @Test fun partialOemPageStillRequestsTheNextPage() {
        assertTrue(hasMoreAfterPage(37))
        assertTrue(!hasMoreAfterPage(0))
    }

    @Test fun freeRotationSpringsBackToTheButtonSelectedBaseAngle() {
        assertEquals(90f, releasedViewerRotation(baseRotation = 90f), 0.0001f)
    }

    @Test fun incompleteAlbumSourcesAreMergedWithoutDuplicates() {
        val a = GalleryAlbum("DCIM/Camera/", "Camera", 2, null, 4L, "Camera")
        val b = GalleryAlbum("DCIM/Camera/", "Camera", 3, null, 4L, "Camera")
        val c = GalleryAlbum("Pictures/Extra/", "Extra", 1, null, 8L, "Extra")
        val merged = mergeAlbumSources(listOf(a), listOf(b, c))
        assertEquals(2, merged.size)
        assertEquals(3, merged.first { it.name == "Camera" }.count)
    }
}
