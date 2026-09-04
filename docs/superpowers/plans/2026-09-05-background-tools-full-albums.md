# Background Tools and Complete Albums Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Run all three ATMACA media tools independently of the dialog and show every accessible MediaStore album in the gallery and tool picker.

**Architecture:** A file-backed `MediaToolWorker` replaces the video-only background path and dispatches to the existing engine by tool type. A focused MediaStore album loader queries images and videos independently; both album consumers render only that complete snapshot and expose query failure instead of fabricating four albums from the current page.

**Tech Stack:** Kotlin, Jetpack Compose, Android MediaStore, Android WorkManager foreground work, JUnit 4, Gradle Android plugin.

**Spec:** `docs/superpowers/specs/2026-09-05-background-tools-full-albums-design.md`

## Global Constraints

- Starting a job must not automatically close its tool dialog.
- Akıllı Kişi Kırpma, Görsel Paketleyici, and Video Kareleri continue after leaving the dialog or turning off the display.
- WorkManager input contains no URI array; selected URIs live in an app-private queue file.
- Album discovery uses MediaStore only and never a broad filesystem scan.
- The root-folder picker is outside this fix and remains secondary.
- Existing crop geometry, packaging rules, frame-rate semantics, viewer gestures, and output naming remain unchanged.
- Target device remains Android 13 / Tecno HiOS, while minSdk stays 26.

---

### Task 1: Define a file-backed job contract for every tool

**Files:**
- Create: `coreapp/src/main/kotlin/com/atmaca/gallery/MediaToolWorkContract.kt`
- Create: `coreapp/src/test/kotlin/com/atmaca/gallery/MediaToolWorkContractTest.kt`

**Interfaces:**
- Consumes: `AtmacaToolPage`, tool option integers, and selected URI strings.
- Produces: `MediaToolJobSpec(tool: AtmacaToolPage, option: Int)`, `writeMediaToolQueue(File, List<String>): Boolean`, `readMediaToolQueue(File): List<String>`, and stable WorkManager data keys.

- [ ] **Step 1: Write failing contract tests**

```kotlin
@Test fun queueRoundTripDeduplicatesWithoutPuttingUrisInWorkData() {
    val file = temporaryFolder.newFile("job.queue")
    assertTrue(writeMediaToolQueue(file, listOf("content://a", "content://a", "content://b")))
    assertEquals(listOf("content://a", "content://b"), readMediaToolQueue(file))
    assertFalse(mediaToolInputKeys().contains("uris"))
}

@Test fun eachToolMapsToItsOptionKind() {
    assertEquals(MediaToolOption.MAX_FACES, optionFor(AtmacaToolPage.PERSON_CROP))
    assertEquals(MediaToolOption.BATCH_SIZE, optionFor(AtmacaToolPage.PACKAGER))
    assertEquals(MediaToolOption.FRAMES_PER_SECOND, optionFor(AtmacaToolPage.VIDEO_FRAMES))
}
```

- [ ] **Step 2: Run the focused test and confirm failure**

Run: `./gradlew :coreapp:testDebugUnitTest --tests '*MediaToolWorkContractTest'`

Expected: FAIL because the contract types and functions do not exist.

- [ ] **Step 3: Implement the minimal queue and option contract**

```kotlin
internal const val KEY_QUEUE_FILE = "queue_file"
internal const val KEY_TOOL = "tool"
internal const val KEY_OPTION = "option"
internal const val KEY_DONE = "done"
internal const val KEY_TOTAL = "total"
internal const val KEY_CREATED = "created"
internal const val KEY_SKIPPED = "skipped"
internal const val KEY_FAILED = "failed"

internal enum class MediaToolOption { MAX_FACES, BATCH_SIZE, FRAMES_PER_SECOND }
internal data class MediaToolJobSpec(val tool: AtmacaToolPage, val option: Int)

internal fun writeMediaToolQueue(file: File, values: List<String>): Boolean = runCatching {
    file.parentFile?.mkdirs()
    file.bufferedWriter().use { out -> values.distinct().forEach(out::appendLine) }
    true
}.getOrDefault(false)

internal fun readMediaToolQueue(file: File): List<String> =
    file.useLines { it.filter(String::isNotBlank).distinct().toList() }
```

- [ ] **Step 4: Run the focused test and confirm success**

