package com.atmaca.reeldroppro.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaTypePolicyTest {
    @Test
    fun `maps common extensions to photo and video`() {
        assertEquals(MediaKind.PHOTO, MediaTypePolicy.fromExtension("jpg"))
        assertEquals(MediaKind.VIDEO, MediaTypePolicy.fromExtension("mp4"))
    }
}
