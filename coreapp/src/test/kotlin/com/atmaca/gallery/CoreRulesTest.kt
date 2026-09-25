package com.atmaca.gallery

import android.Manifest
import org.junit.Assert.assertEquals
import org.junit.Test

class CoreRulesTest {
    @Test
    fun android13RequestsImageAndVideoPermissions() {
        assertEquals(
            listOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO),
            requiredMediaPermissions(33)
        )
    }

    @Test
    fun oldAndroidRequestsLegacyStoragePermission() {
        assertEquals(
            listOf(Manifest.permission.READ_EXTERNAL_STORAGE),
            requiredMediaPermissions(32)
        )
    }

    @Test
    fun photoTabKeepsOnlyImages() {
        val items = listOf(
            TestMedia(1, false),
            TestMedia(2, true),
            TestMedia(3, false)
        )
        assertEquals(listOf(1L, 3L), filterIdsForTab(items, GalleryTab.PHOTOS))
    }

    @Test
    fun videoTabKeepsOnlyVideos() {
        val items = listOf(TestMedia(1, false), TestMedia(2, true))
        assertEquals(listOf(2L), filterIdsForTab(items, GalleryTab.VIDEOS))
    }
}
