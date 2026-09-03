package com.atmaca.gallery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OemRegressionTest {
    @Test fun albumLocatorPrefersRealPathSoPhotoAndVideoCollectionsMerge() {
        assertEquals(
            AlbumLocator.Path("DCIM/Camera/"),
            albumLocator("DCIM/Camera/", 4242L, "Camera")
        )
    }

    @Test fun allToolsUseGalleryInternalAlbumPickerInsteadOfSafTree() {
        assertTrue(toolUsesInternalAlbumPicker(AtmacaToolPage.PERSON_CROP))
        assertTrue(toolUsesInternalAlbumPicker(AtmacaToolPage.PACKAGER))
        assertTrue(toolUsesInternalAlbumPicker(AtmacaToolPage.VIDEO_FRAMES))
    }

    @Test fun dragSelectionFillsEveryIndexCrossedEvenWhenPointerSkipsCells() {
        assertEquals(listOf(2, 3, 4, 5, 6), dragSelectionIndexes(2, 6))
        assertEquals(listOf(6, 5, 4, 3, 2), dragSelectionIndexes(6, 2))
        assertEquals(listOf(4), dragSelectionIndexes(4, 4))
    }
}
