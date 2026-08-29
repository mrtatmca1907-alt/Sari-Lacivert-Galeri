package com.sarilacivert.galeri.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaIndexPolicyTest {
    @Test
    fun cacheInsideTtlIsReusable() {
        assertTrue(MediaIndexPolicy.shouldReuseCache(2_500, 1_000, 2_000))
    }

    @Test
    fun expiredCacheIsNotReusable() {
        assertFalse(MediaIndexPolicy.shouldReuseCache(3_100, 1_000, 2_000))
    }

    @Test
    fun newestTimestampWins() {
        assertEquals(500L, MediaIndexPolicy.normalizedTimestamp(500, 400))
    }

    @Test
    fun clockRollbackNeverReusesCache() {
        assertFalse(MediaIndexPolicy.shouldReuseCache(900, 1_000, 2_000))
    }
}
