package com.sarilacivert.galeri.data

import org.junit.Assert.assertEquals
import org.junit.Test

class BitmapSizingPolicyTest {
    @Test
    fun thumbnailSizeIsClampedToSafeBounds() {
        assertEquals(96, BitmapSizingPolicy.safeThumbnailSize(24))
        assertEquals(320, BitmapSizingPolicy.safeThumbnailSize(320))
        assertEquals(512, BitmapSizingPolicy.safeThumbnailSize(900))
    }

    @Test
    fun viewerTargetUsesTwoTimesViewportWithBounds() {
        assertEquals(1536, BitmapSizingPolicy.viewerTarget(600))
        assertEquals(2400, BitmapSizingPolicy.viewerTarget(1200))
        assertEquals(4096, BitmapSizingPolicy.viewerTarget(3000))
    }
}
