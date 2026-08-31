package com.atmaca.toplayici1907

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsolidationPlannerTest {
    private fun p(
        id: Long,
        name: String,
        path: String = "DCIM/Camera/",
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
    fun equalSizeAndHashProducesOneDuplicate() {
        val a = p(1, "A.jpg")
        val b = p(2, "A (1).jpg")
        val plan = ConsolidationPlanner.plan(listOf(a, b)) { "same" }
        assertEquals(1, plan.survivors.size)
        assertEquals(1, plan.duplicates.size)
        assertEquals(1, plan.duplicateGroups.size)
        assertEquals(a, plan.duplicateGroups.single().survivor)
        assertEquals(listOf(b), plan.duplicateGroups.single().duplicates)
    }

    @Test
    fun equalSizeDifferentHashPreservesBoth() {
        val a = p(1, "A.jpg")
        val b = p(2, "A.jpg", path = "Pictures/")
        val plan = ConsolidationPlanner.plan(listOf(a, b)) { if (it.id == 1L) "x" else "y" }
        assertEquals(2, plan.survivors.size)
        assertTrue(plan.duplicates.isEmpty())
        assertTrue(plan.duplicateGroups.isEmpty())
    }

    @Test
    fun targetPhotoWinsInsideExactDuplicateGroup() {
        val outside = p(1, "A.jpg")
        val target = p(2, "A copy.jpg", path = "Pictures/1907/")
        val plan = ConsolidationPlanner.plan(listOf(outside, target)) { "same" }
        assertEquals(listOf(target), plan.survivors)
        assertEquals(listOf(outside), plan.duplicates)
        assertEquals(target, plan.duplicateGroups.single().survivor)
    }

    @Test
    fun hashFailureIsNeverDeleted() {
        val readable = p(1, "A.jpg")
        val unreadable = p(2, "A (1).jpg")
        val plan = ConsolidationPlanner.plan(listOf(readable, unreadable)) {
            if (it.id == unreadable.id) null else "same"
        }
        assertTrue(plan.survivors.contains(unreadable))
        assertTrue(plan.duplicates.isEmpty())
        assertEquals(listOf(unreadable), plan.hashFailures)
        assertTrue(plan.duplicateGroups.isEmpty())
    }

    @Test
    fun uniqueSizeSkipsHashing() {
        val a = p(1, "A.jpg", size = 100)
        val b = p(2, "B.jpg", size = 200)
        var calls = 0
        val plan = ConsolidationPlanner.plan(listOf(a, b)) {
            calls++
            "unused"
        }
        assertEquals(0, calls)
        assertEquals(2, plan.survivors.size)
    }
}
