package com.atmaca.reeldroppro.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidationPolicyTest {
    @Test
    fun `rejects empty downloads and accepts nonempty matching extension`() {
        assertFalse(ValidationPolicy.valid(sizeBytes = 0, fileName = "x.mp4", expectedExtension = "mp4"))
        assertTrue(ValidationPolicy.valid(sizeBytes = 1024, fileName = "x.mp4", expectedExtension = "mp4"))
    }
}
