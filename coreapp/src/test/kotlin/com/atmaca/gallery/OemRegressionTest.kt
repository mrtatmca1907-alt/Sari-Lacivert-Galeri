package com.atmaca.gallery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test fun backClearsSelectionBeforeLeavingGallery() {
        assertEquals(GalleryBackAction.CLEAR_SELECTION, galleryBackAction(selectedCount = 719, inAlbum = false))
        assertEquals(GalleryBackAction.CLOSE_ALBUM, galleryBackAction(selectedCount = 0, inAlbum = true))
        assertEquals(GalleryBackAction.EXIT, galleryBackAction(selectedCount = 0, inAlbum = false))
    }

    @Test fun internalPickerMustReplaceOuterToolDialogWhileOpen() {
        assertFalse(shouldRenderOuterToolDialog(showInternalAlbumPicker = true))
        assertTrue(shouldRenderOuterToolDialog(showInternalAlbumPicker = false))
    }

    @Test fun albumGridKeyStaysUniqueWhenRelativePathIsBlank() {
        val a = GalleryAlbum("", "Camera", 5, null, bucketId = 11L, bucketName = "Camera")
        val b = GalleryAlbum("", "Screenshots", 8, null, bucketId = 22L, bucketName = "Screenshots")
        assertFalse(albumGridKey(a) == albumGridKey(b))
    }

    @Test fun videoFramesKeepDialogVisibleSoCounterCanBeSeen() {
        assertTrue(keepToolDialogOpenForBackgroundProgress(AtmacaToolPage.VIDEO_FRAMES))
        assertFalse(keepToolDialogOpenForBackgroundProgress(AtmacaToolPage.PERSON_CROP))
    }

    @Test fun buildIdentityIsVisibleAndUniqueForThisPhoneTest() {
        assertEquals("BUILD 140904", visibleBuildBadge())
        assertTrue(appVersionCodeForTest() > 13)
    }
}
