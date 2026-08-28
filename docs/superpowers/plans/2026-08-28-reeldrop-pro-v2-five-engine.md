# ReelDrop Pro V2 Five-Engine Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the broken queue-centric ReelDrop Pro with five independent concurrent download slots, correct Instagram/Facebook extractor routing, durable foreground execution, separated folders, and visible per-slot progress.

**Architecture:** Keep Android/Kotlin as the coordinator and UI. Each of five fixed slots owns its own coroutine/process identity and persisted state. Instagram profile/hashtag uses gallery-dl 1.32.9 through an embedded Chaquopy Python bridge; Facebook uses youtubedl-android. A foreground data-sync service supervises all active slots, while MediaStore publishing and retry handling remain off the main thread.

**Tech Stack:** Kotlin 2.0.21, AGP 8.7.3, SDK 35/min 26, Coroutines, Room, WorkManager, youtubedl-android 0.18.1, Chaquopy 17.0.0, gallery-dl 1.32.9, JUnit 4.

**Spec:** `docs/superpowers/specs/2026-08-28-reeldrop-pro-v2-five-engine-design.md`

## Global Constraints

- Exactly five user-visible independent slots; no user-facing shared queue.
- Up to five slots may be active simultaneously; one slot failure cannot stop another.
- Instagram profile and hashtag route to gallery-dl; Facebook routes to yt-dlp.
- Only publicly accessible content; no private-account/CAPTCHA/access-control bypass.
- Active-only CPU wake-lock; no permanent high-performance Wi-Fi lock.
- Target Android 13, arm64-v8a, 8 GB physical RAM; one low-concurrency stream per slot.
- Storage paths are `Download/ReelDrop Pro/Hashtag|Profil|Facebook/<source>/Fotoğraflar|Videolar`.
- Progress/database writes are throttled and all extraction/file IO is off the UI thread.

---

### Task 1: V2 policies and paths

**Files:**
- Create: `reeldroppro/app/src/test/kotlin/com/atmaca/reeldroppro/engine/FiveEnginePolicyTest.kt`
- Create: `reeldroppro/app/src/main/kotlin/com/atmaca/reeldroppro/engine/FiveEnginePolicy.kt`
- Modify: `reeldroppro/app/src/test/kotlin/com/atmaca/reeldroppro/storage/OutputPathPolicyTest.kt`
- Modify: `reeldroppro/app/src/main/kotlin/com/atmaca/reeldroppro/storage/StorageRules.kt`

- [ ] Write failing tests for five slots, backend routing, process isolation, Turkish path routing, and localized states.
- [ ] Run CI and verify RED before production changes.
- [ ] Implement minimal policies and path rules.
- [ ] Run tests and verify GREEN.

### Task 2: Persist one active job per slot

**Files:**
- Modify: `reeldroppro/app/src/main/kotlin/com/atmaca/reeldroppro/data/JobEntity.kt`
- Modify: `reeldroppro/app/src/main/kotlin/com/atmaca/reeldroppro/data/JobDao.kt`
- Modify: `reeldroppro/app/src/main/kotlin/com/atmaca/reeldroppro/data/AppDatabase.kt`

- [ ] Add tests/pure policy checks for valid slot IDs 1..5 and restartable states.
- [ ] Add `slotId` with Room migration 1→2 and slot-scoped queries/replacement.
- [ ] Verify tests and build.

### Task 3: Correct extractor routing

**Files:**
- Modify: `reeldroppro/build.gradle.kts`
- Modify: `reeldroppro/app/build.gradle.kts`
- Create: `reeldroppro/app/src/main/python/gallery_bridge.py`
- Create: `reeldroppro/app/src/main/kotlin/com/atmaca/reeldroppro/engine/GalleryDlEngine.kt`
- Modify: `reeldroppro/app/src/main/kotlin/com/atmaca/reeldroppro/engine/ExtractorEngine.kt`

- [ ] Add Chaquopy 17.0.0 and pin gallery-dl 1.32.9.
- [ ] Configure gallery-dl base directory, safe filenames, skip-existing behavior, restrained Instagram request delays/retries, and no access-control bypass.
- [ ] Route Instagram to gallery-dl and Facebook to yt-dlp.
- [ ] Replace single global process ID with slot-scoped cancellation identities.
- [ ] Verify build/tests.

### Task 4: Five-worker foreground service and lifecycle lock

**Files:**
- Modify: `reeldroppro/app/src/main/kotlin/com/atmaca/reeldroppro/service/DownloadService.kt`
- Modify: `reeldroppro/app/src/main/AndroidManifest.xml`

- [ ] Replace sequential queue loop with five independent slot workers.
- [ ] Add per-slot start/stop actions and isolated retries.
- [ ] Hold PARTIAL_WAKE_LOCK only while at least one worker is actually active and release at zero active workers.
- [ ] Keep START_STICKY foreground notification while work remains.
- [ ] Verify service compiles and existing jobs recover safely after process recreation.

### Task 5: Five-slot native UI and progress

**Files:**
- Modify: `reeldroppro/app/src/main/kotlin/com/atmaca/reeldroppro/MainActivity.kt`

- [ ] Render exactly five cards with platform selector, one source input, Start/Stop, state, photo/video/error counts, progress, current file, elapsed/speed, and destination.
- [ ] Remove queue wording and queue controls.
- [ ] Throttle UI updates and keep database work off main thread.
- [ ] Verify build/tests.

### Task 6: Distinct ReelDrop Pro V2 icon

**Files:**
- Create: adaptive/vector launcher icon resources under `reeldroppro/app/src/main/res/`.
- Modify: `reeldroppro/app/src/main/AndroidManifest.xml`.

- [ ] Create navy/yellow five-lane download motif, visually distinct from the other ATMACA apps.
- [ ] Wire adaptive + fallback launcher icon resources.
- [ ] Verify Android resource compilation.

### Task 7: CI and artifact verification

- [ ] Run `gradle testDebugUnitTest assembleDebug --stacktrace` in GitHub Actions.
- [ ] Inspect and fix exact failures only.
- [ ] Download the successful `ReelDrop-Pro-APK` artifact.
- [ ] Verify APK ZIP integrity, arm64 native libraries, embedded Python/gallery-dl payload, file type, and SHA-256.
- [ ] Copy verified artifact to `/mnt/data/ReelDrop-Pro-V2.apk` and only then deliver it.