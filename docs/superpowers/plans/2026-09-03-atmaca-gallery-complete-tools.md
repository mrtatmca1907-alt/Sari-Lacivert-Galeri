# ATMACA Gallery Complete Tools Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the approved stable ATMACA gallery release with unified media browsing, full settings, recycle bin, bulk operations, slideshow, accepted Eagle-like photo viewer controls, and isolated Smart Person Crop / Image Packager / Video Frames tools.

**Architecture:** Keep the existing MediaStore/Compose gallery core and accepted photo gesture engine. Split UI state/rules, settings persistence, recycle-bin operations, bulk file jobs, slideshow, and heavy tool workers into focused components so expensive or failing jobs cannot block or crash the viewer. Use MediaStore/SAF for shared media, coroutines/WorkManager for background work, and viewport-aware decoding/caching for images.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Android MediaStore/SAF, Media3 ExoPlayer, Kotlin coroutines, WorkManager, AndroidX DataStore or SharedPreferences where already established, on-device vision model for person crop, MediaMetadataRetriever/MediaCodec-compatible frame extraction.

**Spec:** `docs/superpowers/specs/2026-09-03-atmaca-gallery-complete-tools-design.md`

## Global Constraints

- Primary target: Android 13 / HiOS 13 on Tecno Spark 10 Pro.
- Stability and responsiveness outrank APK size.
- Preserve the currently accepted pinch zoom and free-rotation feel.
- Keep video playback isolated from photo-viewer changes.
- Do not copy/decompile proprietary source from reference APKs; reimplement observed behavior natively.
- Never silently permanently delete when the user expects trash/restore behavior.
- Heavy work must not run on the Compose main thread.
- CI uses the project's Android API 37 toolchain.

---

### Task 1: Gallery navigation and media-grid model

**Files:**
- Modify: `coreapp/src/main/kotlin/com/atmaca/gallery/GalleryApp.kt`
- Modify: `coreapp/src/main/kotlin/com/atmaca/gallery/FeatureRules.kt`
- Test: `coreapp/src/test/kotlin/com/atmaca/gallery/GalleryFeatureRulesTest.kt`

**Interfaces:**
- Produces: `enum class HomeSection { MEDIA, ALBUMS, SETTINGS }`
- Produces: `fun homeSections(): List<HomeSection>`
- Produces: `fun mediaNameOverlay(name: String): String`

- [ ] **Step 1: Write failing tests**

```kotlin
@Test fun homeHasOnlyMediaAlbumsSettings() {
    assertEquals(listOf(HomeSection.MEDIA, HomeSection.ALBUMS, HomeSection.SETTINGS), homeSections())
}

@Test fun thumbnailNameUsesMediaDisplayName() {
    assertEquals("denizcakir_84.jpg", mediaNameOverlay("denizcakir_84.jpg"))
}
```

- [ ] **Step 2: Run the tests and confirm RED**

Run: `gradle :coreapp:testDebugUnitTest --tests com.atmaca.gallery.GalleryFeatureRulesTest`
Expected: FAIL because the new navigation/rule functions do not exist.

- [ ] **Step 3: Implement minimal navigation/rules and unified photo+video grid**

Remove permanent PHOTOS/VIDEOS/DUPLICATES/TRASH navigation entries. `MEDIA` displays the repository's mixed photo/video list; albums remain separate. Render `displayName` over each thumbnail near its lower edge with a translucent backing for readability.

- [ ] **Step 4: Run unit tests**

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add coreapp/src/main/kotlin/com/atmaca/gallery/GalleryApp.kt coreapp/src/main/kotlin/com/atmaca/gallery/FeatureRules.kt coreapp/src/test/kotlin/com/atmaca/gallery/GalleryFeatureRulesTest.kt
git commit -m "feat: unify media home navigation"
```

### Task 2: Sort, filter, view and settings persistence

**Files:**
- Create: `coreapp/src/main/kotlin/com/atmaca/gallery/GallerySettings.kt`
- Create: `coreapp/src/main/kotlin/com/atmaca/gallery/SettingsScreen.kt`
- Modify: `coreapp/src/main/kotlin/com/atmaca/gallery/GalleryApp.kt`
- Test: `coreapp/src/test/kotlin/com/atmaca/gallery/GallerySettingsTest.kt`

**Interfaces:**
- Produces: `data class GallerySettings(...)`
- Produces: `enum class SortCriterion { NAME, PATH, SIZE, MODIFIED, TAKEN, RANDOM }`
- Produces: `data class MediaFilter(...)`
- Produces: `GallerySettingsStore.load()` / `save(settings)`

- [ ] **Step 1: Write failing tests for defaults and sort/filter behavior**

```kotlin
@Test fun defaultsMatchApprovedViewerBehavior() {
    val s = GallerySettings.defaults()
    assertTrue(s.allowGestureRotation)
    assertTrue(s.pullToRefresh)
    assertFalse(s.doubleTapOneToOneZoom)
    assertTrue(s.trashInsteadOfDelete)
}

