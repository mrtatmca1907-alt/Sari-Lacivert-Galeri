# Lightweight Gallery Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a fast native Android gallery with MediaStore albums, virtualized thumbnails, zoomable image viewing, in-app video playback, and safe delete/copy/move.

**Architecture:** `gallery/` is a standalone Kotlin Android app. MediaStore is the source of truth; RecyclerView renders album/media lists; ViewPager2 renders viewer pages; SAF/DocumentFile performs copies and MediaStore delete requests perform destructive operations.

**Tech Stack:** Kotlin 2.0.21, AGP 8.7.3, Android SDK 35, RecyclerView 1.4.0, ViewPager2 1.1.0, DocumentFile 1.0.1, Coroutines 1.9.0, JUnit 4.13.2.

**Spec:** `docs/superpowers/specs/2026-08-28-lite-gallery-design.md`

## Global Constraints
- minSdk 29; targetSdk 35; compileSdk 35.
- No recursive filesystem scan.
- Media queries and bitmap decode must not run on main thread.
- Image decode must sample down to screen-scale memory.
- Delete on Android 11+ must use user-approved MediaStore delete requests.

---

### Task 1: Decode sampling core
**Files:** `gallery/app/src/test/kotlin/com/atmaca/gallery/ImageSampleTest.kt`, `gallery/app/src/main/kotlin/com/atmaca/gallery/ImageSample.kt`.
**Interfaces:** `ImageSample.compute(sourceW:Int, sourceH:Int, targetW:Int, targetH:Int): Int`.
- [ ] Write tests for 1x, 2x, 4x sampling.
- [ ] Run tests RED before implementation.
- [ ] Implement power-of-two sampling.
- [ ] Run tests GREEN.

### Task 2: MediaStore repository and album UI
**Files:** `MediaRepository.kt`, `MainActivity.kt`, `AlbumAdapter.kt`.
**Interfaces:** `AlbumInfo(path,name,count)`, `MediaItem(uri,name,mime,path,dateModified)`, `albums()`, `items(path)`.
- [ ] Query only MediaStore image/video projections on IO dispatcher.
- [ ] Render album rows in RecyclerView and open AlbumActivity.
- [ ] Add Android 13 media permission flow.

### Task 3: Media grid and viewer
**Files:** `AlbumActivity.kt`, `MediaGridAdapter.kt`, `ViewerActivity.kt`, `ViewerAdapter.kt`, `ZoomImageView.kt`, `BitmapLoader.kt`.
**Interfaces:** album grid opens viewer at item index; viewer pages support images/videos.
- [ ] Load thumbnails with per-holder coroutine Job cancellation.
- [ ] Implement multi-selection UI.
- [ ] Implement sampled bitmap load, pinch zoom, drag, double-tap reset/zoom.
- [ ] Implement VideoView page with MediaController.

### Task 4: File operations
**Files:** `FileOps.kt`; modify `AlbumActivity.kt`.
**Interfaces:** `copyToTree(items, treeUri)` returns copied count; destructive delete uses MediaStore delete request.
- [ ] Add ACTION_OPEN_DOCUMENT_TREE for copy/move.
- [ ] Stream-copy to DocumentFile without loading files into RAM.
- [ ] For move, only request deletion after all copies attempted.
- [ ] Refresh album after successful operations/system delete result.

### Task 5: CI artifact
**Files:** `.github/workflows/lite-gallery-build.yml`.
- [ ] Run `testDebugUnitTest assembleDebug` on Java 17 / Gradle 8.10.2.
- [ ] Upload `gallery/app/build/outputs/apk/debug/app-debug.apk` as `Hafif-Galeri-APK`.
- [ ] Download and verify APK ZIP integrity before delivery.
