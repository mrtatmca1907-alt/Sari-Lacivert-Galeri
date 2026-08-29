# ATMACA Galeri Performance Rebuild Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the existing gallery into a stability-first `ATMACA Galeri` build that minimizes ANR, freezes, crashes, memory spikes, and jank while preserving the useful gallery features.

**Architecture:** Keep the existing Kotlin/Jetpack Compose app and MediaStore data source, but isolate expensive work behind repository/cache boundaries, bounded coroutine concurrency, and deterministic lifecycle cleanup. Media metadata stays lightweight in UI state, thumbnails and viewer decodes use separate bounded paths, file operations are chunked, and failures are handled per item instead of crashing the whole session.

**Tech Stack:** Kotlin, Jetpack Compose, Kotlin Coroutines, MediaStore, AndroidX Media3 ExoPlayer, WorkManager, JUnit, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-08-29-gallery-performance-rebuild-design.md`

## Global Constraints

- App name is `ATMACA Galeri`.
- Stability and responsiveness take priority over optional visual effects.
- No MediaStore query, file traversal, EXIF read, hashing, duplicate scan, copy, move, delete, or bitmap decode on the main thread.
- Thumbnail and full-image decoding use separate bounded concurrency.
- One bad/corrupt/inaccessible media item must not abort the whole gallery operation.
- Media3 owns at most one active viewer player and releases it when the viewer leaves composition.
- Preserve albums, photo/video viewing, favorites, search, trash, duplicate scan, copy/move, rename, metadata and current useful navigation where practical.
- Do not add cloud sync, ads, analytics, or unrelated AI features in this stabilization pass.

---

### Task 1: Add regression tests for media sorting/cache policy

**Files:**
- Create: `app/src/test/kotlin/com/sarilacivert/galeri/data/MediaIndexPolicyTest.kt`
- Create: `app/src/main/kotlin/com/sarilacivert/galeri/data/MediaIndexPolicy.kt`
- Modify: `app/build.gradle.kts`

**Interfaces:**
- Produces: `MediaIndexPolicy.shouldReuseCache(nowMs: Long, builtAtMs: Long, ttlMs: Long): Boolean`
- Produces: `MediaIndexPolicy.normalizedTimestamp(dateTaken: Long, dateAdded: Long): Long`

- [ ] **Step 1: Write failing unit tests**

```kotlin
package com.sarilacivert.galeri.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaIndexPolicyTest {
    @Test fun cacheInsideTtlIsReusable() {
        assertTrue(MediaIndexPolicy.shouldReuseCache(2_500, 1_000, 2_000))
    }

    @Test fun expiredCacheIsNotReusable() {
        assertFalse(MediaIndexPolicy.shouldReuseCache(3_100, 1_000, 2_000))
    }

    @Test fun newestTimestampWins() {
        assertEquals(500L, MediaIndexPolicy.normalizedTimestamp(500, 400))
    }
}
```

- [ ] **Step 2: Run the tests and confirm RED**

Run: `./gradlew :app:testDebugUnitTest --tests com.sarilacivert.galeri.data.MediaIndexPolicyTest`
Expected: FAIL because `MediaIndexPolicy` does not exist.

- [ ] **Step 3: Implement the policy helper**

```kotlin
package com.sarilacivert.galeri.data

object MediaIndexPolicy {
    fun shouldReuseCache(nowMs: Long, builtAtMs: Long, ttlMs: Long): Boolean =
        builtAtMs > 0L && nowMs >= builtAtMs && nowMs - builtAtMs <= ttlMs

