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

    @Test fun fileManagerMissingMimeFallsBackToExtension() {
        assertTrue(toolAcceptsDocument(AtmacaToolPage.PERSON_CROP, null, "IMG_1.JPG"))
        assertTrue(toolAcceptsDocument(AtmacaToolPage.PACKAGER, "application/octet-stream", "clip.MP4"))
        assertTrue(toolAcceptsDocument(AtmacaToolPage.VIDEO_FRAMES, "", "video.mov"))
        assertFalse(toolAcceptsDocument(AtmacaToolPage.VIDEO_FRAMES, null, "photo.jpg"))
        assertFalse(toolAcceptsDocument(AtmacaToolPage.PACKAGER, null, "note.pdf"))
    }
}
