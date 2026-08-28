package com.atmaca.gallery

import org.junit.Assert.assertEquals
import org.junit.Test

class ZoomStateTest {
    @Test fun doubleTapTogglesBetweenResetAndZoom() {
        val state = ZoomState()
        assertEquals(2.5f, state.onDoubleTap(), 0.001f)
        assertEquals(1f, state.onDoubleTap(), 0.001f)
    }

    @Test fun pinchIsClampedAndResetReturnsOne() {
        val state = ZoomState()
        assertEquals(5f, state.onScale(10f), 0.001f)
        assertEquals(1f, state.reset(), 0.001f)
    }
}
