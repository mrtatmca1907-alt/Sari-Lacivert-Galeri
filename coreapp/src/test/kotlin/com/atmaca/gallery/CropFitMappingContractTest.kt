package com.atmaca.gallery

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class CropFitMappingContractTest {
    private fun source(): String = File("src/main/kotlin/com/atmaca/gallery/CropEditor.kt").readText()

    @Test
    fun `crop overlay uses fitted image bounds instead of whole screen`() {
        val text = source()
        assertTrue(text.contains("fitImageBounds("))
        assertTrue(text.contains("imageBounds.left"))
        assertTrue(text.contains("imageBounds.top"))
    }

    @Test
    fun `saved crop is mapped from fitted image coordinates`() {
        val text = source()
        assertTrue(text.contains("mapCropToBitmap("))
        assertTrue(text.contains("source.width"))
        assertTrue(text.contains("source.height"))
    }
}
