package com.atmaca.gallery

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewerPinchFocusContractTest {
    @Test
    fun pinch_focus_uses_fitted_image_center_not_full_viewport_center() {
        val source = File("src/main/kotlin/com/atmaca/gallery/StablePhotoPage.kt").readText()

        assertFalse(
            "Sıkıştırarak zoom yaparken görüntü katmanında ekranın tam genişlik merkezini kullanmamalı",
            source.contains("centroid.x - viewportWidth / 2f")
        )
        assertFalse(
            "Sıkıştırarak zoom yaparken görüntü katmanında ekranın tam yükseklik merkezini kullanmamalı",
            source.contains("centroid.y - viewportHeight / 2f")
        )
        assertTrue(
            "Sıkıştırarak zoom yaparken fotoğrafın kendi oturtulmuş genişlik merkezi kullanılmalı",
            source.contains("centroid.x - fitted.width / 2f")
        )
        assertTrue(
            "Sıkıştırarak zoom yaparken fotoğrafın kendi oturtulmuş yükseklik merkezi kullanılmalı",
            source.contains("centroid.y - fitted.height / 2f")
        )
    }
}
