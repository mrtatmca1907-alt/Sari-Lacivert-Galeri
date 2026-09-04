package com.atmaca.gallery

import org.junit.Assert.assertEquals
import org.junit.Test

class FullQualityViewerContractTest {
    @Test
    fun `viewer decode uses original resolution instead of viewport sampling`() {
        assertEquals(1, calculateViewerDecodeSample(12000, 9000, 1080, 2400))
        assertEquals(1, calculateViewerDecodeSample(8000, 6000, 1080, 2400))
        assertEquals(1, calculateViewerDecodeSample(4000, 3000, 1080, 2400))
    }
}
