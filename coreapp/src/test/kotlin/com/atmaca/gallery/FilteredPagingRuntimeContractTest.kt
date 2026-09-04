package com.atmaca.gallery

import org.junit.Assert.assertTrue
import org.junit.Test

class FilteredPagingRuntimeContractTest {
    @Test
    fun `empty filtered page with more media requires another load`() {
        assertTrue(
            shouldLoadMoreForEmptyFilteredPage(
                totalLoaded = 250,
                filteredVisible = 0,
                hasMore = true,
                loading = false
            )
        )
    }
}
