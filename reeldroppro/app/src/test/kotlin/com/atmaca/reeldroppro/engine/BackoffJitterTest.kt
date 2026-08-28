package com.atmaca.reeldroppro.engine

import org.junit.Assert.assertTrue
import org.junit.Test

class BackoffJitterTest {
    @Test
    fun `jitter stays within bounded range`() {
        val base = 20_000L
        val jittered = BackoffJitter.apply(base, seed = 7L)
        assertTrue(jittered in 16_000L..24_000L)
    }
}
