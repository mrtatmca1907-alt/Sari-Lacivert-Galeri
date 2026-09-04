package com.atmaca.gallery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MutationPerformanceRulesTest {
    @Test fun mutationRemovesOnlyAffectedItemsWithoutFullReload() {
        val current = listOf(10L, 11L, 12L, 13L, 14L)
        assertEquals(listOf(10L, 12L, 14L), removeMutatedIds(current, setOf(11L, 13L)))
    }

    @Test fun successfulTrashDeleteOrRestoreUsesLocalMutationRefresh() {
        val plan = mutationRefreshPlan(success = true)
        assertFalse(plan.reloadCollectionImmediately)
        assertFalse(plan.scanAlbumsImmediately)
        assertFalse(plan.scanDuplicatesImmediately)
        assertTrue(plan.refreshSecondaryScreensWhenOpened)
    }
}
