package com.atmaca.gallery

import org.junit.Assert.assertEquals
import org.junit.Test

class ViewerRotatedFitContractTest {
    @Test
    fun quarterTurnFitsUsingRotatedPhotoBounds() {
        val fitted = viewerImageRenderSize(
            viewportWidth = 1080f,
            viewportHeight = 1920f,
            imageWidth = 1920f,
            imageHeight = 1080f,
            rotation = 90f
        )

        assertEquals(1920f, fitted.width, 0.5f)
        assertEquals(1080f, fitted.height, 0.5f)
    }
}
