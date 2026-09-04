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
}
