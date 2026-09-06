package com.atmaca.gallery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompleteViewerRulesTest {
    @Test fun photoViewerChromeMatchesApprovedReferenceLayout() {
        assertEquals(listOf("Geri", "Ad", "Döndür", "Düzenle", "Diğer"), photoViewerTopActions())
        assertEquals(listOf("Favori", "Düzenle", "Paylaş", "Çöp", "Bilgi", "Slayt"), photoViewerBottomActions())
    }

    @Test fun doubleTapZoomsAndSecondDoubleTapResets() {
        assertEquals(ViewerTapAction.ZOOM_RESET, viewerDoubleTapAction())
        assertEquals(2.25f, nextDoubleTapScale(1f), 0.0001f)
        assertEquals(1f, nextDoubleTapScale(2.25f), 0.0001f)
    }

    @Test fun slideshowAdvancesAndLoopsDeterministically() {
        val controller = SlideshowController(count = 5, loop = true)
        assertEquals(1, controller.nextIndex(0))
        assertEquals(0, controller.nextIndex(4))
        assertTrue(controller.canAdvance(4))
    }

    @Test fun nonLoopingSlideshowStopsAtLastItem() {
        val controller = SlideshowController(count = 3, loop = false)
        assertEquals(2, controller.nextIndex(2))
        assertFalse(controller.canAdvance(2))
    }
}
