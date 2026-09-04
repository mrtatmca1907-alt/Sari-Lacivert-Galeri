package com.atmaca.gallery

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AlbumOemPrimaryContractTest {
    @Test
    fun album_contents_use_oem_safe_collections_as_primary_source() {
        val source = File("src/main/kotlin/com/atmaca/gallery/GalleryViewModel.kt").readText()
        assertTrue(
            source.contains("repository.loadAllInAlbumOemSafe(album)"),
            "Albüm içeriği Images + Video koleksiyonlarından güvenli biçimde okunmalı"
        )
        assertFalse(
            source.contains("if (fastPage.isNotEmpty() || snapshot.items.isNotEmpty())"),
            "Eksik ama boş olmayan Files sonucu albümün tamamı sanılmamalı"
        )
    }
}
