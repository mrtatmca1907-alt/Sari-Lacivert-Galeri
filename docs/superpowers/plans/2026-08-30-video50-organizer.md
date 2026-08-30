# Video50 Organizer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a standalone Android APK that discovers videos by selected folder or whole-phone scan and moves them into `Movies/Video N` folders in groups of 50.

**Architecture:** A small Java Android app uses MediaStore for whole-phone discovery and SAF for optional source-folder traversal. A pure-Java grouping policy determines `Video N` buckets; a move engine writes into the phone's public Movies collection and deletes each source only after the destination is safely written.

**Tech Stack:** Android SDK 35, Java 17, AppCompat, Material, JUnit 4, Gradle 8.9.

**Spec:** `docs/superpowers/specs/2026-08-30-video50-organizer-design.md`

## Global Constraints
- New standalone APK; do not modify ATMACA runtime files.
- Move only; source must disappear after successful move.
- Max 50 videos per generated folder.
- Destination is fixed to public `Movies/Video N`; no destination picker.
- Never delete source before successful destination write.
- minSdk 26, targetSdk 35.

---

### Task 1: Standalone Android project and grouping policy

**Files:**
- Create: `VIDEO50/settings.gradle.kts`
- Create: `VIDEO50/build.gradle.kts`
- Create: `VIDEO50/app/build.gradle.kts`
- Create: `VIDEO50/app/src/main/java/com/atmaca/video50/BatchPolicy.java`
- Test: `VIDEO50/app/src/test/java/com/atmaca/video50/BatchPolicyTest.java`

**Interfaces:**
- Produces: `BatchPolicy.folderNameForIndex(int zeroBasedIndex): String`
- Produces: `BatchPolicy.relativeMoviesPathForIndex(int zeroBasedIndex): String`

- [ ] **Step 1: Write failing tests**
```java
@Test public void groupsEveryFifty() {
  assertEquals("Video 1", BatchPolicy.folderNameForIndex(0));
  assertEquals("Video 1", BatchPolicy.folderNameForIndex(49));
  assertEquals("Video 2", BatchPolicy.folderNameForIndex(50));
  assertEquals("Video 7", BatchPolicy.folderNameForIndex(326));
  assertEquals("Movies/Video 2", BatchPolicy.relativeMoviesPathForIndex(50));
}
```
- [ ] **Step 2:** Run `cd VIDEO50 && gradle :app:testDebugUnitTest` and verify failure because `BatchPolicy` is missing.
- [ ] **Step 3: Implement minimal policy**
```java
public static String folderNameForIndex(int i) {
  if (i < 0) throw new IllegalArgumentException("index");
  return "Video " + ((i / 50) + 1);
}
public static String relativeMoviesPathForIndex(int i) {
  return "Movies/" + folderNameForIndex(i);
}
```
- [ ] **Step 4:** Run unit tests and verify pass.
- [ ] **Step 5:** Commit.

### Task 2: Video discovery

**Files:**
- Create: `VIDEO50/app/src/main/java/com/atmaca/video50/VideoItem.java`
- Create: `VIDEO50/app/src/main/java/com/atmaca/video50/VideoScanner.java`
- Create: `VIDEO50/app/src/main/java/com/atmaca/video50/DedupePolicy.java`
- Test: `VIDEO50/app/src/test/java/com/atmaca/video50/DedupePolicyTest.java`

**Interfaces:**
- Produces: immutable `VideoItem(Uri uri, String name, long size, String relativePath)`.
- Produces: `VideoScanner.scanMediaStore(Context)` and `VideoScanner.scanTree(Context, Uri)` returning unique items.

- [ ] **Step 1:** Write a pure-Java failing test proving duplicate keys are emitted once.
- [ ] **Step 2:** Run tests and verify RED.
- [ ] **Step 3:** Implement URI-key de-duplication and Android scanners; exclude items already under generated `Movies/Video N` folders during whole-phone scan.
- [ ] **Step 4:** Run all unit tests GREEN.
- [ ] **Step 5:** Commit.

### Task 3: Safe Movies move engine

**Files:**
- Create: `VIDEO50/app/src/main/java/com/atmaca/video50/MoveEngine.java`
- Create: `VIDEO50/app/src/main/java/com/atmaca/video50/MoveJournal.java`
- Test: `VIDEO50/app/src/test/java/com/atmaca/video50/MoveJournalTest.java`

**Interfaces:**
- `MoveEngine.move(Context, List<VideoItem>, ProgressCallback)`.
- Android 10+: create destination in MediaStore using `RELATIVE_PATH = BatchPolicy.relativeMoviesPathForIndex(index)`.
- Android 9 and below: create files under public `Movies/Video N`.
- Delete a source only after destination stream closes successfully.

- [ ] **Step 1:** Write journal/group-state tests first.
- [ ] **Step 2:** Verify RED.
- [ ] **Step 3:** Implement journal and safe move loop; on write failure delete partial destination if possible and keep source untouched.
- [ ] **Step 4:** Verify tests GREEN.
- [ ] **Step 5:** Commit.

### Task 4: Android permissions and single-screen UI

**Files:**
- Create: `VIDEO50/app/src/main/AndroidManifest.xml`
- Create: `VIDEO50/app/src/main/java/com/atmaca/video50/PermissionPolicy.java`
- Create: `VIDEO50/app/src/main/java/com/atmaca/video50/MainActivity.java`
- Create: `VIDEO50/app/src/main/res/values/themes.xml`
- Create: `VIDEO50/app/src/main/res/values/strings.xml`
- Test: `VIDEO50/app/src/test/java/com/atmaca/video50/PermissionPolicyTest.java`

**Interfaces:**
- Buttons: `Kaynak Klasör Seç`, `Telefonu Tara`, `50'şerli Ayır ve Movies'e Taşı`.
- Shows found, moved, remaining, current `Movies/Video N`, and errors.

- [ ] **Step 1:** Add permission-state unit test for SDK 33+ vs older.
- [ ] **Step 2:** Verify RED.
- [ ] **Step 3:** Implement manifest and Activity Result flows for source tree, media permission, scan, confirmation, and move.
- [ ] **Step 4:** Run unit tests and assemble debug APK.
- [ ] **Step 5:** Commit.

### Task 5: CI build and artifact

**Files:**
- Create: `.github/workflows/video50-organizer.yml`

**Interfaces:**
- Produces artifact `ATMACA-Video50.apk`.

- [ ] **Step 1:** Workflow checks out branch, installs Java 17/Android SDK/Gradle 8.9.
- [ ] **Step 2:** Run `gradle :app:testDebugUnitTest`.
- [ ] **Step 3:** Run `gradle :app:assembleDebug`.
- [ ] **Step 4:** Rename artifact to `ATMACA-Video50.apk` and upload.
- [ ] **Step 5:** Confirm GitHub Actions is green and artifact exists before delivery.

## Self-review
Coverage: separate APK, both discovery modes, fixed public Movies destination, 50-item grouping, move-only semantics, no premature deletion, Android permissions, progress UI, CI artifact are all mapped to tasks. Interfaces and names are consistent across tasks.