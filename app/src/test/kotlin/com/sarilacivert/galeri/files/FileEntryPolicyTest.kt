package com.sarilacivert.galeri.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileEntryPolicyTest {
    @Test
    fun foldersAlwaysSortBeforeFiles() {
        val folder = FileEntryPolicy.Entry("B", true)
        val file = FileEntryPolicy.Entry("A.jpg", false)
        assertTrue(FileEntryPolicy.compare(folder, file) < 0)
    }

    @Test
    fun namesSortCaseInsensitively() {
        val a = FileEntryPolicy.Entry("alpha", false)
        val b = FileEntryPolicy.Entry("Beta", false)
        assertTrue(FileEntryPolicy.compare(a, b) < 0)
    }

    @Test
    fun imageAndVideoExtensionsAreRecognized() {
        assertTrue(FileEntryPolicy.isPreviewable("foto.JPG"))
        assertTrue(FileEntryPolicy.isPreviewable("film.mp4"))
        assertFalse(FileEntryPolicy.isPreviewable("arsiv.zip"))
    }

    @Test
    fun extensionExtractionIsStable() {
        assertEquals("jpg", FileEntryPolicy.extensionOf("a.b.c.JPG"))
        assertEquals("", FileEntryPolicy.extensionOf("README"))
    }
}
