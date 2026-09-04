package com.atmaca.gallery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DragAutoScrollRegressionTest {
    @Test
    fun `drag near top asks for upward scroll`() {
        assertTrue(dragAutoScrollDelta(pointerY = 5f, viewportHeight = 1000f, edgePx = 120f) < 0f)
    }

    @Test
    fun `drag near bottom asks for downward scroll`() {
        assertTrue(dragAutoScrollDelta(pointerY = 995f, viewportHeight = 1000f, edgePx = 120f) > 0f)
    }

    @Test
    fun `drag in middle does not auto scroll`() {
        assertEquals(0f, dragAutoScrollDelta(pointerY = 500f, viewportHeight = 1000f, edgePx = 120f), 0f)
    }
}