@Test fun photoAndVideoFiltersCanBothRemainEnabled() {
    val f = MediaFilter(images = true, videos = true, gifs = true, raw = true, svg = true)
    assertTrue(f.images && f.videos)
}
```

- [ ] **Step 2: Run tests and confirm RED**

Expected: missing settings types/functions.

- [ ] **Step 3: Implement settings model/store and Settings screen**

Include approved groups: browsing, deep zoom/image quality, fullscreen media, videos, thumbnails/scrolling, file operations, visibility/security placeholders only where real secure flow exists, recycle bin, and tools. Persist all implemented toggles and values.

- [ ] **Step 4: Run tests and Compose compilation**

Run: `gradle :coreapp:testDebugUnitTest :coreapp:compileDebugKotlin`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add coreapp/src/main/kotlin/com/atmaca/gallery/GallerySettings.kt coreapp/src/main/kotlin/com/atmaca/gallery/SettingsScreen.kt coreapp/src/main/kotlin/com/atmaca/gallery/GalleryApp.kt coreapp/src/test/kotlin/com/atmaca/gallery/GallerySettingsTest.kt
git commit -m "feat: add complete gallery settings"
```

### Task 3: Drag selection and bulk actions

**Files:**
- Create: `coreapp/src/main/kotlin/com/atmaca/gallery/SelectionController.kt`
- Modify: `coreapp/src/main/kotlin/com/atmaca/gallery/GalleryApp.kt`
- Test: `coreapp/src/test/kotlin/com/atmaca/gallery/SelectionControllerTest.kt`

**Interfaces:**
- Produces: `fun selectionRange(start: Int, end: Int): IntRange`
- Produces: `class SelectionController`
- Produces actions: select all, copy, move, share, trash.

- [ ] **Step 1: Write failing tests**

```kotlin
@Test fun dragSelectionSelectsEveryCrossedCellOnce() {
    val c = SelectionController()
    c.begin(2)
    c.dragTo(5)
    assertEquals(setOf(2,3,4,5), c.indices)
}

@Test fun reverseDragAlsoSelectsWholeRange() {
    assertEquals(3..7, selectionRange(7, 3))
}
```

- [ ] **Step 2: Verify RED**

- [ ] **Step 3: Implement selection controller and grid gesture integration**

Long-press starts selection. Dragging across cells updates selection without repeated toggles. Selection toolbar includes `Tümünü seç`, `Taşı`, `Kopyala`, `Paylaş`, `Çöpe taşı`.

- [ ] **Step 4: Run tests and compilation**

- [ ] **Step 5: Commit**

```bash
git add coreapp/src/main/kotlin/com/atmaca/gallery/SelectionController.kt coreapp/src/main/kotlin/com/atmaca/gallery/GalleryApp.kt coreapp/src/test/kotlin/com/atmaca/gallery/SelectionControllerTest.kt
git commit -m "feat: add drag selection and bulk actions"
```

### Task 4: Background copy/move engine

**Files:**
- Create: `coreapp/src/main/kotlin/com/atmaca/gallery/FileOperationEngine.kt`
- Modify: `coreapp/src/main/kotlin/com/atmaca/gallery/GalleryActions.kt`
- Test: `coreapp/src/test/kotlin/com/atmaca/gallery/FileOperationEngineTest.kt`

**Interfaces:**
- Produces: `sealed interface FileOperationState`
- Produces: `suspend fun copy(items, destination): FileOperationResult`
- Produces: `suspend fun move(items, destination): FileOperationResult`

- [ ] **Step 1: Write failing state-transition tests**

```kotlin
@Test fun successfulMoveReportsNoSourceLeftBehind() {
    val result = fakeEngine.moveResult(success = 3, failed = 0, sourceDeleted = 3)
    assertEquals(3, result.sourceDeleted)
}
```

- [ ] **Step 2: Verify RED**

- [ ] **Step 3: Implement coroutine-based copy/move with progress**

Use ContentResolver streams/SAF destinations. For move, delete source only after destination write is verified. Surface per-item errors and do not freeze UI.

- [ ] **Step 4: Run unit tests**

- [ ] **Step 5: Commit**

