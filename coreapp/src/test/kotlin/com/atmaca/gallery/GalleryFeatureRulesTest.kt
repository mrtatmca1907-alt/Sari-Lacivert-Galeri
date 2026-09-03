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
}
