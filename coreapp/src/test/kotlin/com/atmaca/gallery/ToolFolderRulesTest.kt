package com.atmaca.gallery

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolFolderRulesTest {
    @Test fun personCropFolderAcceptsOnlyImages() {
        assertTrue(toolAcceptsMime(AtmacaToolPage.PERSON_CROP, "image/jpeg"))
        assertTrue(toolAcceptsMime(AtmacaToolPage.PERSON_CROP, "image/png"))
        assertFalse(toolAcceptsMime(AtmacaToolPage.PERSON_CROP, "video/mp4"))
    }

    @Test fun packagerAcceptsImagesAndVideosButFrameToolOnlyVideos() {
        assertTrue(toolAcceptsMime(AtmacaToolPage.PACKAGER, "image/webp"))
        assertTrue(toolAcceptsMime(AtmacaToolPage.PACKAGER, "video/mp4"))
        assertFalse(toolAcceptsMime(AtmacaToolPage.PACKAGER, "application/pdf"))
        assertTrue(toolAcceptsMime(AtmacaToolPage.VIDEO_FRAMES, "video/mp4"))
        assertFalse(toolAcceptsMime(AtmacaToolPage.VIDEO_FRAMES, "image/jpeg"))
    }
}
