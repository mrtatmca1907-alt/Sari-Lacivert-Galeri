# ATMACA File Manager Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a native Android 13+ file manager with reliable media display, non-blocking delete, verified move/copy, and no post-operation whole-storage scanning.

**Architecture:** Add a dedicated `filemanager` application module using Compose. File access is isolated behind a repository; all mutating operations run through a coroutine-based engine on `Dispatchers.IO`, with targeted refresh events for only affected directories and MediaStore rows.

**Tech Stack:** Kotlin, Android Gradle Plugin 9.3, Jetpack Compose, lifecycle ViewModel, coroutines, MediaStore, Storage Access Framework/DocumentFile.

**Spec:** `docs/superpowers/specs/2026-09-01-atmaca-file-manager-design.md`

## Global Constraints
- Android 13+ behavior must work correctly; minSdk 26 remains acceptable.
- No root requirement.
- No whole-device or whole-storage scan after move/copy/delete/rename.
- UI thread performs no filesystem traversal or file mutation.
- Move success is reported only after destination verification and source removal.
- Rotation must not restart active file operations.

---

### Task 1: Module and operation contracts

**Files:**
- Modify: `settings.gradle.kts`
- Create: `filemanager/build.gradle.kts`
- Create: `filemanager/src/main/AndroidManifest.xml`
- Create: `filemanager/src/main/java/com/atmaca/filemanager/core/FileModels.kt`
- Test: `filemanager/src/test/java/com/atmaca/filemanager/core/FileModelsTest.kt`

**Interfaces:**
- Produces: `FileEntry`, `OperationType`, `OperationResult`, `RefreshScope`.

- [ ] Write tests proving `RefreshScope` can represent targeted source/destination refresh without a global-scan option.
- [ ] Run tests and verify they fail before production types exist.
- [ ] Add the minimal module and model types.
- [ ] Run tests and verify pass.
- [ ] Commit.

### Task 2: File operation engine

**Files:**
- Create: `filemanager/src/main/java/com/atmaca/filemanager/core/FileOperationEngine.kt`
- Create: `filemanager/src/main/java/com/atmaca/filemanager/core/LocalFileBackend.kt`
- Test: `filemanager/src/test/java/com/atmaca/filemanager/core/FileOperationEngineTest.kt`

**Interfaces:**
- Consumes: `OperationType`, `OperationResult`, `RefreshScope`.
- Produces: `suspend fun move(...)`, `copy(...)`, `delete(...)` and targeted refresh results.

- [ ] Write failing tests for same-filesystem move, cross-filesystem copy-verify-delete semantics, delete, and targeted refresh only.
- [ ] Verify tests fail.
- [ ] Implement minimal backend and engine on blocking-safe interfaces.
- [ ] Verify tests pass.
- [ ] Commit.

### Task 3: Directory/media repository

**Files:**
- Create: `filemanager/src/main/java/com/atmaca/filemanager/data/FileRepository.kt`
- Create: `filemanager/src/main/java/com/atmaca/filemanager/data/MediaStoreUpdater.kt`
- Test: `filemanager/src/test/java/com/atmaca/filemanager/data/FileRepositoryTest.kt`

**Interfaces:**
- Produces: paged/chunked directory listing and `refreshDirectories(paths: Set<String>)`.

- [ ] Write failing tests proving directory listing is sorted and refresh never requests full storage enumeration.
- [ ] Verify failure.
- [ ] Implement repository with `Dispatchers.IO` and targeted MediaStore updates.
- [ ] Verify tests pass.
- [ ] Commit.

### Task 4: ViewModel stability and queue control

**Files:**
- Create: `filemanager/src/main/java/com/atmaca/filemanager/ui/FileManagerViewModel.kt`
- Test: `filemanager/src/test/java/com/atmaca/filemanager/ui/FileManagerViewModelTest.kt`

**Interfaces:**
- Produces lifecycle-stable browser state and one active operation per source item.

- [ ] Write failing tests for duplicate operation suppression, rotation-safe state ownership, and debounced targeted refresh.
- [ ] Verify failure.
- [ ] Implement ViewModel queue/state logic.
- [ ] Verify tests pass.
- [ ] Commit.

### Task 5: Browser UI and thumbnails

**Files:**
- Create: `filemanager/src/main/java/com/atmaca/filemanager/MainActivity.kt`
- Create: `filemanager/src/main/java/com/atmaca/filemanager/ui/FileBrowserScreen.kt`
- Create: `filemanager/src/main/java/com/atmaca/filemanager/ui/ThumbnailLoader.kt`
- Create: `filemanager/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes ViewModel browser state and operations.
- Produces list/grid browser, multi-select, move/copy/delete/rename/create-folder actions and media thumbnails.

- [ ] Build browser UI with no disk work in composables.
- [ ] Add bounded/cancellable thumbnail loading.
- [ ] Add simple target-folder picker without an “Add to directory” step.
- [ ] Verify assembleDebug.
- [ ] Commit.

### Task 6: CI build and stress checks

**Files:**
- Create: `.github/workflows/build-atmaca-file-manager.yml`

**Interfaces:**
- Produces installable debug APK artifact.

- [ ] Add CI steps for unit tests and `:filemanager:assembleDebug`.
- [ ] Run workflow.
- [ ] Inspect failures and fix root causes only.
- [ ] Confirm artifact exists before declaring completion.
- [ ] Commit.