    fun normalizedTimestamp(dateTaken: Long, dateAdded: Long): Long =
        maxOf(dateTaken, dateAdded)
}
```

Add `testImplementation("junit:junit:4.13.2")` to `app/build.gradle.kts` if it is not already present.

- [ ] **Step 4: Run tests and confirm GREEN**

Run: `./gradlew :app:testDebugUnitTest --tests com.sarilacivert.galeri.data.MediaIndexPolicyTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/test/kotlin/com/sarilacivert/galeri/data/MediaIndexPolicyTest.kt app/src/main/kotlin/com/sarilacivert/galeri/data/MediaIndexPolicy.kt app/build.gradle.kts
git commit -m "test: lock media cache policy"
```

### Task 2: Stabilize MediaStore indexing and avoid repeated whole-library work

**Files:**
- Modify: `app/src/main/kotlin/com/sarilacivert/galeri/data/MediaRepository.kt`
- Test: `app/src/test/kotlin/com/sarilacivert/galeri/data/MediaIndexPolicyTest.kt`

**Interfaces:**
- Consumes: `MediaIndexPolicy.shouldReuseCache(...)`
- Produces: existing `loadAll`, `loadAlbums`, `loadAlbum`, `search`, `loadFavoriteItems`, and mutation APIs with bounded background work and cache invalidation.

- [ ] **Step 1: Add a failing cache-boundary test**

Extend `MediaIndexPolicyTest` with:

```kotlin
@Test fun clockRollbackNeverReusesCache() {
    assertFalse(MediaIndexPolicy.shouldReuseCache(900, 1_000, 2_000))
}
```

- [ ] **Step 2: Run the test and confirm RED if current policy permits rollback**

Run: `./gradlew :app:testDebugUnitTest --tests com.sarilacivert.galeri.data.MediaIndexPolicyTest`
Expected: PASS only after the helper guards `nowMs >= builtAtMs`.

- [ ] **Step 3: Refactor `MediaRepository` to reuse snapshots safely**

Use `MediaIndexPolicy.shouldReuseCache` in both pre-lock and inside-lock checks. Keep MediaStore queries on `Dispatchers.IO`, aggregation/filter/sort on `Dispatchers.Default`, preserve atomic replacement of `mediaCache`, and invalidate cache only after successful mutations. Do not clear visible UI state before a refresh result is ready.

- [ ] **Step 4: Make per-item provider failures non-fatal**

Wrap row-to-model conversion so a malformed row is skipped while the cursor continues. Mutation loops must return per-item success/failure instead of throwing out the entire batch.

- [ ] **Step 5: Run unit tests and debug compile**

Run: `./gradlew :app:testDebugUnitTest :app:compileDebugKotlin`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/sarilacivert/galeri/data/MediaRepository.kt app/src/test/kotlin/com/sarilacivert/galeri/data/MediaIndexPolicyTest.kt
git commit -m "perf: stabilize media indexing and cache reuse"
```

### Task 3: Bound bitmap memory and cancel obsolete image work

**Files:**
- Modify: `app/src/main/kotlin/com/sarilacivert/galeri/data/BitmapLoader.kt`
- Modify: `app/src/main/kotlin/com/sarilacivert/galeri/ui/Components.kt`
- Modify: `app/src/main/kotlin/com/sarilacivert/galeri/ui/ViewerScreen.kt`
- Modify: `app/src/main/kotlin/com/sarilacivert/galeri/MainActivity.kt`

**Interfaces:**
- Produces: `BitmapLoader.thumbnail(uri, sizePx)` with bounded concurrent decode and memory cache.
- Produces: `BitmapLoader.full(uri, maxDimension)` with one heavyweight decode at a time.
- Produces: deterministic `clearMemory()` integration on trim-memory events.

- [ ] **Step 1: Add testable sizing policy**

Create pure functions in `BitmapLoader` companion or a new `BitmapSizingPolicy.kt`:

```kotlin
fun safeThumbnailSize(requested: Int): Int = requested.coerceIn(96, 512)
fun viewerTarget(viewportMax: Int): Int = (viewportMax * 2).coerceIn(1536, 4096)
```

Write JUnit tests proving low/high values clamp correctly before using them in the loader.

- [ ] **Step 2: Run RED tests**

Run: `./gradlew :app:testDebugUnitTest`
Expected: FAIL until the sizing policy exists.

- [ ] **Step 3: Apply bounded decode rules**

Keep thumbnail concurrency at a small fixed limit, keep full decode concurrency at one, use `RGB_565` only for grid thumbnails, use `ARGB_8888` for viewer decodes, cap the memory LRU conservatively, and never place full-viewer bitmaps in the global thumbnail cache.

