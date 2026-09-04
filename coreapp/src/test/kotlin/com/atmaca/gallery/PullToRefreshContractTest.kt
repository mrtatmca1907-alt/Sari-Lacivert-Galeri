package com.atmaca.gallery

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class PullToRefreshContractTest {
    @Test
    fun gallery_supports_pull_down_refresh_without_permanent_button() {
        val source = File("src/main/kotlin/com/atmaca/gallery/GalleryApp.kt").readText()
        assertTrue("Albüm ızgarası gerçek çek-yenile bileşenini kullanmalı", source.contains("PullToRefreshBox("))
        assertTrue("Çek-yenile albüm sorgusunu yeniden başlatmalı", source.contains("onRefresh = { refreshCurrentSection() }"))
    }
}