```bash
git add coreapp/src/main/kotlin/com/atmaca/gallery/FileOperationEngine.kt coreapp/src/main/kotlin/com/atmaca/gallery/GalleryActions.kt coreapp/src/test/kotlin/com/atmaca/gallery/FileOperationEngineTest.kt
git commit -m "feat: add stable background copy and move"
```

### Task 5: Full recycle-bin workflow

**Files:**
- Create: `coreapp/src/main/kotlin/com/atmaca/gallery/TrashRepository.kt`
- Create: `coreapp/src/main/kotlin/com/atmaca/gallery/TrashScreen.kt`
- Modify: `coreapp/src/main/kotlin/com/atmaca/gallery/GalleryActions.kt`
- Modify: `coreapp/src/main/kotlin/com/atmaca/gallery/SettingsScreen.kt`
- Test: `coreapp/src/test/kotlin/com/atmaca/gallery/TrashRulesTest.kt`

**Interfaces:**
- Produces: `trash(items)`, `restore(items)`, `permanentDelete(items)`, `emptyTrash()`
- Produces: `trashSizeBytes()` and selected-item count.

- [ ] **Step 1: Write failing tests**

```kotlin
@Test fun emptyTrashRequiresExplicitPermanentDeleteAction() {
    assertEquals(TrashAction.PERMANENT_DELETE_ALL, emptyTrashAction())
}

@Test fun trashScreenOffersRestoreDeleteAndSelectAll() {
    assertEquals(listOf("Tümünü seç", "Geri yükle", "Kalıcı sil", "Çöp kutusunu boşalt"), trashActions())
}
```

- [ ] **Step 2: Verify RED**

- [ ] **Step 3: Implement MediaStore trash batching and Trash screen**

On API levels supporting MediaStore trash, use batched trash/restore consent requests. Avoid one confirmation per file. Legacy fallback never pretends restore is possible. Add total-size calculation off the main thread.

- [ ] **Step 4: Run tests and compilation**

- [ ] **Step 5: Commit**

```bash
git add coreapp/src/main/kotlin/com/atmaca/gallery/TrashRepository.kt coreapp/src/main/kotlin/com/atmaca/gallery/TrashScreen.kt coreapp/src/main/kotlin/com/atmaca/gallery/GalleryActions.kt coreapp/src/main/kotlin/com/atmaca/gallery/SettingsScreen.kt coreapp/src/test/kotlin/com/atmaca/gallery/TrashRulesTest.kt
git commit -m "feat: add complete recycle bin"
```

### Task 6: Photo viewer chrome and double-tap fullscreen

**Files:**
- Modify: `coreapp/src/main/kotlin/com/atmaca/gallery/StablePhotoPage.kt`
- Modify: `coreapp/src/main/kotlin/com/atmaca/gallery/GalleryApp.kt`
- Modify: `coreapp/src/main/kotlin/com/atmaca/gallery/FeatureRules.kt`
- Test: `coreapp/src/test/kotlin/com/atmaca/gallery/GalleryFeatureRulesTest.kt`

**Interfaces:**
- Produces: viewer top actions `Back, Name, Rotate, Edit, More`.
- Produces: viewer bottom actions `Favorite, Edit, Share, Trash, Info, Slideshow`.
- Produces: `doubleTapAction = ToggleChrome` while keeping pinch/free rotation unchanged.

- [ ] **Step 1: Write failing tests**

```kotlin
@Test fun doubleTapTogglesFullscreenChrome() {
    assertEquals(ViewerTapAction.TOGGLE_CHROME, viewerDoubleTapAction())
}

@Test fun viewerBottomActionsMatchReference() {
    assertEquals(listOf("Favori","Düzenle","Paylaş","Çöp","Bilgi","Slayt"), photoViewerBottomActions())
}
```

- [ ] **Step 2: Verify RED**

- [ ] **Step 3: Implement controls without changing accepted transform engine**

Handle double-tap inside the existing single pointer-input engine so no second competing gesture detector returns. Keep one-finger pager swipe at fit and transformed-photo pan ownership. Add blurred/dark background option.

- [ ] **Step 4: Run tests**

- [ ] **Step 5: Commit**

```bash
git add coreapp/src/main/kotlin/com/atmaca/gallery/StablePhotoPage.kt coreapp/src/main/kotlin/com/atmaca/gallery/GalleryApp.kt coreapp/src/main/kotlin/com/atmaca/gallery/FeatureRules.kt coreapp/src/test/kotlin/com/atmaca/gallery/GalleryFeatureRulesTest.kt
git commit -m "feat: complete Eagle-style photo viewer controls"
```

### Task 7: Viewport-aware image decoding and caches

