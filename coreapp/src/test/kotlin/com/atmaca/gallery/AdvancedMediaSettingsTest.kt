package com.atmaca.gallery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvancedMediaSettingsTest {
    @Test fun advancedFilterLabelsMatchReferenceSet() {
        assertEquals(
            listOf("Tümü", "Fotoğraflar", "Videolar", "GIF'ler", "RAW resimler", "SVG'ler"),
            mediaFilterLabels()
        )
    }

    @Test fun gifRawAndSvgFiltersUseMimeTypeWithoutConfusingVideos() {
        assertTrue(mediaFilterAccepts(false, "image/gif", MediaFilter.GIF))
        assertTrue(mediaFilterAccepts(false, "image/x-adobe-dng", MediaFilter.RAW))
        assertTrue(mediaFilterAccepts(false, "image/svg+xml", MediaFilter.SVG))
        assertFalse(mediaFilterAccepts(true, "video/mp4", MediaFilter.GIF))
        assertFalse(mediaFilterAccepts(false, "image/jpeg", MediaFilter.RAW))
    }

    @Test fun advancedSortLabelsMatchReferenceSet() {
        assertEquals(
            listOf("Ad", "Yol", "Boyut", "Son değiştirilme", "Alınan tarih", "Rastgele"),
            mediaSortLabels()
        )
        assertEquals(
            listOf(MediaSort.NAME, MediaSort.PATH, MediaSort.SIZE, MediaSort.MODIFIED, MediaSort.TAKEN, MediaSort.RANDOM),
            MediaSort.entries
        )
    }

    @Test fun sortDirectionCanReverseAnyStableOrderedResult() {
        assertEquals(listOf("a", "b", "c"), applySortDirection(listOf("a", "b", "c"), SortDirection.ASCENDING))
        assertEquals(listOf("c", "b", "a"), applySortDirection(listOf("a", "b", "c"), SortDirection.DESCENDING))
        assertEquals(listOf("Artan", "Azalan"), sortDirectionLabels())
    }
}
