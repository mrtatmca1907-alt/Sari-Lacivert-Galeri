package com.atmaca.gallery

import android.Manifest

enum class GalleryTab { PHOTOS, VIDEOS }

data class TestMedia(val id: Long, val isVideo: Boolean)

fun requiredMediaPermissions(sdkInt: Int): List<String> =
    if (sdkInt >= 33) {
        listOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO
        )
    } else {
        listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

fun filterIdsForTab(items: List<TestMedia>, tab: GalleryTab): List<Long> =
    items.asSequence()
        .filter { item ->
            when (tab) {
                GalleryTab.PHOTOS -> !item.isVideo
                GalleryTab.VIDEOS -> item.isVideo
            }
        }
        .map(TestMedia::id)
        .toList()
