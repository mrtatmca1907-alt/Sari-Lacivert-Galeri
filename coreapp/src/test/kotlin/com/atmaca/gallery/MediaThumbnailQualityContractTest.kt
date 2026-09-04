package com.atmaca.gallery

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaThumbnailQualityContractTest {
    private fun source(): String = File("src/main/kotlin/com/atmaca/gallery/GalleryApp.kt").readText()

    @Test
    fun `media grid uses high quality thumbnails`() {
        val text = source()
        assertTrue(text.contains("HIGH_QUALITY_THUMBNAIL_EDGE"))
        assertTrue(text.contains("loadThumbnailCompat(context, item, HIGH_QUALITY_THUMBNAIL_EDGE)"))
    }
}