**Files:**
- Modify/Create: `coreapp/src/main/kotlin/com/atmaca/gallery/ImageLoader.kt`
- Modify: `coreapp/src/main/kotlin/com/atmaca/gallery/StablePhotoPage.kt`
- Test: `coreapp/src/test/kotlin/com/atmaca/gallery/ImageLoaderTest.kt`

**Interfaces:**
- Produces: `fun calculateInSampleSize(srcW:Int, srcH:Int, targetW:Int, targetH:Int): Int`
- Produces viewport-aware decode API.

- [ ] **Step 1: Write failing sampling tests**

```kotlin
@Test fun hugeImageIsSampledForPhoneViewport() {
    assertTrue(calculateInSampleSize(12000, 9000, 2160, 2160) >= 2)
}

@Test fun smallImageIsNotUpsampled() {
    assertEquals(1, calculateInSampleSize(1080, 720, 2160, 2160))
}
```

- [ ] **Step 2: Verify RED**

- [ ] **Step 3: Implement sampled decode and bounded caches**

Grid thumbnails decode near cell size. Viewer decodes with viewport headroom rather than blindly loading source resolution. Do not hold all full-size bitmaps.

- [ ] **Step 4: Run tests**

- [ ] **Step 5: Commit**

```bash
git add coreapp/src/main/kotlin/com/atmaca/gallery/ImageLoader.kt coreapp/src/main/kotlin/com/atmaca/gallery/StablePhotoPage.kt coreapp/src/test/kotlin/com/atmaca/gallery/ImageLoaderTest.kt
git commit -m "perf: add viewport-aware image decoding"
```

### Task 8: Slideshow engine

**Files:**
- Create: `coreapp/src/main/kotlin/com/atmaca/gallery/SlideshowController.kt`
- Modify: `coreapp/src/main/kotlin/com/atmaca/gallery/GalleryApp.kt`
- Modify: `coreapp/src/main/kotlin/com/atmaca/gallery/SettingsScreen.kt`
- Test: `coreapp/src/test/kotlin/com/atmaca/gallery/SlideshowControllerTest.kt`

**Interfaces:**
- Produces: start/pause/stop, interval, loop, random-order state.

- [ ] **Step 1: Write failing tests**

```kotlin
@Test fun slideshowAdvancesAfterConfiguredInterval() {
    val c = SlideshowController(intervalMs = 3000, count = 5)
    assertEquals(1, c.nextIndex(current = 0))
}

@Test fun loopReturnsToFirstItem() {
    val c = SlideshowController(intervalMs = 3000, count = 5, loop = true)
    assertEquals(0, c.nextIndex(current = 4))
}
```

- [ ] **Step 2: Verify RED**

- [ ] **Step 3: Implement slideshow and keep-screen-awake behavior**

- [ ] **Step 4: Run tests**

- [ ] **Step 5: Commit**

```bash
git add coreapp/src/main/kotlin/com/atmaca/gallery/SlideshowController.kt coreapp/src/main/kotlin/com/atmaca/gallery/GalleryApp.kt coreapp/src/main/kotlin/com/atmaca/gallery/SettingsScreen.kt coreapp/src/test/kotlin/com/atmaca/gallery/SlideshowControllerTest.kt
git commit -m "feat: add configurable slideshow"
```

### Task 9: Duplicate finder moved into Settings/Tools

**Files:**
- Modify: existing duplicate-finder source files
- Modify: `coreapp/src/main/kotlin/com/atmaca/gallery/SettingsScreen.kt`
- Test: duplicate rules tests

**Interfaces:**
- Existing duplicate scanner remains reusable but no longer has permanent bottom navigation.

- [ ] **Step 1: Add failing navigation test proving duplicates are only exposed under Tools**
- [ ] **Step 2: Verify RED**
- [ ] **Step 3: Wire duplicate finder from Settings/Tools and preserve result/delete behavior**
- [ ] **Step 4: Run tests**
- [ ] **Step 5: Commit `feat: move duplicate finder into tools`**

### Task 10: Smart Person Crop isolated module

**Files:**
- Create: `coreapp/src/main/kotlin/com/atmaca/gallery/tools/personcrop/PersonCropWorker.kt`
- Create: `coreapp/src/main/kotlin/com/atmaca/gallery/tools/personcrop/PersonCropScreen.kt`
- Create: `coreapp/src/main/kotlin/com/atmaca/gallery/tools/personcrop/PersonCropNaming.kt`
- Modify: `coreapp/src/main/kotlin/com/atmaca/gallery/SettingsScreen.kt`
- Test: `coreapp/src/test/kotlin/com/atmaca/gallery/tools/personcrop/PersonCropNamingTest.kt`

