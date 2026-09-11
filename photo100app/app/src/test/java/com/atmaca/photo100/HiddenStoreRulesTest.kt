package com.atmaca.photo100

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HiddenStoreRulesTest {
    @Test
    fun recognizesSupportedPhotoExtensions() {
        assertTrue(HiddenStoreRules.isPhoto("IMG_1.JPG"))
        assertTrue(HiddenStoreRules.isPhoto("x.heic"))
        assertTrue(HiddenStoreRules.isPhoto("x.webp"))
        assertFalse(HiddenStoreRules.isPhoto("video.mp4"))
        assertFalse(HiddenStoreRules.isPhoto(".nomedia"))
    }

    @Test
    fun excludesHiddenStoreAndAndroidFoldersFromCollection() {
        assertFalse(HiddenStoreRules.shouldEnter("/storage/emulated/0/.Foto100Kasa"))
        assertFalse(HiddenStoreRules.shouldEnter("/storage/emulated/0/Android"))
        assertTrue(HiddenStoreRules.shouldEnter("/storage/emulated/0/DCIM"))
        assertTrue(HiddenStoreRules.shouldEnter("/storage/emulated/0/Pictures"))
    }
}
