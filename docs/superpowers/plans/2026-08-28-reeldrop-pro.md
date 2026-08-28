# ReelDrop Pro Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build one resilient Android 13 downloader app for Instagram public profiles, Instagram hashtags, and Facebook public media with durable queueing, recovery, diagnostics, and bounded resource usage.

**Architecture:** Native Kotlin app under `reeldroppro/`. Room stores jobs and completed items, a foreground `dataSync` service owns active work, WorkManager restores deferred work, platform adapters produce normalized extractor requests, and a shared engine executes yt-dlp/FFmpeg/aria2 only off the main thread. Media is written to deterministic platform/source/photo-or-video folders and partial files are never treated as complete.

**Tech Stack:** Kotlin 2.0.21, Android Gradle Plugin 8.7.3, compile/target SDK 35, min SDK 26, Coroutines, Room, WorkManager, youtubedl-android 0.18.1 + FFmpeg + aria2c, JUnit 4.

**Spec:** `docs/superpowers/specs/2026-08-28-reeldrop-pro-design.md`

## Global Constraints

- Primary runtime target: TECNO Spark 10 Pro, Android 13, Helio G88, 8 GB physical RAM.
- APK size is not a constraint when bundled components materially improve reliability or recovery.
- Native Kotlin is the application runtime; no Python application runtime.
- UI thread performs no extractor, network, database, FFmpeg, storage enumeration, or large parsing work.
- Long jobs survive UI closure and normal process recreation.
- Network loss pauses/retries instead of silently losing jobs.
- No permanent Wi-Fi high-performance lock or permanent wake lock.
- arm64-v8a is the production ABI target.

---

### Task 1: Project shell and domain rules

**Files:**
- Create: `reeldroppro/settings.gradle.kts`
- Create: `reeldroppro/build.gradle.kts`
- Create: `reeldroppro/gradle.properties`
- Create: `reeldroppro/app/build.gradle.kts`
- Create: `reeldroppro/app/src/main/AndroidManifest.xml`
- Create: `reeldroppro/app/src/main/kotlin/com/atmaca/reeldroppro/model/DownloadModels.kt`
- Create: `reeldroppro/app/src/main/kotlin/com/atmaca/reeldroppro/core/InputParser.kt`
- Test: `reeldroppro/app/src/test/kotlin/com/atmaca/reeldroppro/core/InputParserTest.kt`

**Interfaces:**
- Produces: `Platform`, `JobState`, `ParsedInput`, `InputParser.parse(mode: Platform, raw: String): List<ParsedInput>`.

- [ ] Write tests covering Instagram profile URL/username, hashtag with or without `#`, Facebook URLs, dedupe, and rejection of unsupported hosts.
- [ ] Run `gradle testDebugUnitTest` and verify RED before implementation.
- [ ] Implement the parser and domain enums/data classes.
- [ ] Run tests and verify GREEN.
- [ ] Commit project shell and parser.

### Task 2: Durable queue, retry rules, and duplicate index

**Files:**
- Create: `reeldroppro/app/src/main/kotlin/com/atmaca/reeldroppro/data/AppDatabase.kt`
- Create: `reeldroppro/app/src/main/kotlin/com/atmaca/reeldroppro/data/JobEntity.kt`
- Create: `reeldroppro/app/src/main/kotlin/com/atmaca/reeldroppro/data/JobDao.kt`
- Create: `reeldroppro/app/src/main/kotlin/com/atmaca/reeldroppro/data/CompletedItemEntity.kt`
- Create: `reeldroppro/app/src/main/kotlin/com/atmaca/reeldroppro/data/CompletedItemDao.kt`
- Create: `reeldroppro/app/src/main/kotlin/com/atmaca/reeldroppro/core/RetryPolicy.kt`
- Test: `reeldroppro/app/src/test/kotlin/com/atmaca/reeldroppro/core/RetryPolicyTest.kt`

**Interfaces:**
- Produces: persistent `JobEntity` queue, completed-item unique key `(platform, sourceKey, mediaKey)`, and `RetryPolicy.nextDelayMs(attempt: Int, retryable: Boolean): Long?`.

- [ ] Write failing retry-policy tests for exponential capped delays and non-retryable errors.
- [ ] Run tests and verify RED.
- [ ] Implement Room schema/DAOs and retry policy.
- [ ] Run tests and verify GREEN.
- [ ] Commit persistence layer.

### Task 3: Error classification and extractor request building

