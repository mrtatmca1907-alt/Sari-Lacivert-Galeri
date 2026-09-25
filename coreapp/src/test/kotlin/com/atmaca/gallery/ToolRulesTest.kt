package com.atmaca.gallery

import org.junit.Assert.assertEquals
import org.junit.Test

class ToolRulesTest {
    @Test fun packageBatchSizesPreserveEveryItem() {
        assertEquals(listOf(50, 50, 1), packageBatchSizes(101, 50))
        assertEquals(emptyList<Int>(), packageBatchSizes(0, 50))
    }

    @Test fun personCropNamesNeverOverwriteSource() {
        assertEquals("photo_person_1.jpg", personCropName("photo.jpg", 1))
        assertEquals("photo_person_12.png", personCropName("photo.png", 12))
    }

    @Test fun videoFramesUseHumanSequenceAndDuration() {
        assertEquals(10, frameCount(10_000L, 1_000L))
        assertEquals("tatil 3.jpg", frameName("tatil.mp4", 3))
        assertEquals("clip 1.jpg", frameName("clip", 1))
    }
}
