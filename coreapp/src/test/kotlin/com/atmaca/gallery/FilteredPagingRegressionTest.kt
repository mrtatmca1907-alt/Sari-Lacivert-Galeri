package com.atmaca.gallery

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilteredPagingRegressionTest {
    @Test
    fun `loads another page when current loaded page has no filter matches`() {
        assertTrue(
            shouldLoadMoreForEmptyFilteredPage(
                totalLoaded = 120,
                filteredVisible = 0,
                hasMore = true,
                loading = false
            )
        )
    }

    @Test
    fun `does not request another page while already loading`() {
        assertFalse(
            shouldLoadMoreForEmptyFilteredPage(
                totalLoaded = 120,
                filteredVisible = 0,
                hasMore = true,
                loading = true
            )
        )
    }

    @Test
    fun `does not request another page when filter already has results`() {
        assertFalse(
            shouldLoadMoreForEmptyFilteredPage(
                totalLoaded = 120,
                filteredVisible = 3,
                hasMore = true,
                loading = false
            )
        )
    }
}
