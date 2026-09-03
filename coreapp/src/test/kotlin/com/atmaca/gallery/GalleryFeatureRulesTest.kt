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
    @Test fun personCropExpandsFaceBoxToIncludeUpperBodyAndClampsToPhoto() { assertEquals(IntCropRect(260,80,740,840), personCropBounds(1000,1000,400,200,600,400)); assertEquals(IntCropRect(0,0,210,340), personCropBounds(500,500,40,20,140,120)) }
    @Test fun oneFingerSwipeAtFitIsLeftForPagerButTransformedPhotoConsumesPan() { assertFalse(shouldPhotoConsumeGesture(pointerCount=1, scale=1f, rotation=0f)); assertTrue(shouldPhotoConsumeGesture(pointerCount=2, scale=1f, rotation=0f)); assertTrue(shouldPhotoConsumeGesture(pointerCount=1, scale=1.5f, rotation=0f)); assertTrue(shouldPhotoConsumeGesture(pointerCount=1, scale=1f, rotation=15f)) }
    @Test fun parentTransformStateIsCommittedOnlyWhenGestureEnds() { assertFalse(shouldCommitViewerTransform(false)); assertTrue(shouldCommitViewerTransform(true)) }
    @Test fun homeHasOnlyMediaAlbumsSettings() { assertEquals(listOf(HomeSection.MEDIA,HomeSection.ALBUMS,HomeSection.SETTINGS),homeSections()) }
    @Test fun thumbnailNameUsesMediaDisplayName() { assertEquals("denizcakir_84.jpg",mediaNameOverlay("denizcakir_84.jpg")) }
    @Test fun mediaFilterSeparatesPhotosVideosAndAll() { assertTrue(mediaFilterAccepts(false,MediaFilter.ALL)); assertTrue(mediaFilterAccepts(true,MediaFilter.ALL)); assertTrue(mediaFilterAccepts(false,MediaFilter.PHOTOS)); assertFalse(mediaFilterAccepts(true,MediaFilter.PHOTOS)); assertTrue(mediaFilterAccepts(true,MediaFilter.VIDEOS)); assertFalse(mediaFilterAccepts(false,MediaFilter.VIDEOS)) }
    @Test fun settingsExposeStableSortAndFilterChoices() { assertEquals(listOf("Tümü","Fotoğraflar","Videolar","GIF'ler","RAW resimler","SVG'ler"),mediaFilterLabels()); assertEquals(listOf("Ad","Yol","Boyut","Son değiştirilme","Alınan tarih","Rastgele"),mediaSortLabels()) }
    @Test fun doubleTapNeedsTwoNearbyTapsInsideTheTimeWindow() { assertTrue(isViewerDoubleTap(1000L,1240L,18f,300L,80f)); assertFalse(isViewerDoubleTap(1000L,1400L,18f,300L,80f)); assertFalse(isViewerDoubleTap(1000L,1240L,120f,300L,80f)) }
    @Test fun doubleTapInActiveViewerMustZoomOrResetNotToggleChrome() { assertEquals(ViewerTapAction.ZOOM_RESET, viewerDoubleTapAction()) }
    @Test fun viewportDecodeReducesHugeImagesButKeepsNormalPhotosSharp() { assertEquals(3,calculateViewerDecodeSample(12000,9000,1080,1920)); assertEquals(1,calculateViewerDecodeSample(4000,3000,1080,1920)); assertEquals(1,calculateViewerDecodeSample(0,0,1080,1920)) }
    @Test fun completeSettingsContainRecycleSlideshowAndAllThreeTools() { assertEquals(listOf("Geri Dönüşüm Kutusu","Slayt gösterisi","Akıllı Kişi Kırpma","Görsel Paketleyici","Video Kareleri"),completeSettingsEntries()) }
    @Test fun slideshowIntervalIsAlwaysSafe() { assertEquals(1,clampSlideshowSeconds(0)); assertEquals(5,clampSlideshowSeconds(5)); assertEquals(30,clampSlideshowSeconds(80)) }
    @Test fun slideshowPrefetchesUpcomingPagesWithoutRepeatingCurrent() { assertEquals(listOf(4,5), slideshowPrefetchIndices(current=3,count=8,loop=true,ahead=2)); assertEquals(listOf(0,1), slideshowPrefetchIndices(current=7,count=8,loop=true,ahead=2)); assertEquals(listOf(7), slideshowPrefetchIndices(current=6,count=8,loop=false,ahead=2)); assertEquals(emptyList<Int>(), slideshowPrefetchIndices(current=0,count=1,loop=true,ahead=2)) }
    @Test fun packagerCreatesStableBatchFolders() { assertEquals("Pictures/ATMACA Paketler/Paket_0001/",packageBatchPath(1)); assertEquals("Pictures/ATMACA Paketler/Paket_0012/",packageBatchPath(12)) }
    @Test fun videoFramesUseOneSecondDefaultCadence() { assertEquals(1000L,frameIntervalMs(1)); assertEquals(500L,frameIntervalMs(2)) }
}
