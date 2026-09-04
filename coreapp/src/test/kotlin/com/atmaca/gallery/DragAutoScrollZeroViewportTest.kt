package com.atmaca.gallery

import org.junit.Assert.assertEquals
import org.junit.Test

class DragAutoScrollZeroViewportTest {
    @Test
    fun `invalid viewport never scrolls`() {
        assertEquals(0f, dragAutoScrollDelta(pointerY = 10f, viewportHeight = 0f, edgePx = 120f), 0f)
    }
}
