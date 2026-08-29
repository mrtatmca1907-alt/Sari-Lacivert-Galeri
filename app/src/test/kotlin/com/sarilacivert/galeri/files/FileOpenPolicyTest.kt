package com.sarilacivert.galeri.files

import org.junit.Assert.assertEquals
import org.junit.Test

class FileOpenPolicyTest {
    @Test
    fun `image extensions resolve to image mime types`() {
        assertEquals("image/jpeg", FileOpenPolicy.mimeTypeFor("foto.jpg"))
        assertEquals("image/png", FileOpenPolicy.mimeTypeFor("foto.png"))
        assertEquals("image/webp", FileOpenPolicy.mimeTypeFor("foto.webp"))
        assertEquals("image/heic", FileOpenPolicy.mimeTypeFor("foto.heic"))
    }

    @Test
    fun `video extensions resolve to video mime types`() {
        assertEquals("video/mp4", FileOpenPolicy.mimeTypeFor("video.mp4"))
        assertEquals("video/x-matroska", FileOpenPolicy.mimeTypeFor("video.mkv"))
    }
}