Run: `./gradlew :coreapp:testDebugUnitTest --tests '*MediaToolWorkContractTest'`

Expected: PASS.

- [ ] **Step 5: Commit the contract**

```bash
git add coreapp/src/main/kotlin/com/atmaca/gallery/MediaToolWorkContract.kt coreapp/src/test/kotlin/com/atmaca/gallery/MediaToolWorkContractTest.kt
git commit -m "test: define file backed media tool jobs"
```

### Task 2: Replace the video-only worker with the shared foreground worker

**Files:**
- Create: `coreapp/src/main/kotlin/com/atmaca/gallery/MediaToolWorker.kt`
- Modify: `coreapp/src/main/kotlin/com/atmaca/gallery/VideoFrameWorker.kt`
- Create: `coreapp/src/test/kotlin/com/atmaca/gallery/MediaToolWorkerContractTest.kt`

**Interfaces:**
- Consumes: `MediaToolJobSpec`, its queue file, and `CompleteToolEngine`.
- Produces: `enqueueMediaToolWork(Context, AtmacaToolPage, List<Uri>, Int): UUID?`, foreground progress, and output data containing created/skipped/failed counts.

- [ ] **Step 1: Write failing dispatch and lifecycle tests**

```kotlin
@Test fun allThreeToolsAreBackgroundCapable() {
    AtmacaToolPage.entries.forEach { assertTrue(toolUsesBackgroundWorker(it)) }
}

@Test fun onlyExplicitCancelCancelsAStartedJob() {
    assertFalse(shouldCancelMediaToolWork(ToolUiExit.DIALOG_DISMISSED))
    assertFalse(shouldCancelMediaToolWork(ToolUiExit.APP_BACKGROUND))
    assertTrue(shouldCancelMediaToolWork(ToolUiExit.EXPLICIT_CANCEL))
}

@Test fun toolDispatchIsComplete() {
    assertEquals(EngineOperation.PERSON_CROP, engineOperation(AtmacaToolPage.PERSON_CROP))
    assertEquals(EngineOperation.PACKAGE_MEDIA, engineOperation(AtmacaToolPage.PACKAGER))
    assertEquals(EngineOperation.VIDEO_FRAMES, engineOperation(AtmacaToolPage.VIDEO_FRAMES))
}
```

- [ ] **Step 2: Run the focused test and confirm failure**

Run: `./gradlew :coreapp:testDebugUnitTest --tests '*MediaToolWorkerContractTest'`

Expected: FAIL because the shared worker rules do not exist.

- [ ] **Step 3: Implement enqueue and complete dispatch**

```kotlin
fun enqueueMediaToolWork(context: Context, tool: AtmacaToolPage, uris: List<Uri>, option: Int): UUID? {
    if (uris.isEmpty()) return null
    val id = UUID.randomUUID()
    val queue = File(context.filesDir, "media_tool_jobs/$id.queue")
    if (!writeMediaToolQueue(queue, uris.map(Uri::toString))) return null
    val request = OneTimeWorkRequestBuilder<MediaToolWorker>()
        .setId(id)
        .setInputData(workDataOf(KEY_QUEUE_FILE to queue.absolutePath, KEY_TOOL to tool.name, KEY_OPTION to option))
        .addTag("atmaca_media_tools")
        .build()
    WorkManager.getInstance(context.applicationContext).enqueue(request)
    return id
}
```

Inside `MediaToolWorker.doWork`, parse the queue and dispatch exhaustively:

```kotlin
val result = when (tool) {
    AtmacaToolPage.PERSON_CROP -> engine.smartPersonCrop(uris, option.coerceIn(1, 24), ::publishProgress)
    AtmacaToolPage.PACKAGER -> engine.packageMedia(uris, option.coerceIn(5, 200), ::publishProgress)
    AtmacaToolPage.VIDEO_FRAMES -> engine.extractVideoFrames(uris, option.coerceIn(1, 4), true, ::publishProgress)
}
```

Use one notification channel named `ATMACA Arka Plan İşlemleri`, a stable per-job notification ID, `FOREGROUND_SERVICE_TYPE_DATA_SYNC`, and Turkish tool-specific titles. Delete the queue in `finally` when work reaches success, failure, or cancellation; retain it only when returning `Result.retry()`.

Keep `enqueueVideoFrameWork` temporarily as a deprecated forwarding wrapper so no stale call site can break compilation, then remove it after Task 3 migrates the UI.

