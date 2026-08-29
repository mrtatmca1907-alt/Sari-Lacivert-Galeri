package com.sarilacivert.galeri.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileBrowserPolicyTest {
    @Test
    fun parentNavigationNeverEscapesRoot() {
        assertEquals("/storage/emulated/0", FileBrowserPolicy.parentPath("/storage/emulated/0", "/storage/emulated/0"))
        assertEquals("/storage/emulated/0", FileBrowserPolicy.parentPath("/storage/emulated/0/Download", "/storage/emulated/0"))
        assertEquals("/storage/emulated/0/Download", FileBrowserPolicy.parentPath("/storage/emulated/0/Download/Test", "/storage/emulated/0"))
    }

    @Test
    fun mediaKindsAreDetectedWithoutOpeningFiles() {
        assertTrue(FileBrowserPolicy.isImage("foto.JPEG"))
        assertTrue(FileBrowserPolicy.isVideo("video.MKV"))
        assertFalse(FileBrowserPolicy.isVideo("belge.pdf"))
        assertFalse(FileBrowserPolicy.isImage("arsiv.zip"))
    }

    @Test
    fun byteFormattingIsCompact() {
        assertEquals("0 B", FileBrowserPolicy.formatBytes(0))
        assertEquals("1.0 KB", FileBrowserPolicy.formatBytes(1024))
        assertEquals("1.0 MB", FileBrowserPolicy.formatBytes(1024L * 1024L))
    }
}
