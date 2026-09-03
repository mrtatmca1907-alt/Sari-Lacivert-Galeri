package com.atmaca.gallery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GalleryFeatureRulesTest {
    @Test
    fun albumsAreGroupedByRelativePath() {
        val items = listOf(
            MediaMeta(1, "Pictures/A/", 10, "image/jpeg"),
            MediaMeta(2, "Pictures/B/", 20, "image/jpeg"),
            MediaMeta(3, "Pictures/A/", 30, "video/mp4")
        )

        val albums = groupAlbums(items)

        assertEquals(listOf("Pictures/A/", "Pictures/B/"), albums.map { it.relativePath })
        assertEquals(2, albums.first().count)
    }

    @Test
    fun duplicateCandidatesRequireSamePositiveSize() {
        val items = listOf(
            MediaMeta(1, "Pictures/", 100, "image/jpeg"),
            MediaMeta(2, "DCIM/", 100, "image/jpeg"),
            MediaMeta(3, "DCIM/", 200, "image/jpeg"),
            MediaMeta(4, "Movies/", 0, "video/mp4")
        )

        val groups = duplicateCandidateGroups(items)

        assertEquals(1, groups.size)
        assertEquals(listOf(1L, 2L), groups.single().map { it.id })
    }

    @Test
    fun targetPathsAlwaysEndWithSlashAndNeverStartWithSlash() {
        assertEquals("Pictures/ATMACA/", normalizeRelativePath("/Pictures/ATMACA"))
        assertEquals("DCIM/Camera/", normalizeRelativePath("DCIM/Camera/"))
        assertTrue(normalizeRelativePath("  /Movies/Test// ").endsWith('/'))
    }

    @Test
    fun viewerZoomIsClampedAndDoubleTapTogglesUsefulZoom() {
        assertEquals(1f, clampViewerScale(0.2f))
        assertEquals(8f, clampViewerScale(20f))
        assertEquals(2.5f, nextDoubleTapScale(1f))
        assertEquals(1f, nextDoubleTapScale(3f))
    }

    @Test
    fun viewerRotationAdvancesByQuarterTurns() {
        assertEquals(90f, nextQuarterRotation(0f))
        assertEquals(0f, nextQuarterRotation(270f))
    }

    @Test
    fun cropRectIsNormalizedAndClampedToImageBounds() {
        val crop = normalizedCropRect(-0.2f, 0.1f, 1.3f, 0.9f)
        assertEquals(NormalizedCropRect(0f, 0.1f, 1f, 0.9f), crop)
        assertTrue(crop.width > 0f)
        assertTrue(crop.height > 0f)
    }

    @Test
    fun cropAspectRatiosAreStable() {
        assertEquals(1f, CropRatio.SQUARE.ratio, 0.0001f)
        assertEquals(4f / 3f, CropRatio.FOUR_THREE.ratio, 0.0001f)
        assertEquals(16f / 9f, CropRatio.SIXTEEN_NINE.ratio, 0.0001f)
    }
}
