# Video Kareleri V6 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a minimal Android batch video-frame extractor that outputs one JPEG per second, deletes successful source videos, and moves failed source videos into `HATA`.

**Architecture:** A plain Android Activity owns folder selection and status display. A foreground `Service` owns the entire batch, processes direct child videos sequentially with `MediaMetadataRetriever`, writes JPEGs through SAF `DocumentFile`, and handles terminal source-file movement/deletion. Pure filename/frame-count rules are isolated in `FrameRules` and unit tested.

**Tech Stack:** Java, Android SDK 37, targetSdk 33, AndroidX DocumentFile, JUnit 4, GitHub Actions, Gradle 9.5.

**Spec:** `docs/superpowers/specs/2026-09-08-video-kareleri-v6-design.md`

## Global Constraints
- Android 13 is the primary runtime target.
- No recursive scan.
- One video is processed at a time.
- One JPEG is produced for each second of video duration.
- Successful source video is deleted after frame output completes.
- Failed source video is moved to `HATA` only after a complete error-copy succeeds.
- No face/ML/crop/gallery features.
- Progress UI is throttled to avoid slowing extraction.

---

### Task 1: Pure frame rules

**Files:**
- Create: `videoframes/src/main/java/com/atmaca/videokareleri/FrameRules.java`
- Test: `videoframes/src/test/java/com/atmaca/videokareleri/FrameRulesTest.java`

**Interfaces:**
- Produces: `frameCount(long)`, `baseName(String)`, `frameName(String,int)`, `isVideo(String,String)`.

- [x] **Step 1: Write failing tests** for 10-second count, partial last second, zero duration, extension stripping and sortable JPEG names.
- [x] **Step 2: Implement minimal `FrameRules`** using ceiling division and six-digit numbering.
- [ ] **Step 3: Run** `gradle :videoframes:testDebugUnitTest --stacktrace` and require PASS.

### Task 2: Folder picker and stable foreground batch

**Files:**
- Create: `videoframes/src/main/java/com/atmaca/videokareleri/MainActivity.java`
- Create: `videoframes/src/main/java/com/atmaca/videokareleri/FrameExtractService.java`
- Create: `videoframes/src/main/AndroidManifest.xml`
- Create: `videoframes/build.gradle.kts`
- Modify: `settings.gradle.kts`

**Interfaces:**
- Activity sends `ACTION_START` with persisted tree URI.
- Service broadcasts `ACTION_PROGRESS` with `message`, `done`, `total`, `finished`.

- [x] **Step 1: Implement SAF folder selection** with persistent read/write URI permission.
- [x] **Step 2: Enumerate only direct child videos** and process them sequentially.
- [x] **Step 3: Extract frames** at 1,000,000 microsecond intervals with `MediaMetadataRetriever.OPTION_CLOSEST`.
- [x] **Step 4: Write JPEG quality 90** into the video-base-name folder, replacing same-name frame files on retry.
- [x] **Step 5: Delete successful source video** after all JPEGs finish.
- [x] **Step 6: On extraction failure, copy to `HATA` then delete original** only after copy completion.
- [x] **Step 7: Keep the job alive** with foreground notification and partial wake lock.

### Task 3: CI build and artifact

**Files:**
- Create: `.github/workflows/build-video-kareleri-v6.yml`

- [x] **Step 1: Install SDK 37 and Gradle 9.5** in GitHub Actions.
- [x] **Step 2: Run unit tests before APK build.**
- [x] **Step 3: Build** `:videoframes:assembleDebug`.
- [x] **Step 4: Upload** `videoframes-debug.apk` as `Video-Kareleri-V6-APK`.
- [ ] **Step 5: Verify workflow success and inspect APK structure** before delivering.
