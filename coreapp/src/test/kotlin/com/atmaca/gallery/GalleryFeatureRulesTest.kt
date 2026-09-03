package com.atmaca.gallery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun viewerZoomNeverShrinksBelowFitAndHasGalleryLikeUpperBound() {
        assertEquals(1f, clampViewerScale(0.2f))
        assertEquals(4f, clampViewerScale(20f))
        assertEquals(2.25f, nextDoubleTapScale(1f))
        assertEquals(1f, nextDoubleTapScale(2.5f))
    }

    @Test
    fun pinchFactorTracksFingersDirectlyButRejectsSingleFrameSpikes() {
        assertEquals(1.25f, galleryZoomFactor(1.25f), 0.0001f)
        assertEquals(0.8f, galleryZoomFactor(0.8f), 0.0001f)
        assertEquals(1.35f, galleryZoomFactor(3f), 0.0001f)
        assertEquals(0.72f, galleryZoomFactor(0.1f), 0.0001f)
    }

    @Test
    fun zoomKeepsFingerFocusAnchoredInsteadOfJumpingFromCenter() {
        val offset = zoomOffsetAroundFocus(
            oldOffset = 0f,
            focusFromCenter = 200f,
            oldScale = 1f,
            newScale = 2f
        )
        assertEquals(-200f, offset, 0.001f)
        assertEquals(50f, zoomOffsetAroundFocus(50f, 0f, 1f, 2f), 0.001f)
    }

    @Test
    fun panIsClampedSoPhotoCannotBeDraggedPastViewport() {
        val bounds = viewerPanBounds(
            viewportWidth = 1080f,
            viewportHeight = 1920f,
            imageWidth = 1080f,
            imageHeight = 1080f,
            scale = 2f,
            rotation = 0f
        )
        assertEquals(540f, bounds.maxX, 0.001f)
        assertEquals(120f, bounds.maxY, 0.001f)
        assertEquals(540f, clampViewerOffset(900f, bounds.maxX), 0.001f)
        assertEquals(-120f, clampViewerOffset(-400f, bounds.maxY), 0.001f)
    }

    @Test
    fun viewerControlsHideWheneverPhotoIsTransformed() {
        assertTrue(shouldShowViewerControls(scale = 1f, gestureActive = false))
        assertFalse(shouldShowViewerControls(scale = 1.2f, gestureActive = false))
        assertFalse(shouldShowViewerControls(scale = 1f, gestureActive = true))
    }

    @Test
    fun pagerIsEnabledOnlyAtFitScale() {
        assertTrue(shouldEnablePager(1f))
        assertFalse(shouldEnablePager(1.01f))
        assertFalse(shouldEnablePager(2f))
    }

    @Test
    fun viewerRotationAdvancesByQuarterTurns() {
        assertEquals(90f, nextQuarterRotation(0f))
        assertEquals(0f, nextQuarterRotation(270f))
    }

    @Test
    fun freeRotationIsNormalizedAndCanMoveBothDirections() {
        assertEquals(15f, normalizeViewerRotation(375f), 0.001f)
        assertEquals(345f, normalizeViewerRotation(-15f), 0.001f)
        assertEquals(42.5f, applyViewerRotationDelta(40f, 2.5f), 0.001f)
    }

    @Test
    fun screenshotCaptureAlwaysHidesViewerChrome() {
        assertFalse(shouldRenderViewerChrome(captureInProgress = true, controlsVisible = true, scale = 1f, gestureActive = false))
        assertTrue(shouldRenderViewerChrome(captureInProgress = false, controlsVisible = true, scale = 1f, gestureActive = false))
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
