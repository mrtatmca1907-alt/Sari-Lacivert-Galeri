# ATMACA File Manager Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a stable local Android file manager with responsive browsing and safe file operations.

**Architecture:** A single-activity Android app owns UI only. Directory loads and file operations run on bounded executors, while a ViewModel holds navigation state across rotation. File classification, sorting, collision naming, and operation helpers are isolated and unit-tested.

**Tech Stack:** Android SDK, Java 17, RecyclerView/AppCompat, AndroidX Lifecycle, AGP 9.3.0, Gradle 9.5.0, JUnit 4.

**Spec:** `docs/superpowers/specs/2026-08-29-atmaca-file-manager-design.md`

## Global Constraints
- compileSdk 36, targetSdk 36, minSdk 23.
- App id `com.atmaca.filemanager`.
- Never do directory enumeration or file copy/move/delete on the UI thread.
- One corrupt/inaccessible file must not abort the whole folder or batch.
- Thumbnail memory must be bounded.
- V1 excludes FTP/SMB/WebDAV/cloud/root.

---

### Task 1: Android project + classification core

**Files:**
- Create: `atmaca-file-manager/settings.gradle`
- Create: `atmaca-file-manager/build.gradle`
- Create: `atmaca-file-manager/app/build.gradle`
- Create: `atmaca-file-manager/app/src/main/AndroidManifest.xml`
- Create: `atmaca-file-manager/app/src/main/java/com/atmaca/filemanager/FileTypes.java`
- Test: `atmaca-file-manager/app/src/test/java/com/atmaca/filemanager/FileTypesTest.java`

**Interfaces:**
- Produces `FileTypes.categoryOf(String)` and `FileTypes.isPreviewable(String)`.

- [ ] Write JUnit tests for image, video, document, APK, archive, and other classification.
- [ ] Run `gradle -p atmaca-file-manager testDebugUnitTest`; expect red before implementation.
- [ ] Implement `FileTypes` with case-insensitive extension matching.
- [ ] Run unit tests; expect green.
- [ ] Commit.

### Task 2: Safe naming and sorted directory model

**Files:**
- Create: `atmaca-file-manager/app/src/main/java/com/atmaca/filemanager/FileEntry.java`
- Create: `atmaca-file-manager/app/src/main/java/com/atmaca/filemanager/FileRules.java`
- Test: `atmaca-file-manager/app/src/test/java/com/atmaca/filemanager/FileRulesTest.java`

**Interfaces:**
- Produces `FileRules.uniqueTarget(File dir, String name)` and `FileRules.ENTRY_COMPARATOR`.

- [ ] Write tests proving folders sort before files and collision names become `x (2).ext`.
- [ ] Run tests; expect red.
- [ ] Implement immutable `FileEntry` and `FileRules`.
- [ ] Run tests; expect green.
- [ ] Commit.

### Task 3: Background directory loader and ViewModel

**Files:**
- Create: `atmaca-file-manager/app/src/main/java/com/atmaca/filemanager/BrowserViewModel.java`

**Interfaces:**
- Produces observable `BrowserState`, `open(File)`, `refresh()`, `goUp()`, `search(String)`, and `shutdown()`.

- [ ] Implement one single-thread directory executor plus monotonically increasing generation token.
- [ ] Enumerate only the current folder, catch errors per entry, sort immutable results.
- [ ] Publish loading/error/result state through LiveData.
- [ ] Verify project compiles.
- [ ] Commit.

### Task 4: Safe serialized copy/move/delete/rename

**Files:**
- Create: `atmaca-file-manager/app/src/main/java/com/atmaca/filemanager/FileOperations.java`

**Interfaces:**
- Produces asynchronous `copy`, `move`, `delete`, `rename`, `mkdir` using one worker.

- [ ] Add tests for collision naming dependency and path validation where host-JVM safe.
- [ ] Implement buffered recursive copy with per-file failure collection.
- [ ] Implement move as rename first then copy/delete fallback.
- [ ] Implement delete/rename/mkdir and result summaries.
- [ ] Run tests and compile.
- [ ] Commit.

### Task 5: Responsive browser UI

**Files:**
- Create: `atmaca-file-manager/app/src/main/java/com/atmaca/filemanager/MainActivity.java`
- Create: `atmaca-file-manager/app/src/main/java/com/atmaca/filemanager/FileAdapter.java`
- Create: `atmaca-file-manager/app/src/main/res/values/colors.xml`
- Create: `atmaca-file-manager/app/src/main/res/values/styles.xml`
- Create: `atmaca-file-manager/app/src/main/res/drawable/ic_launcher.xml`

**Interfaces:**
- MainActivity observes BrowserViewModel and only submits immutable lists to adapter.

- [ ] Build sarı-lacivert programmatic layout with path bar, search, storage permission, categories, refresh, and file list.
- [ ] Add RecyclerView adapter with stable IDs and lightweight type icons; no full-resolution bitmap decoding.
- [ ] Add long-press multi-select and copy/move/delete/rename/new-folder actions.
- [ ] Handle Android 11+ All Files Access and Android <=10 storage permission.
- [ ] Compile debug APK.
- [ ] Commit.

### Task 6: CI build and artifact verification

**Files:**
- Create: `.github/workflows/atmaca-file-manager.yml`

**Interfaces:**
- Produces artifact `ATMACA-Dosya-Yoneticisi-v1.apk`.

- [ ] Configure Java 17 and Gradle 9.5.0.
- [ ] Run unit tests.
- [ ] Assemble debug.
- [ ] Copy APK to stable artifact filename and upload.
- [ ] Verify workflow success, unzip-test the APK, compute SHA-256, and deliver the exact artifact.
