package com.atmaca.reeldroppro.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class RetryCapTest {
    @Test
    fun `backoff never exceeds five minutes`() {
        assertEquals(300_000L, BackoffJitter.cap(900_000L))
    }
}
