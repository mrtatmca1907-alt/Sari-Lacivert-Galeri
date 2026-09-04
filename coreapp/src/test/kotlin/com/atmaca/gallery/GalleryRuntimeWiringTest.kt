package com.atmaca.gallery

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class GalleryRuntimeWiringTest {
    private fun gallerySource(): String {
        val candidates = listOf(
            File("coreapp/src/main/kotlin/com/atmaca/gallery/GalleryApp.kt"),
            File("src/main/kotlin/com/atmaca/gallery/GalleryApp.kt")
        )
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("GalleryApp.kt bulunamadi")
    }

    @Test
    fun `filtered empty page rule is wired to real media collection`() {
        val source = gallerySource()
        assertTrue(source.contains("shouldLoadMoreForEmptyFilteredPage("))
        assertTrue(source.contains("onLoadMore()"))
    }

    @Test
    fun `drag edge auto scroll is wired to real media grid`() {
        val source = gallerySource()
        assertTrue(source.contains("dragAutoScrollDelta("))
        assertTrue(source.contains("gridState.scrollBy("))
    }
}
