package com.atmaca.gallery

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class OemAlbumRuntimeContractTest {
    private fun source(): String = File("src/main/kotlin/com/atmaca/gallery/GalleryViewModel.kt").readText()

    @Test
    fun `album mode uses OEM safe album loader`() {
        val text = source()
        assertTrue(text.contains("loadAllInAlbumOemSafe("))
    }

    @Test
    fun `album loader keeps paging semantics after OEM safe load`() {
        val text = source()
        assertTrue(text.contains("MediaStoreRepository.PAGE_SIZE"))
        assertTrue(text.contains("snapshot.items.size"))
    }
}
