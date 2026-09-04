package com.atmaca.gallery

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class KeysetPagingContractTest {
    private fun repositorySource(): String = File("src/main/kotlin/com/atmaca/gallery/MediaStoreRepository.kt").readText()
    private fun viewModelSource(): String = File("src/main/kotlin/com/atmaca/gallery/GalleryViewModel.kt").readText()

    @Test
    fun `mixed media continuation uses last media key instead of only offset`() {
        val repository = repositorySource()
        val viewModel = viewModelSource()
        assertTrue(repository.contains("loadMixedPageAfter("))
        assertTrue(repository.contains("DATE_ADDED}<?"))
        assertTrue(repository.contains("_ID}<?"))
        assertTrue(viewModel.contains("snapshot.items.lastOrNull()"))
        assertTrue(viewModel.contains("loadMixedPageAfter("))
    }
}