- [ ] **Step 4: Run worker and existing frame tests**

Run: `./gradlew :coreapp:testDebugUnitTest --tests '*MediaToolWorkerContractTest' --tests '*OemRegressionTest'`

Expected: PASS.

- [ ] **Step 5: Commit the worker**

```bash
git add coreapp/src/main/kotlin/com/atmaca/gallery/MediaToolWorker.kt coreapp/src/main/kotlin/com/atmaca/gallery/VideoFrameWorker.kt coreapp/src/test/kotlin/com/atmaca/gallery/MediaToolWorkerContractTest.kt
git commit -m "feat: run every media tool in foreground work"
```

### Task 3: Detach tool execution from the Compose dialog

**Files:**
- Modify: `coreapp/src/main/kotlin/com/atmaca/gallery/CompleteSettingsExtras.kt`
- Modify: `coreapp/src/main/kotlin/com/atmaca/gallery/FeatureRules.kt`
- Modify: `coreapp/src/test/kotlin/com/atmaca/gallery/OemRegressionTest.kt`
- Create: `coreapp/src/test/kotlin/com/atmaca/gallery/BackgroundToolUiContractTest.kt`

**Interfaces:**
- Consumes: `enqueueMediaToolWork` and WorkInfo progress/output keys.
- Produces: a dialog observer that survives recomposition through persisted work ID and cancels only from the visible `İptal` action.

- [ ] **Step 1: Update tests to require background behavior for all tools**

```kotlin
@Test fun everyToolKeepsDialogAvailableForBackgroundProgress() {
    AtmacaToolPage.entries.forEach { assertTrue(keepToolDialogOpenForBackgroundProgress(it)) }
}

@Test fun returningToGalleryDoesNotRequestCancellation() {
    assertEquals(BackgroundToolAction.KEEP_RUNNING, backgroundToolAction(explicitCancel = false))
    assertEquals(BackgroundToolAction.CANCEL, backgroundToolAction(explicitCancel = true))
}
```

- [ ] **Step 2: Run UI contract tests and confirm failure**

Run: `./gradlew :coreapp:testDebugUnitTest --tests '*BackgroundToolUiContractTest' --tests '*OemRegressionTest'`

Expected: FAIL because person crop and packager are still dialog coroutine jobs.

- [ ] **Step 3: Route `start()` for every tool through WorkManager**

```kotlin
val option = when (tool) {
    AtmacaToolPage.PERSON_CROP -> maxFaces
    AtmacaToolPage.PACKAGER -> batchSize
    AtmacaToolPage.VIDEO_FRAMES -> framesPerSecond
}
val workId = enqueueMediaToolWork(context, tool, selectedUris, option)
```

Remove direct `engine.smartPersonCrop` and `engine.packageMedia` launches from the composable. Persist the latest ID per tool in `SharedPreferences`, restore it when the dialog opens, observe WorkInfo, and clear only terminal IDs. `onDismiss` closes UI without cancellation. The `İptal` button explicitly calls `cancelWorkById` and deletes the associated queue after WorkManager reaches `CANCELLED`.

Render `İşleniyor: done / total` for all three tools and a completion message using created/skipped/failed. Keep the dialog open after enqueue, as approved.

- [ ] **Step 4: Run focused and complete unit tests**

Run: `./gradlew :coreapp:testDebugUnitTest`

Expected: PASS with the old video-only expectation replaced by all-three background coverage.

- [ ] **Step 5: Commit the detached UI**

```bash
git add coreapp/src/main/kotlin/com/atmaca/gallery/CompleteSettingsExtras.kt coreapp/src/main/kotlin/com/atmaca/gallery/FeatureRules.kt coreapp/src/test/kotlin/com/atmaca/gallery/OemRegressionTest.kt coreapp/src/test/kotlin/com/atmaca/gallery/BackgroundToolUiContractTest.kt
git commit -m "feat: detach media tools from settings dialog"
```

### Task 4: Make the complete MediaStore album snapshot authoritative

**Files:**
- Modify: `coreapp/src/main/kotlin/com/atmaca/gallery/MediaStoreRepository.kt`
- Create: `coreapp/src/main/kotlin/com/atmaca/gallery/AlbumLoadResult.kt`
- Modify: `coreapp/src/test/kotlin/com/atmaca/gallery/AlbumEnumerationOemContractTest.kt`
- Create: `coreapp/src/test/kotlin/com/atmaca/gallery/CompleteAlbumSnapshotTest.kt`

