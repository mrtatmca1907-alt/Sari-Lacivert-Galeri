package com.atmaca.reeldroppro.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class TempFilePolicyTest {
    @Test
    fun `download uses part suffix and final name after validation`() {
        assertEquals("video.mp4.part", TempFilePolicy.tempName("video.mp4"))
        assertEquals("video.mp4", TempFilePolicy.finalName("video.mp4.part"))
    }
}
