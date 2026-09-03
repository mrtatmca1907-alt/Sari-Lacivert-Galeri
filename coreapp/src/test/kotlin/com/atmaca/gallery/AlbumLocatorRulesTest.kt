package com.atmaca.gallery

import org.junit.Assert.assertEquals
import org.junit.Test

class AlbumLocatorRulesTest {
    @Test fun albumLocatorPrefersBucketThenRealPathThenBucketName() {
        assertEquals(AlbumLocator.Bucket(42L), albumLocator("", 42L, "Camera"))
        assertEquals(AlbumLocator.Path("DCIM/Camera/"), albumLocator("DCIM/Camera/", 0L, "Camera"))
        assertEquals(AlbumLocator.Name("WhatsApp Images"), albumLocator("", 0L, "WhatsApp Images"))
    }
}