**Interfaces:**
- Consumes: separate Images and Video MediaStore cursors.
- Produces: `AlbumLoadResult(albums: List<GalleryAlbum>, imageQueryFailed: Boolean, videoQueryFailed: Boolean)` from `loadCompleteAlbums()`.

- [ ] **Step 1: Write failing aggregation and partial-failure tests**

```kotlin
@Test fun sameRelativePathAcrossCollectionsMergesIntoOneAlbum() {
    val rows = listOf(imageRow("DCIM/Camera/"), videoRow("DCIM/Camera/"))
    val albums = aggregateAlbumRows(rows)
    assertEquals(1, albums.size)
    assertEquals(2, albums.single().count)
}

@Test fun oneFailedCollectionKeepsTheOtherCollectionVisible() {
    val result = combineAlbumQueries(images = Result.success(listOf(imageRow("Pictures/A/"))), videos = Result.failure(Exception()))
    assertEquals(listOf("A"), result.albums.map(GalleryAlbum::name))
    assertFalse(result.imageQueryFailed)
    assertTrue(result.videoQueryFailed)
}

@Test fun completeFailureDoesNotInventQuickAlbums() {
    val result = combineAlbumQueries(Result.failure(Exception()), Result.failure(Exception()))
    assertTrue(result.albums.isEmpty())
    assertTrue(result.imageQueryFailed && result.videoQueryFailed)
}
```

- [ ] **Step 2: Run album tests and confirm failure**

Run: `./gradlew :coreapp:testDebugUnitTest --tests '*CompleteAlbumSnapshotTest' --tests '*AlbumEnumerationOemContractTest'`

Expected: FAIL because structured results and independently testable aggregation do not exist.

- [ ] **Step 3: Implement independent minimal-projection queries**

Use `_ID`, `DISPLAY_NAME`, `DATE_ADDED`, `BUCKET_ID`, and `BUCKET_DISPLAY_NAME` as the mandatory projection. Query `RELATIVE_PATH` only when available and retry without it on OEM rejection. Query `MediaStore.Images.Media.EXTERNAL_CONTENT_URI` and `MediaStore.Video.Media.EXTERNAL_CONTENT_URI` separately, then merge rows by `albumIdentityKey`. Preserve albums from a successful collection if the other query fails.

```kotlin
data class AlbumLoadResult(
    val albums: List<GalleryAlbum>,
    val imageQueryFailed: Boolean,
    val videoQueryFailed: Boolean
) {
    val completelyFailed: Boolean get() = imageQueryFailed && videoQueryFailed
}
```

Ensure cancellation is rethrown rather than converted to query failure, choose the newest row as cover, and sort with `lowercase(Locale.ROOT)`.

- [ ] **Step 4: Run all repository contract tests**

Run: `./gradlew :coreapp:testDebugUnitTest --tests '*Album*Test' --tests '*Oem*Test'`

Expected: PASS.

- [ ] **Step 5: Commit album loading**

```bash
git add coreapp/src/main/kotlin/com/atmaca/gallery/MediaStoreRepository.kt coreapp/src/main/kotlin/com/atmaca/gallery/AlbumLoadResult.kt coreapp/src/test/kotlin/com/atmaca/gallery/AlbumEnumerationOemContractTest.kt coreapp/src/test/kotlin/com/atmaca/gallery/CompleteAlbumSnapshotTest.kt
git commit -m "fix: load complete albums from media collections"
```

### Task 5: Use the same album result in both screens and make refresh truthful

**Files:**
- Modify: `coreapp/src/main/kotlin/com/atmaca/gallery/GalleryApp.kt`
- Modify: `coreapp/src/main/kotlin/com/atmaca/gallery/InternalToolAlbumPicker.kt`
- Modify: `coreapp/src/main/kotlin/com/atmaca/gallery/AlbumFallback.kt`
- Create: `coreapp/src/test/kotlin/com/atmaca/gallery/AlbumConsumerContractTest.kt`

**Interfaces:**
- Consumes: `MediaStoreRepository.loadCompleteAlbums()`.
- Produces: atomic album snapshot replacement, refresh behavior, and a visible Turkish failure state shared by both consumers.

