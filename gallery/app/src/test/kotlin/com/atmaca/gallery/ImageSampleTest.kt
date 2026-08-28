package com.atmaca.gallery

import org.junit.Assert.assertEquals
import org.junit.Test

class ImageSampleTest {
    @Test fun sample_is_one_when_source_fits_target() {
        assertEquals(1, ImageSample.compute(1000, 1000, 1000, 1000))
    }
    @Test fun sample_uses_power_of_two() {
        assertEquals(2, ImageSample.compute(4000, 3000, 1600, 1200))
        assertEquals(4, ImageSample.compute(8000, 6000, 1600, 1200))
    }
    @Test fun invalid_dimensions_fall_back_to_one() {
        assertEquals(1, ImageSample.compute(0, 0, 1600, 1200))
    }
}
