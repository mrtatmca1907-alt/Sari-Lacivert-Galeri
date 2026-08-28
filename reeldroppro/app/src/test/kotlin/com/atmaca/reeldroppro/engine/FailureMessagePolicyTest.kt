package com.atmaca.reeldroppro.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FailureMessagePolicyTest {
    @Test
    fun `specific stderr is surfaced instead of generic exit code`() {
        val text = FailureMessagePolicy.message(1, "HTTP Error 429: Too Many Requests")
        assertTrue(text.contains("429"))
        assertFalse(text.contains("hata kodu: 1", ignoreCase = true))
    }
}
