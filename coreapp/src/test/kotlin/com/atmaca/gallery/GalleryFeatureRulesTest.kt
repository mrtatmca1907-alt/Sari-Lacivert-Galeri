package com.atmaca.gallery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GalleryFeatureRulesTest {
    @Test fun albumsAreGroupedByRelativePath() { val items=listOf(MediaMeta(1,"Pictures/A/",10,"image/jpeg"),MediaMeta(2,"Pictures/B/",20,"image/jpeg"),MediaMeta(3,"Pictures/A/",30,"video/mp4")); val albums=groupAlbums(items); assertEquals(listOf("Pictures/A/","Pictures/B/"),albums.map{it.relativePath}); assertEquals(2,albums.first().count) }
    @Test fun duplicateCandidatesRequireSamePositiveSize() { val items=listOf(MediaMeta(1,"Pictures/",100,"image/jpeg"),MediaMeta(2,"DCIM/",100,"image/jpeg"),MediaMeta(3,"DCIM/",200,"image/jpeg"),MediaMeta(4,"Movies/",0,"video/mp4")); val groups=duplicateCandidateGroups(items); assertEquals(1,groups.size); assertEquals(listOf(1L,2L),groups.single().map{it.id}) }
    @Test fun targetPathsAlwaysEndWithSlashAndNeverStartWithSlash() { assertEquals("Pictures/ATMACA/",normalizeRelativePath("/Pictures/ATMACA")); assertEquals("DCIM/Camera/",normalizeRelativePath("DCIM/Camera/")); assertTrue(normalizeRelativePath("  /Movies/Test// ").endsWith('/')) }
    @Test fun viewerZoomNeverShrinksBelowFitAndHasGalleryLikeUpperBound() { assertEquals(1f,clampViewerScale(0.2f)); assertEquals(4f,clampViewerScale(20f)); assertEquals(2.25f,nextDoubleTapScale(1f)); assertEquals(1f,nextDoubleTapScale(2.5f)) }
    @Test fun pinchFactorTracksFingersDirectlyButRejectsSingleFrameSpikes() { assertEquals(1.25f,galleryZoomFactor(1.25f),0.0001f); assertEquals(0.8f,galleryZoomFactor(0.8f),0.0001f); assertEquals(1.35f,galleryZoomFactor(3f),0.0001f); assertEquals(0.72f,galleryZoomFactor(0.1f),0.0001f) }
    @Test fun zoomKeepsFingerFocusAnchoredInsteadOfJumpingFromCenter() { assertEquals(-200f,zoomOffsetAroundFocus(0f,200f,1f,2f),0.001f); assertEquals(50f,zoomOffsetAroundFocus(50f,0f,1f,2f),0.001f) }
    @Test fun panIsClampedSoPhotoCannotBeDraggedPastViewport() { val bounds=viewerPanBounds(1080f,1920f,1080f,1080f,2f,0f); assertEquals(540f,bounds.maxX,0.001f); assertEquals(120f,bounds.maxY,0.001f); assertEquals(540f,clampViewerOffset(900f,bounds.maxX),0.001f); assertEquals(-120f,clampViewerOffset(-400f,bounds.maxY),0.001f) }
    @Test fun viewerControlsHideWheneverPhotoIsTransformed() { assertTrue(shouldShowViewerControls(1f,false)); assertFalse(shouldShowViewerControls(1.2f,false)); assertFalse(shouldShowViewerControls(1f,true)) }
    @Test fun pagerIsEnabledOnlyAtFitScale() { assertTrue(shouldEnablePager(1f)); assertFalse(shouldEnablePager(1.01f)); assertFalse(shouldEnablePager(2f)) }
    @Test fun viewerRotationAdvancesByQuarterTurns() { assertEquals(90f,nextQuarterRotation(0f)); assertEquals(0f,nextQuarterRotation(270f)) }
    @Test fun freeRotationIsNormalizedAndCanMoveBothDirections() { assertEquals(15f,normalizeViewerRotation(375f),0.001f); assertEquals(345f,normalizeViewerRotation(-15f),0.001f); assertEquals(42.5f,applyViewerRotationDelta(40f,2.5f),0.001f) }
    @Test fun screenshotCaptureAlwaysHidesViewerChrome() { assertFalse(shouldRenderViewerChrome(true,true,1f,false)); assertTrue(shouldRenderViewerChrome(false,true,1f,false)) }
    @Test fun cropRectIsNormalizedAndClampedToImageBounds() { val crop=normalizedCropRect(-0.2f,0.1f,1.3f,0.9f); assertEquals(NormalizedCropRect(0f,0.1f,1f,0.9f),crop); assertTrue(crop.width>0f); assertTrue(crop.height>0f) }
    @Test fun cropAspectRatiosAreStable() { assertEquals(1f,CropRatio.SQUARE.ratio,0.0001f); assertEquals(4f/3f,CropRatio.FOUR_THREE.ratio,0.0001f); assertEquals(16f/9f,CropRatio.SIXTEEN_NINE.ratio,0.0001f) }

    @Test fun oneFingerSwipeAtFitIsLeftForPagerButTransformedPhotoConsumesPan() {
        assertFalse(shouldPhotoConsumeGesture(pointerCount=1, scale=1f, rotation=0f))
        assertTrue(shouldPhotoConsumeGesture(pointerCount=2, scale=1f, rotation=0f))
        assertTrue(shouldPhotoConsumeGesture(pointerCount=1, scale=1.5f, rotation=0f))
        assertTrue(shouldPhotoConsumeGesture(pointerCount=1, scale=1f, rotation=15f))
    }

    @Test fun parentTransformStateIsCommittedOnlyWhenGestureEnds() {
        assertFalse(shouldCommitViewerTransform(gestureEnded=false))
        assertTrue(shouldCommitViewerTransform(gestureEnded=true))
    }

    @Test fun photoOptionsLiveInTopMenuAndBottomBarStaysMinimal() {
        assertEquals(
            listOf("Kırp", "Screenshot modu", "Ad değiştir", "Çöpe taşı / sil"),
            viewerMenuEntries(isVideo = false, screenshotMode = false)
        )
        assertEquals(listOf("Paylaş", "Geri"), viewerBottomActions(isVideo = false))
    }

    @Test fun videoTopMenuDoesNotExposePhotoOnlyTools() {
        assertEquals(
            listOf("Ad değiştir", "Çöpe taşı / sil"),
            viewerMenuEntries(isVideo = true, screenshotMode = false)
        )
    }

    @Test fun homeHasOnlyMediaAlbumsSettings() {
        assertEquals(
            listOf(HomeSection.MEDIA, HomeSection.ALBUMS, HomeSection.SETTINGS),
            homeSections()
        )
    }

    @Test fun thumbnailNameUsesMediaDisplayName() {
        assertEquals("denizcakir_84.jpg", mediaNameOverlay("denizcakir_84.jpg"))
    }

    @Test fun mediaFilterSeparatesPhotosVideosAndAll() {
        assertTrue(mediaFilterAccepts(isVideo = false, MediaFilter.ALL))
        assertTrue(mediaFilterAccepts(isVideo = true, MediaFilter.ALL))
        assertTrue(mediaFilterAccepts(isVideo = false, MediaFilter.PHOTOS))
        assertFalse(mediaFilterAccepts(isVideo = true, MediaFilter.PHOTOS))
        assertTrue(mediaFilterAccepts(isVideo = true, MediaFilter.VIDEOS))
        assertFalse(mediaFilterAccepts(isVideo = false, MediaFilter.VIDEOS))
    }

    @Test fun settingsExposeStableSortAndFilterChoices() {
        assertEquals(listOf("Tümü", "Fotoğraflar", "Videolar"), mediaFilterLabels())
        assertEquals(listOf("En yeni", "En eski", "Ada göre"), mediaSortLabels())
    }
}
