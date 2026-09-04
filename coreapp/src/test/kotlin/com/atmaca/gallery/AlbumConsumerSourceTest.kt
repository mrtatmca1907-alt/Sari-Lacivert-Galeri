package com.atmaca.gallery

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AlbumConsumerSourceTest {
    private fun source(name: String) = File("src/main/kotlin/com/atmaca/gallery/$name").readText()

    @Test fun bothAlbumConsumersUseCompleteSnapshot() {
        assertTrue(source("GalleryApp.kt").contains("loadCompleteAlbums"))
        assertTrue(source("InternalToolAlbumPicker.kt").contains("loadCompleteAlbums"))
    }

    @Test fun visibleAlbumsNeverFallBackToTheCurrentMediaPage() {
        assertFalse(source("GalleryApp.kt").contains("else quickAlbums(state.items)"))
        assertFalse(source("InternalToolAlbumPicker.kt").contains("loadMixedPage(offset"))
    }
}