- [ ] **Step 4: Ensure Compose cancels obsolete requests**

Key image-loading effects by media URI and target size. When a grid cell leaves composition or the viewer changes item, the coroutine should cancel and the resulting bitmap must not be written into stale UI state.

- [ ] **Step 5: Clear transient bitmap cache on memory pressure**

Wire `ComponentCallbacks2` / `onTrimMemory` from the activity or application boundary to call `BitmapLoader.clearMemory()` for meaningful trim levels.

- [ ] **Step 6: Run tests and compile**

Run: `./gradlew :app:testDebugUnitTest :app:compileDebugKotlin`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/com/sarilacivert/galeri/data/BitmapLoader.kt app/src/main/kotlin/com/sarilacivert/galeri/ui/Components.kt app/src/main/kotlin/com/sarilacivert/galeri/ui/ViewerScreen.kt app/src/main/kotlin/com/sarilacivert/galeri/MainActivity.kt app/src/test
git commit -m "perf: bound image decoding and memory use"
```

### Task 4: Make video viewer lifecycle deterministic

**Files:**
- Modify: `app/src/main/kotlin/com/sarilacivert/galeri/ui/ViewerScreen.kt`

**Interfaces:**
- Produces: exactly one active `ExoPlayer` for the visible video item.
- Produces: player release on disposal/navigation and visible error state for unsupported/corrupt media.

- [ ] **Step 1: Add a small pure playback-state reducer test**

Create `ViewerPlaybackState.kt` with states `Idle`, `Loading`, `Ready`, `Error(message)` and a pure transition function used by the viewer. Write JUnit tests for prepare-success, prepare-error, and dispose-to-idle.

- [ ] **Step 2: Run RED tests**

Run: `./gradlew :app:testDebugUnitTest`
Expected: FAIL before reducer implementation.

- [ ] **Step 3: Implement the state reducer and player ownership**

Create the player only for the current video item, call `setMediaItem`, `prepare`, and update state from `Player.Listener`. In `DisposableEffect`, remove listeners, stop, clear media items, and release the player. Never keep one player per pager page.

- [ ] **Step 4: Handle video failure without frozen white/black screen**

Render a lightweight error panel with retry/back controls when playback enters `Error` instead of leaving an empty player surface.

- [ ] **Step 5: Run tests and compile**

Run: `./gradlew :app:testDebugUnitTest :app:compileDebugKotlin`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/sarilacivert/galeri/ui/ViewerScreen.kt app/src/main/kotlin/com/sarilacivert/galeri/ui/ViewerPlaybackState.kt app/src/test
git commit -m "fix: make video playback lifecycle stable"
```

### Task 5: Chunk large file operations and isolate failures

**Files:**
- Modify: `app/src/main/kotlin/com/sarilacivert/galeri/data/MediaRepository.kt`
- Modify: `app/src/main/kotlin/com/sarilacivert/galeri/ui/GalleryApp.kt`
- Modify: `app/src/main/kotlin/com/sarilacivert/galeri/ui/DuplicatesScreen.kt`
- Modify: `app/src/main/kotlin/com/sarilacivert/galeri/worker/DuplicateScanWorker.kt`

**Interfaces:**
- Produces: batch result model containing processed/succeeded/failed counts.
- Produces: chunked copy/move/delete/duplicate processing with cancellation points.

- [ ] **Step 1: Add a pure `BatchProgress` unit test**

Create `BatchProgress.kt` and test that progress never exceeds total, failed items do not reduce processed count, and completion is true only when `processed >= total`.

- [ ] **Step 2: Run RED tests**

Run: `./gradlew :app:testDebugUnitTest`
Expected: FAIL until `BatchProgress` exists.

- [ ] **Step 3: Process large selections in bounded chunks**

Use chunks of at most 32 items for copy/move style loops, call `ensureActive()` between chunks, and keep the actual file I/O inside `Dispatchers.IO`. Collect failures per item and continue.

- [ ] **Step 4: Keep duplicate scanning off the UI thread**

