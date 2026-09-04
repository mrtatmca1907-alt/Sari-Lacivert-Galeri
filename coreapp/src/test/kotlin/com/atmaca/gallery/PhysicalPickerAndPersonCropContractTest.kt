package com.atmaca.gallery

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PhysicalPickerAndPersonCropContractTest {
    @Test
    fun internalAlbumPickerDoesNotBlockOnWholeLibraryBeforeShowingFolders() {
        val source = File("src/main/kotlin/com/atmaca/gallery/InternalToolAlbumPicker.kt").readText()
        assertFalse(source.contains("repository.loadAlbumsOemSafe()"))
        assertTrue(source.contains("repository.loadMixedPage("))
    }

    @Test
    fun personCropKeepsMuchMoreThanFaceAndUpperBody() {
        val crop = personCropBounds(
            sourceWidth = 1080,
            sourceHeight = 1920,
            faceLeft = 430,
            faceTop = 420,
            faceRight = 650,
            faceBottom = 640
        )
        assertTrue(crop.width >= 750)
        assertTrue(crop.height >= 1400)
        assertTrue(crop.top <= 220)
        assertTrue(crop.bottom >= 1600)
    }
}
