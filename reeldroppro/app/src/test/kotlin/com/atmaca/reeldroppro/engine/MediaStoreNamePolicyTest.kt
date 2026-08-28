package com.atmaca.reeldroppro.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaStoreNamePolicyTest {
    @Test
    fun `preserves extension and stable id for media store display name`() {
        assertEquals("title_[id123].jpg", MediaStoreNamePolicy.displayName("title", "id123", "jpg"))
    }
}
