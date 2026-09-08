package com.sarilacivert.galeri.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class GestureMathTest {
    @Test
    fun angleDeltaWrapsAcrossMinus180And180() {
        assertEquals(2f, GestureMath.shortestAngleDelta(179f, -179f), 0.001f)
        assertEquals(-2f, GestureMath.shortestAngleDelta(-179f, 179f), 0.001f)
    }

    @Test
    fun angleDeltaKeepsOrdinaryRotation() {
        assertEquals(30f, GestureMath.shortestAngleDelta(10f, 40f), 0.001f)
        assertEquals(-45f, GestureMath.shortestAngleDelta(20f, -25f), 0.001f)
    }

    @Test
    fun normalizeRotationKeepsValueInsideCircle() {
        assertEquals(5f, GestureMath.normalizeRotation(365f), 0.001f)
        assertEquals(355f, GestureMath.normalizeRotation(-5f), 0.001f)
    }
}
