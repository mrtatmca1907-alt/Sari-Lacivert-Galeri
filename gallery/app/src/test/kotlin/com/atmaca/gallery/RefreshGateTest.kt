package com.atmaca.gallery

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RefreshGateTest {
    @Test
    fun `coalesces rapid media changes while a refresh is already running`() {
        val gate = RefreshGate()
        assertTrue(gate.request())
        assertFalse(gate.request())
        assertFalse(gate.request())
        assertTrue(gate.finishAndCheckPending())
        assertFalse(gate.finishAndCheckPending())
    }
}
