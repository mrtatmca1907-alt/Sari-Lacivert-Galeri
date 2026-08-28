package com.atmaca.reeldroppro.storage

import org.junit.Assert.assertEquals
import org.junit.Test

class OutputPathPolicyTest {
    @Test
    fun `builds ReelDrop V2 profile hashtag and Facebook folders`() {
        assertEquals(
            "Download/ReelDrop Pro/Profil/1birsenaltuntas/Fotoğraflar",
            OutputPathPolicy.relativePath("INSTAGRAM_PROFILE", "1birsenaltuntas", MediaBucket.PHOTO)
        )
        assertEquals(
            "Download/ReelDrop Pro/Hashtag/pelinakil/Videolar",
            OutputPathPolicy.relativePath("INSTAGRAM_HASHTAG", "#pelinakil", MediaBucket.VIDEO)
        )
        assertEquals(
            "Download/ReelDrop Pro/Facebook/page_name/Videolar",
            OutputPathPolicy.relativePath("FACEBOOK", "page name", MediaBucket.VIDEO)
        )
    }

    @Test
    fun `sanitizes unsafe source path characters`() {
        assertEquals(
            "Download/ReelDrop Pro/Profil/pelin_akil/Fotoğraflar",
            OutputPathPolicy.relativePath("INSTAGRAM_PROFILE", "pelin/akil", MediaBucket.PHOTO)
        )
    }
}
