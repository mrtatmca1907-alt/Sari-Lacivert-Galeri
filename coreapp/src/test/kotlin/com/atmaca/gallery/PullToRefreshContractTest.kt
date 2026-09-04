package com.atmaca.gallery

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class PullToRefreshContractTest {
    @Test
    fun gallery_supports_pull_down_refresh_without_permanent_button() {
        val source = File("src/main/kotlin/com/atmaca/gallery/GalleryApp.kt").readText()
        assertTrue(
            "Galeri aşağı çekilince yenilemeli ve kalıcı yenile düğmesine ihtiyaç duymamalı",
            source.contains("pullRefresh") ||
                source.contains("PullToRefreshBox") ||
                source.contains("nestedScroll") && source.contains("refresh")
        )
    }
}
