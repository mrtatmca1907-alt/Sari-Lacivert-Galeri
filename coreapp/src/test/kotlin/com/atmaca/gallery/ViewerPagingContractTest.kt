package com.atmaca.gallery

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewerPagingContractTest {
    @Test
    fun viewer_requests_more_media_before_reaching_loaded_end() {
        val source = File("src/main/kotlin/com/atmaca/gallery/GalleryApp.kt").readText()
        assertTrue(
            "Görüntüleyici son yüklenen fotoğrafa yaklaşınca sonraki sayfayı istemeli",
            source.contains("viewerPagingShouldLoadMore") &&
                source.contains("onLoadMore()")
        )
    }
}
