package com.atmaca.reeldroppro.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class FilenamePolicyTest {
    @Test
    fun `keeps stable id and extension while sanitizing title`() {
        assertEquals("video_adi_[abc123].mp4", FilenamePolicy.fileName("video adı", "abc123", "mp4"))
    }
}
