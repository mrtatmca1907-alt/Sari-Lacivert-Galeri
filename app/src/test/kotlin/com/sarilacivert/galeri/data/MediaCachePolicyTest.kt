package com.sarilacivert.galeri.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaCachePolicyTest {
    @Test
    fun cleanCacheIsReusedWithoutTimeExpiry() {
        assertTrue(MediaCachePolicy.shouldReuse(hasCache = true, dirty = false))
    }

    @Test
    fun dirtyCacheMustBeRebuilt() {
        assertFalse(MediaCachePolicy.shouldReuse(hasCache = true, dirty = true))
    }

    @Test
    fun missingCacheMustBeBuilt() {
        assertFalse(MediaCachePolicy.shouldReuse(hasCache = false, dirty = false))
    }
}
