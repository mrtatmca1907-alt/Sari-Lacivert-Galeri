package com.atmaca.reeldroppro.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class StoragePressurePolicyTest {
    @Test
    fun `insufficient storage pauses job instead of corrupting output`() {
        assertEquals(StorageDecision.RUN, StoragePressurePolicy.decide(freeBytes = 2_000_000_000L, expectedBytes = 500_000_000L))
        assertEquals(StorageDecision.WAIT_FOR_SPACE, StoragePressurePolicy.decide(freeBytes = 100_000_000L, expectedBytes = 500_000_000L))
    }
}