**Interfaces:**
- Input: selected photos or album/folder.
- Output: non-destructive crops by default, selected destination.
- Worker reports progress, cancelled, skipped, failed counts.

- [ ] **Step 1: Write failing output-naming/queue tests**

```kotlin
@Test fun multiplePersonCropsGetStableSequentialNames() {
    assertEquals("photo_person_2.jpg", personCropName("photo.jpg", 2))
}
```

- [ ] **Step 2: Verify RED**
- [ ] **Step 3: Implement bounded WorkManager worker and on-device detector adapter**
- [ ] **Step 4: Run tests and worker compilation**
- [ ] **Step 5: Commit `feat: add smart person crop tool`**

### Task 11: Image Packager isolated module

**Files:**
- Create: `coreapp/src/main/kotlin/com/atmaca/gallery/tools/packager/ImagePackagerWorker.kt`
- Create: `coreapp/src/main/kotlin/com/atmaca/gallery/tools/packager/ImagePackagerScreen.kt`
- Test: `coreapp/src/test/kotlin/com/atmaca/gallery/tools/packager/ImagePackagerRulesTest.kt`

**Interfaces:**
- Input: selected media or folder/album.
- Config: batch size, copy vs explicit move.
- Output: user-authorized destination with progress/cancel/error counts.

- [ ] **Step 1: Write failing batching tests**

```kotlin
@Test fun oneHundredOneImagesAtFiftyPerBatchCreatesThreeGroups() {
    assertEquals(listOf(50,50,1), packageBatchSizes(101, 50))
}
```

- [ ] **Step 2: Verify RED**
- [ ] **Step 3: Implement worker and UI**
- [ ] **Step 4: Run tests**
- [ ] **Step 5: Commit `feat: add image packager tool`**

### Task 12: Video Frames isolated module

**Files:**
- Create: `coreapp/src/main/kotlin/com/atmaca/gallery/tools/videoframes/VideoFramesWorker.kt`
- Create: `coreapp/src/main/kotlin/com/atmaca/gallery/tools/videoframes/VideoFramesScreen.kt`
- Create: `coreapp/src/main/kotlin/com/atmaca/gallery/tools/videoframes/FrameNaming.kt`
- Test: `coreapp/src/test/kotlin/com/atmaca/gallery/tools/videoframes/FrameNamingTest.kt`

**Interfaces:**
- Default cadence: 1 JPEG/second.
- Output folder per video based on video name.
- Output names: `<video name> 1.jpg`, `<video name> 2.jpg`, ...
- Queue continues when one video fails.

- [ ] **Step 1: Write failing cadence/naming tests**

```kotlin
@Test fun tenSecondVideoAtOneFramePerSecondRequestsTenFrames() {
    assertEquals(10, frameCount(durationMs = 10_000, intervalMs = 1_000))
}

@Test fun frameNameUsesVideoNameAndHumanNumber() {
    assertEquals("tatil 3.jpg", frameName("tatil.mp4", 3))
}
```

- [ ] **Step 2: Verify RED**
- [ ] **Step 3: Implement bounded background extraction worker**
- [ ] **Step 4: Run tests**
- [ ] **Step 5: Commit `feat: add video frame extraction tool`**

### Task 13: Build, integration and release verification

**Files:**
- Modify: `coreapp/build.gradle.kts`
- Modify/Create: `.github/workflows/complete-gallery.yml`
- Test: all unit tests plus APK assembly.

**Interfaces:**
- Release version: next available versionCode; versionName `0.6.0-complete-tools` unless an intervening commit already uses it.

- [ ] **Step 1: Run full unit suite**

Run: `gradle :coreapp:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 2: Assemble debug APK**

Run: `gradle :coreapp:assembleDebug`
Expected: PASS and `coreapp/build/outputs/apk/debug/coreapp-debug.apk` exists.

- [ ] **Step 3: Run archive integrity check after CI artifact download**

Run: `unzip -t <artifact.zip>` and `unzip -t <apk>`
Expected: no errors.

- [ ] **Step 4: Record SHA-256 and artifact size**

Run: `sha256sum <apk> <artifact.zip>`

- [ ] **Step 5: Commit release/build changes**

```bash
git add coreapp/build.gradle.kts .github/workflows/complete-gallery.yml
git commit -m "build: verify complete gallery tools release"
```

- [ ] **Step 6: Final gate**

Do not claim real-device smoothness until the APK is installed and tested on the Tecno Spark 10 Pro. CI success proves compilation/tests only, not OEM runtime behavior.
