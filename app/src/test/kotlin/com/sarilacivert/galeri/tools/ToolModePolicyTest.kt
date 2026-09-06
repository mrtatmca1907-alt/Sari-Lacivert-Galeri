package com.sarilacivert.galeri.tools

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolModePolicyTest {
    @Test
    fun personCropAcceptsImagesOnly() {
        assertTrue(ToolModePolicy.accepts(ToolMode.PERSON_CROP, "a.jpg", "image/jpeg"))
        assertFalse(ToolModePolicy.accepts(ToolMode.PERSON_CROP, "a.mp4", "video/mp4"))
    }

    @Test
    fun videoFramesAcceptsVideosOnly() {
        assertTrue(ToolModePolicy.accepts(ToolMode.VIDEO_FRAMES, "a.mp4", "video/mp4"))
        assertFalse(ToolModePolicy.accepts(ToolMode.VIDEO_FRAMES, "a.png", "image/png"))
    }

    @Test
    fun packagerAcceptsImagesAndVideos() {
        assertTrue(ToolModePolicy.accepts(ToolMode.PACKAGER, "a.webp", "image/webp"))
        assertTrue(ToolModePolicy.accepts(ToolMode.PACKAGER, "a.mkv", "video/x-matroska"))
        assertFalse(ToolModePolicy.accepts(ToolMode.PACKAGER, "a.pdf", "application/pdf"))
    }
}
