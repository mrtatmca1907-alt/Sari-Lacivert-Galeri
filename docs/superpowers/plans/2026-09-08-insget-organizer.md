# Insget Organizer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build an installable Android utility that moves Insget images and videos into base-name folders.

**Architecture:** A minimal single-activity Java Android app delegates filename parsing and file moves to focused utility classes. It uses fixed `Pictures/Insget` and `Movies/Insget` paths, All files access on Android 11+, and a background executor to keep the UI responsive.

**Tech Stack:** Android SDK 35, Java 17, Android Gradle Plugin 8.7.3, Gradle 8.9, JUnit 4.

**Spec:** `docs/superpowers/specs/2026-09-08-insget-organizer-design.md`

## Global Constraints
- Process only direct files in `Pictures/Insget` and `Movies/Insget`.
- Move rather than copy.
- Never decode media or recursively scan generated folders.
- Preserve underscores in real names while removing trailing numeric suffixes.
- Never overwrite an existing destination file.
- Keep UI responsive and show moved / skipped / error totals.

---

### Task 1: Project and grouping rule

**Files:**
- Create: `InsgetOrganizer/settings.gradle`
- Create: `InsgetOrganizer/build.gradle`
- Create: `InsgetOrganizer/app/build.gradle`
- Create: `InsgetOrganizer/app/src/main/java/com/atmaca/insgetorganizer/GroupName.java`
- Test: `InsgetOrganizer/app/src/test/java/com/atmaca/insgetorganizer/GroupNameTest.java`

**Interfaces:**
- Produces: `GroupName.fromFileName(String): String`

- [ ] **Step 1: Write failing tests** for simple names, underscore names, multi-number suffixes and files without numeric suffixes.
- [ ] **Step 2: Run** `gradle :app:testDebugUnitTest` and verify the grouping tests fail before implementation.
- [ ] **Step 3: Implement** `GroupName.fromFileName` by stripping the extension and trailing `[_-]digits` segments only.
- [ ] **Step 4: Run** `gradle :app:testDebugUnitTest` and verify tests pass.

### Task 2: File organizer engine

**Files:**
- Create: `InsgetOrganizer/app/src/main/java/com/atmaca/insgetorganizer/Organizer.java`

**Interfaces:**
- Consumes: `GroupName.fromFileName(String)`
- Produces: `Organizer.Result organize(File picturesInsget, File moviesInsget, ProgressListener listener)`

- [ ] **Step 1:** Filter only supported image extensions in Pictures and supported video extensions in Movies.
- [ ] **Step 2:** For each direct child file, derive destination folder and create it only when needed.
- [ ] **Step 3:** Resolve filename collisions by appending `(n)` before the extension.
- [ ] **Step 4:** Move with `Files.move`; count moved, skipped and errors without stopping the entire job.

### Task 3: Android UI and permissions

**Files:**
- Create: `InsgetOrganizer/app/src/main/AndroidManifest.xml`
- Create: `InsgetOrganizer/app/src/main/res/layout/activity_main.xml`
- Create: `InsgetOrganizer/app/src/main/res/values/strings.xml`
- Create: `InsgetOrganizer/app/src/main/res/values/themes.xml`
- Create: `InsgetOrganizer/app/src/main/java/com/atmaca/insgetorganizer/MainActivity.java`

**Interfaces:**
- Consumes: `Organizer.organize(...)`

- [ ] **Step 1:** Declare All files access and compatible legacy storage permissions.
- [ ] **Step 2:** Build one-screen UI with permission state, source paths, start button and counters.
- [ ] **Step 3:** If All files access is missing, open the app-specific Android settings page.
- [ ] **Step 4:** Run organizer on a single background executor and update UI on the main thread.
- [ ] **Step 5:** Disable the start button while work is running and restore it when complete.

### Task 4: CI APK build

**Files:**
- Create: `.github/workflows/build-insget-organizer.yml`

**Interfaces:**
- Produces: installable `app-debug.apk` as GitHub Actions artifact `Insget-Organizer-APK`.

- [ ] **Step 1:** Configure JDK 17, Gradle 8.9 and Android SDK 35.
- [ ] **Step 2:** Run unit tests and `assembleDebug` from `InsgetOrganizer`.
- [ ] **Step 3:** Upload `InsgetOrganizer/app/build/outputs/apk/debug/app-debug.apk`.
- [ ] **Step 4:** Inspect workflow result; if build fails, fix code/build configuration and rerun until green.