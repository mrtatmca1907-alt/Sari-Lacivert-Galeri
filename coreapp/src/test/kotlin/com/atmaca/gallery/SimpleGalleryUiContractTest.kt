package com.atmaca.gallery

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SimpleGalleryUiContractTest {
    private fun gallerySource(): String {
        val candidates = listOf(
            File("src/main/kotlin/com/atmaca/gallery/GalleryApp.kt"),
            File("coreapp/src/main/kotlin/com/atmaca/gallery/GalleryApp.kt")
        )
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("GalleryApp.kt source not found")
    }

    @Test fun `home starts in albums and removes permanent bottom navigation`() {
        val source = gallerySource()
        assertTrue(source.contains("mutableStateOf(HomeSection.ALBUMS)"))
        assertFalse(source.contains("NavigationBarItem("))
    }

    @Test fun `overflow toggles between folders and all media`() {
        val source = gallerySource()
        assertTrue(source.contains("Tüm klasör içeriğini göster"))
        assertTrue(source.contains("Klasör görünümüne geç"))
        assertTrue(source.contains("Sıralama ölçütü"))
        assertTrue(source.contains("Medyayı filtrele"))
    }

    @Test fun `refresh is no longer a permanent top bar button`() {
        val source = gallerySource()
        assertFalse(source.contains("Icon(Icons.Default.Refresh, \"Yenile\")"))
    }
}
