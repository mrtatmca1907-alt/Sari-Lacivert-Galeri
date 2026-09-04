package com.atmaca.gallery

import org.junit.Assert.assertTrue
import org.junit.Test

class DragAutoScrollStrengthTest {
    @Test
    fun `closer to edge scrolls at least as strongly`() {
        val near = kotlin.math.abs(dragAutoScrollDelta(pointerY = 5f, viewportHeight = 1000f, edgePx = 120f))
        val farther = kotlin.math.abs(dragAutoScrollDelta(pointerY = 90f, viewportHeight = 1000f, edgePx = 120f))
        assertTrue(near >= farther)
    }
}
