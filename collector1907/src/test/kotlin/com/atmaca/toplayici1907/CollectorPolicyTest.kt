package com.atmaca.toplayici1907

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectorPolicyTest {
    private fun p(
        id: Long,
        name: String,
        path: String,
        size: Long = 100L,
        dateAdded: Long = id
    ) = PhotoRecord(
        id = id,
        uri = "content://media/$id",
        name = name,
        size = size,
        relativePath = path,
        dateAdded = dateAdded
    )

    @Test
    fun targetCopyWinsSurvivor() {
        val outside = p(1, "IMG.jpg", "DCIM/Camera/")
        val target = p(2, "IMG (1).jpg", "Pictures/1907/")
        assertEquals(target, CollectorPolicy.chooseSurvivor(listOf(outside, target)))
    }

    @Test
    fun originalLookingNameWinsWhenBothAreOutsideTarget() {
        val original = p(1, "IMG.jpg", "DCIM/Camera/", dateAdded = 20)
        val copy = p(2, "IMG (1).jpg", "Pictures/", dateAdded = 10)
        assertEquals(original, CollectorPolicy.chooseSurvivor(listOf(copy, original)))
    }

    @Test
    fun sameNameDifferentContentCanReceiveUniqueTargetNames() {
        val reserved = mutableSetOf("img.jpg")
        assertEquals("IMG_2.jpg", CollectorPolicy.uniqueName("IMG.jpg", reserved))
        assertEquals("IMG_3.jpg", CollectorPolicy.uniqueName("IMG.jpg", reserved))
    }

    @Test
    fun targetDetectionAcceptsMissingTrailingSlash() {
        assertTrue(CollectorPolicy.isTarget(p(1, "x.jpg", "Pictures/1907")))
    }
}
