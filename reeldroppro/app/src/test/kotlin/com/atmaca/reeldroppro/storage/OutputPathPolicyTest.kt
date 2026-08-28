package com.atmaca.reeldroppro.storage

import org.junit.Assert.assertEquals
import org.junit.Test

class OutputPathPolicyTest {
    @Test
    fun `builds deterministic platform source and media folders`() {
        assertEquals(
            "Download/ReelDrop Pro/instagram/1birsenaltuntas/photos",
            OutputPathPolicy.relativePath("Instagram", "1birsenaltuntas", MediaBucket.PHOTO)
        )
        assertEquals(
            "Download/ReelDrop Pro/facebook/page_name/videos",
            OutputPathPolicy.relativePath("Facebook", "page name", MediaBucket.VIDEO)
        )
    }

    @Test
    fun `sanitizes unsafe path characters without losing source identity`() {
        assertEquals(
            "Download/ReelDrop Pro/instagram/pelin_akil/photos",
            OutputPathPolicy.relativePath("Instagram", "pelin/akil", MediaBucket.PHOTO)
        )
    }
}
