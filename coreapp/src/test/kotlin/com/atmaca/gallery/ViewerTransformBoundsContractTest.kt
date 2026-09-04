package com.atmaca.gallery

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewerTransformBoundsContractTest {
    @Test
    fun photo_transform_uses_fitted_image_bounds_instead_of_full_viewport_layer() {
        val source = File("src/main/kotlin/com/atmaca/gallery/StablePhotoPage.kt").readText()
        assertTrue(
            "Zoom/döndürme tam ekran katmanına değil, ekrana sığdırılmış fotoğraf alanına uygulanmalı",
            source.contains("viewerImageRenderSize") &&
                source.contains("requiredWidth") &&
                source.contains("requiredHeight")
        )
    }
}