**Files:**
- Create: `reeldroppro/app/src/main/kotlin/com/atmaca/reeldroppro/engine/DownloadError.kt`
- Create: `reeldroppro/app/src/main/kotlin/com/atmaca/reeldroppro/engine/ErrorClassifier.kt`
- Create: `reeldroppro/app/src/main/kotlin/com/atmaca/reeldroppro/engine/ExtractorRequestFactory.kt`
- Test: `reeldroppro/app/src/test/kotlin/com/atmaca/reeldroppro/engine/ErrorClassifierTest.kt`
- Test: `reeldroppro/app/src/test/kotlin/com/atmaca/reeldroppro/engine/ExtractorRequestFactoryTest.kt`

**Interfaces:**
- Produces: `DownloadError`, `ErrorClassifier.classify(stderr: String, throwable: Throwable?): DownloadError`, `ExtractorRequestFactory.build(input: ParsedInput, outputTemplate: String): YoutubeDLRequest`.

- [ ] Write failing tests for private/auth, removed, rate-limit, network, storage-full, unsupported, and generic extractor errors.
- [ ] Write failing tests verifying profile/hashtag/Facebook request options and output templates.
- [ ] Implement classifier and request factory.
- [ ] Run tests and verify GREEN.
- [ ] Commit engine rules.

### Task 4: Foreground coordinator and recovery

**Files:**
- Create: `reeldroppro/app/src/main/kotlin/com/atmaca/reeldroppro/engine/ExtractorEngine.kt`
- Create: `reeldroppro/app/src/main/kotlin/com/atmaca/reeldroppro/service/DownloadService.kt`
- Create: `reeldroppro/app/src/main/kotlin/com/atmaca/reeldroppro/worker/ResumeWorker.kt`
- Create: `reeldroppro/app/src/main/kotlin/com/atmaca/reeldroppro/receiver/BootReceiver.kt`
- Create: `reeldroppro/app/src/main/kotlin/com/atmaca/reeldroppro/net/NetworkMonitor.kt`

**Interfaces:**
- Consumes: `JobDao`, `RetryPolicy`, `ErrorClassifier`, `ExtractorRequestFactory`.
- Produces: process-safe active-job execution, notification progress, network-wait state, reboot/restart recovery.

- [ ] Add service state-transition tests around queued/resolving/downloading/completed/retry-wait/failed/cancelled decisions using pure transition functions.
- [ ] Verify RED.
- [ ] Implement extractor initialization/execution on `Dispatchers.IO`, stderr capture, foreground notification, queue recovery, and connectivity wait/retry.
- [ ] Verify unit tests GREEN and assembleDebug succeeds.
- [ ] Commit coordinator.

### Task 5: Storage, filenames, diagnostics

**Files:**
- Create: `reeldroppro/app/src/main/kotlin/com/atmaca/reeldroppro/storage/StorageRules.kt`
- Create: `reeldroppro/app/src/main/kotlin/com/atmaca/reeldroppro/diagnostics/DiagnosticLogger.kt`
- Test: `reeldroppro/app/src/test/kotlin/com/atmaca/reeldroppro/storage/StorageRulesTest.kt`

**Interfaces:**
- Produces: deterministic `Download/ReelDrop Pro/<platform>/<source>/<Foto|Video>/` paths, collision-safe filenames, compact redacted diagnostic export.

- [ ] Write failing tests for invalid filename characters, collision suffixing, and platform/source folder normalization.
- [ ] Implement storage rules and redacted diagnostic log.
- [ ] Verify GREEN.
- [ ] Commit storage and diagnostics.

### Task 6: Native UI and icon

**Files:**
- Create: `reeldroppro/app/src/main/kotlin/com/atmaca/reeldroppro/MainActivity.kt`
- Create: `reeldroppro/app/src/main/res/drawable/app_icon.xml`

**Interfaces:**
- Consumes: `InputParser` and database queue.
- Produces: three entry modes, queue/status view, start/cancel/retry-failed controls, aggregate progress.

- [ ] Implement a lightweight navy/yellow ReelDrop Pro UI with distinct falcon/download visual identity.
- [ ] Ensure all queue/database work runs off main thread.
- [ ] Build APK and inspect lint/compile output for main-thread or API-level issues.
- [ ] Commit UI.

### Task 7: CI, final verification, artifact

**Files:**
- Create: `.github/workflows/reeldrop-pro-build.yml`

**Interfaces:**
- Produces: `ReelDrop-Pro-APK` GitHub Actions artifact.

- [ ] Add workflow running `gradle testDebugUnitTest assembleDebug --stacktrace` in `reeldroppro` on `reeldrop-pro`.
- [ ] Push workflow and wait for CI.
- [ ] If CI fails, inspect exact job logs and fix only the demonstrated cause.
- [ ] Download artifact after a successful run.
- [ ] Verify APK ZIP integrity, file type, arm64 native libraries, and SHA-256 before delivery.
- [ ] Commit any final fixes.
