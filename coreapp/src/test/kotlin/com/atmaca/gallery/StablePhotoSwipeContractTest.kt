package com.atmaca.gallery

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class StablePhotoSwipeContractTest {
    private fun source(): String = File("src/main/kotlin/com/atmaca/gallery/StablePhotoPage.kt").readText()

    @Test
    fun `single finger at fit scale stays owned by horizontal pager`() {
        val text = source()
        assertTrue(text.contains("val owns = shouldPhotoConsumeGesture(pressed, scale, localRotation)"))
        assertTrue(text.contains("val transformFrame = owns"))
    }

    @Test
    fun `release resets zoom pan and temporary rotation`() {
        val text = source()
        val release = text.substringAfter("if (transformed) {").substringBefore("} else if (!moved)")
        assertTrue(release.contains("localRotation = releasedViewerRotation(rotation)"))
        assertTrue(release.contains("resetZoomOnly()"))
        assertTrue(release.contains("onGestureActive(false)"))
    }
}