- [ ] **Step 1: Write failing source-wiring tests**

```kotlin
@Test fun albumConsumersUseCompleteRepositorySnapshot() {
    assertTrue(source("GalleryApp.kt").contains("loadCompleteAlbums()"))
    assertTrue(source("InternalToolAlbumPicker.kt").contains("loadCompleteAlbums()"))
}

@Test fun userVisibleAlbumRenderingHasNoQuickPageFallback() {
    assertFalse(source("GalleryApp.kt").contains("else quickAlbums(state.items)"))
    assertFalse(source("InternalToolAlbumPicker.kt").contains("loadMixedPage(offset"))
}
```

- [ ] **Step 2: Run the consumer test and confirm failure**

Run: `./gradlew :coreapp:testDebugUnitTest --tests '*AlbumConsumerContractTest'`

Expected: FAIL on both old fallback/paging paths.

- [ ] **Step 3: Wire both consumers to the complete snapshot**

In each `LaunchedEffect(refreshKey)`, call `loadCompleteAlbums()` once, set `albums` to the returned list atomically, and set an error message only when `completelyFailed`. Do not blank the previous snapshot while refresh is running; show the progress indicator alongside it. The refresh button increments its key and always triggers a new repository query.

Main gallery rendering uses `albums` directly:

```kotlin
AlbumGrid(
    albums = albums,
    loading = albumsLoading,
    error = albumsError,
    onRefresh = { albumsRefresh++ },
    onOpen = vm::openAlbum
)
```

The internal picker enters an album through `loadAllInAlbumOemSafe(album)`, filters for the active tool, and reports `Bu albümde bu araca uygun medya yok` instead of silently closing with an empty selection.

- [ ] **Step 4: Run all unit tests**

Run: `./gradlew :coreapp:testDebugUnitTest`

Expected: PASS.

- [ ] **Step 5: Commit both consumers**

```bash
git add coreapp/src/main/kotlin/com/atmaca/gallery/GalleryApp.kt coreapp/src/main/kotlin/com/atmaca/gallery/InternalToolAlbumPicker.kt coreapp/src/main/kotlin/com/atmaca/gallery/AlbumFallback.kt coreapp/src/test/kotlin/com/atmaca/gallery/AlbumConsumerContractTest.kt
git commit -m "fix: show complete albums in gallery pickers"
```

### Task 6: Build identity, regression verification, and APK

**Files:**
- Modify: `coreapp/build.gradle.kts`
- Modify: `coreapp/src/main/kotlin/com/atmaca/gallery/BuildIdentity.kt`
- Modify: `coreapp/src/test/kotlin/com/atmaca/gallery/OemRegressionTest.kt`
- Verify: `.github/workflows/build-atmaca-core.yml`

**Interfaces:**
- Consumes: completed background and album changes.
- Produces: a uniquely identifiable signed/debuggable APK artifact.

- [ ] **Step 1: Bump and test build identity**

Set `versionCode = 140906`, `versionName = "0.7.2-hios-build-140906"`, and visible badge `BUILD 140906`. Update the exact assertion in `OemRegressionTest`.

- [ ] **Step 2: Run clean verification**

Run: `./gradlew clean :coreapp:testDebugUnitTest :coreapp:assembleDebug`

Expected: BUILD SUCCESSFUL and every unit test passes.

- [ ] **Step 3: Inspect the APK**

Run: `unzip -t coreapp/build/outputs/apk/debug/coreapp-debug.apk`

Expected: `No errors detected in compressed data`.

Run: `apkanalyzer manifest application-id coreapp/build/outputs/apk/debug/coreapp-debug.apk`

Expected: `com.atmaca.gallery`.

Run: `apkanalyzer manifest version-code coreapp/build/outputs/apk/debug/coreapp-debug.apk`

Expected: `140906`.

- [ ] **Step 4: Commit the build identity**

```bash
git add coreapp/build.gradle.kts coreapp/src/main/kotlin/com/atmaca/gallery/BuildIdentity.kt coreapp/src/test/kotlin/com/atmaca/gallery/OemRegressionTest.kt
git commit -m "build: release gallery background jobs fix"
```

- [ ] **Step 5: Push and run clean CI**

Push `fix/background-tools-full-albums`, run `.github/workflows/build-atmaca-core.yml`, and require successful source-architecture, unit-test, APK-build, and artifact-upload steps before delivery.
