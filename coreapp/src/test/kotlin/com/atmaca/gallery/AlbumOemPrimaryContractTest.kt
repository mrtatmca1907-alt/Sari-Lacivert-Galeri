package com.atmaca.gallery

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlbumOemPrimaryContractTest {
    @Test
    fun album_contents_use_oem_safe_collections_as_primary_source() {
        val source = File("src/main/kotlin/com/atmaca/gallery/GalleryViewModel.kt").readText()
        assertTrue(
            "Albüm içeriği Images + Video koleksiyonlarından güvenli biçimde okunmalı",
            source.contains("repository.loadAllInAlbumOemSafe(album)")
        )
        assertFalse(
            "Eksik ama boş olmayan Files sonucu albümün tamamı sanılmamalı",
            source.contains("if (fastPage.isNotEmpty() || snapshot.items.isNotEmpty())")
        )
    }
}
