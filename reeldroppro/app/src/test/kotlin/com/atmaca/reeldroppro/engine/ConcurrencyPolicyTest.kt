package com.atmaca.reeldroppro.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class ConcurrencyPolicyTest {
    @Test
    fun `uses bounded concurrency under thermal and low memory pressure`() {
        assertEquals(3, ConcurrencyPolicy.limit(lowMemory = false, thermalSevere = false))
        assertEquals(1, ConcurrencyPolicy.limit(lowMemory = true, thermalSevere = false))
        assertEquals(1, ConcurrencyPolicy.limit(lowMemory = false, thermalSevere = true))
    }
}