Keep hashing/decoding inside WorkManager/background dispatchers, persist progress after batches, and update UI from worker state instead of direct synchronous scanning.

- [ ] **Step 5: Show non-blocking progress**

In Compose, keep navigation responsive while a batch operation runs; show progress/cancel UI without running the operation in a click callback on the main thread.

- [ ] **Step 6: Run tests and compile**

Run: `./gradlew :app:testDebugUnitTest :app:compileDebugKotlin`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/com/sarilacivert/galeri/data/MediaRepository.kt app/src/main/kotlin/com/sarilacivert/galeri/ui/GalleryApp.kt app/src/main/kotlin/com/sarilacivert/galeri/ui/DuplicatesScreen.kt app/src/main/kotlin/com/sarilacivert/galeri/worker/DuplicateScanWorker.kt app/src/main/kotlin/com/sarilacivert/galeri/data/BatchProgress.kt app/src/test
git commit -m "perf: chunk heavy gallery operations"
```

### Task 6: Modernize identity without adding GPU-heavy UI

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/res/values/strings.xml` if present; otherwise create it
- Modify/Create: launcher icon resources under `app/src/main/res/mipmap-anydpi-v26/` and `app/src/main/res/drawable/`
- Modify: `app/src/main/kotlin/com/sarilacivert/galeri/ui/GalleryTheme.kt`
- Modify: `app/src/main/kotlin/com/sarilacivert/galeri/ui/GalleryApp.kt`
- Modify: `app/build.gradle.kts`

**Interfaces:**
- Produces: visible app name `ATMACA Galeri`.
- Produces: lightweight navy/yellow adaptive launcher icon and restrained gallery chrome.

- [ ] **Step 1: Add/verify string resource**

Use:

```xml
<resources>
    <string name="app_name">ATMACA Galeri</string>
</resources>
```

Point the manifest application label at `@string/app_name`.

- [ ] **Step 2: Update version metadata**

Increment `versionCode` from 22 and set a stability-focused `versionName`, for example `2.2.0-stable`.

- [ ] **Step 3: Add the adaptive icon**

Use a simple vector foreground with no bitmap-heavy effects and a navy/yellow palette. Keep adaptive foreground/background resources compatible with Android 8+.

- [ ] **Step 4: Keep UI effects lightweight**

Use the existing dark/navy/yellow theme, avoid blur, oversized shadows, continuously animated backgrounds, and unnecessary recompositions. Ensure lazy-grid items use stable media IDs/URIs as keys.

- [ ] **Step 5: Compile resources**

Run: `./gradlew :app:processDebugResources :app:compileDebugKotlin`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/AndroidManifest.xml app/src/main/res app/src/main/kotlin/com/sarilacivert/galeri/ui/GalleryTheme.kt app/src/main/kotlin/com/sarilacivert/galeri/ui/GalleryApp.kt app/build.gradle.kts
git commit -m "feat: polish ATMACA Galeri identity"
```

### Task 7: CI verification and APK artifact

**Files:**
- Modify: `.github/workflows/build-apk.yml` only if the new branch is not already built by CI.

**Interfaces:**
- Produces: a GitHub Actions run that executes unit tests and `assembleDebug` for `gallery-performance-rebuild`.

- [ ] **Step 1: Ensure CI runs tests before APK assembly**

The branch job must execute:

```bash
gradle :app:testDebugUnitTest :app:assembleDebug --stacktrace
```

- [ ] **Step 2: Push/trigger the branch workflow**

Expected: a workflow run for `gallery-performance-rebuild`.

- [ ] **Step 3: Inspect the completed job**

Expected: unit tests PASS, Kotlin compile PASS, debug APK assembly PASS, artifact upload PASS.

- [ ] **Step 4: Download and inspect the artifact**

Verify the returned archive contains a freshly built APK from the successful run. Confirm the package and file size from the produced APK before presenting it.

- [ ] **Step 5: Final commit if CI workflow needed adjustment**

```bash
git add .github/workflows/build-apk.yml
git commit -m "ci: verify ATMACA Galeri stable build"
```
