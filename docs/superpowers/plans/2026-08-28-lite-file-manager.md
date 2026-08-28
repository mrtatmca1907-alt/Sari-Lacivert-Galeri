# Lite File Manager Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a lightweight Samsung My Files-inspired Android file manager that is fast, safe for copy/move/delete operations, and compatible with the gallery storage model.

**Architecture:** Native Kotlin Android app using Storage Access Framework / DocumentFile for user-selected storage roots and direct folder browsing without whole-device recursive scans. UI is a lightweight category home plus folder browser; file mutations are performed off the main thread and move deletes source only after destination copy succeeds.

**Tech Stack:** Kotlin 2.0.21, Android SDK 35, AndroidX RecyclerView, DocumentFile, Kotlin Coroutines, JUnit 4.

**Spec:** `docs/superpowers/specs/2026-08-28-lite-file-manager-design.md`

## Global Constraints

- Native Kotlin Android app.
- No recursive full-disk scan on launch.
- Browse only the current folder and explicitly requested categories.
- Core operations: open, multi-select, copy, move, rename, delete, create folder, sort/search.
- Move must never delete source before destination write is verified successful.
- SAF/DocumentFile semantics must remain compatible with the gallery app.
- Samsung-inspired layout only; no Samsung source/assets.

---

### Task 1: Project shell and safe operation rules

**Files:**
- Create: `filemanager/settings.gradle.kts`
- Create: `filemanager/build.gradle.kts`
- Create: `filemanager/gradle.properties`
- Create: `filemanager/app/build.gradle.kts`
- Create: `filemanager/app/src/main/AndroidManifest.xml`
- Create: `filemanager/app/src/test/kotlin/com/atmaca/files/TransferRulesTest.kt`
- Create: `filemanager/app/src/main/kotlin/com/atmaca/files/TransferRules.kt`

**Interfaces:**
- Produces: `TransferRules.canDeleteSource(copySucceeded: Boolean, destinationExists: Boolean): Boolean`

- [ ] **Step 1: Write the failing test**
```kotlin
class TransferRulesTest {
    @Test fun source_is_deleted_only_after_verified_copy() {
        assertFalse(TransferRules.canDeleteSource(false, false))
        assertFalse(TransferRules.canDeleteSource(true, false))
        assertTrue(TransferRules.canDeleteSource(true, true))
    }
}
```
- [ ] **Step 2: Run test to verify it fails**
Run: `gradle testDebugUnitTest --tests com.atmaca.files.TransferRulesTest`
Expected: FAIL because `TransferRules` does not exist.
- [ ] **Step 3: Write minimal implementation**
```kotlin
object TransferRules {
    fun canDeleteSource(copySucceeded: Boolean, destinationExists: Boolean) = copySucceeded && destinationExists
}
```
- [ ] **Step 4: Run test to verify it passes**
Run: `gradle testDebugUnitTest --tests com.atmaca.files.TransferRulesTest`
Expected: PASS.
- [ ] **Step 5: Commit**
```bash
git add filemanager
git commit -m "feat: scaffold lite file manager"
```

### Task 2: SAF root selection and folder browser

**Files:**
- Create: `filemanager/app/src/main/kotlin/com/atmaca/files/MainActivity.kt`
- Create: `filemanager/app/src/main/kotlin/com/atmaca/files/BrowserActivity.kt`
- Create: `filemanager/app/src/main/kotlin/com/atmaca/files/FileEntry.kt`
- Create: `filemanager/app/src/main/kotlin/com/atmaca/files/FileAdapter.kt`
- Create: `filemanager/app/src/main/kotlin/com/atmaca/files/StorageRepository.kt`

**Interfaces:**
- Produces: `StorageRepository.children(uri: Uri): List<FileEntry>` and `FileEntry` model.

- [ ] **Step 1: Write failing sort/filter test** for directories-first alphabetical ordering.
- [ ] **Step 2: Run it and verify RED.**
- [ ] **Step 3: Implement current-folder-only DocumentFile listing and sorting.**
- [ ] **Step 4: Run tests and verify GREEN.**
- [ ] **Step 5: Commit** browser shell.

### Task 3: File operations

**Files:**
- Create: `filemanager/app/src/main/kotlin/com/atmaca/files/FileOperations.kt`
- Modify: `filemanager/app/src/main/kotlin/com/atmaca/files/BrowserActivity.kt`

**Interfaces:**
- Produces: `copy`, `move`, `delete`, `rename`, `createFolder` suspend operations.

- [ ] **Step 1: Write failing tests for operation decision rules.**
- [ ] **Step 2: Verify RED.**
- [ ] **Step 3: Implement streaming copy with buffered I/O; move copies first and deletes only after verified destination.**
- [ ] **Step 4: Verify GREEN and run full unit suite.**
- [ ] **Step 5: Commit** file operations.

### Task 4: Samsung-inspired home and categories

**Files:**
- Modify: `filemanager/app/src/main/kotlin/com/atmaca/files/MainActivity.kt`
- Create: `filemanager/app/src/main/kotlin/com/atmaca/files/Category.kt`

**Interfaces:**
- Produces: home cards for Images, Videos, Audio, Documents, Downloads, APKs and Storage.

- [ ] **Step 1: Write failing category mapping test.**
- [ ] **Step 2: Verify RED.**
- [ ] **Step 3: Implement category cards and lightweight MIME/extension filters without recursive startup scans.**
- [ ] **Step 4: Verify GREEN.**
- [ ] **Step 5: Commit** home UI.

### Task 5: CI, APK and verification

**Files:**
- Create: `.github/workflows/lite-file-manager-build.yml`

**Interfaces:**
- Produces: debug APK artifact `Hafif-Dosya-Yoneticisi-APK`.

- [ ] **Step 1: Run** `gradle testDebugUnitTest assembleDebug --stacktrace`.
- [ ] **Step 2: Fix any root-cause build/test failures.**
- [ ] **Step 3: Upload APK artifact from `filemanager/app/build/outputs/apk/debug/app-debug.apk`.**
- [ ] **Step 4: Download artifact and run APK ZIP integrity verification.**
- [ ] **Step 5: Deliver verified APK to the user.**
