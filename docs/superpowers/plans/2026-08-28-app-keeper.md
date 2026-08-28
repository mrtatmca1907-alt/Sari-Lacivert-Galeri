# ATMACA Koruma Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a native Android app that lets the user select installed launcher apps and runs a persistent best-effort protection service with wake/network/boot recovery support.

**Architecture:** A small Kotlin app under `keeper/` with a foreground `KeeperService`, `BootReceiver`, package-list repository, persistent preferences, and a lightweight Views UI. Core selection normalization is pure Kotlin and unit-tested; Android integration is verified by Gradle build and manifest checks.

**Tech Stack:** Kotlin 2.0.21, Android Gradle Plugin 8.7.3, Android SDK 35, Java 17, JUnit 4.

**Spec:** `docs/superpowers/specs/2026-08-28-app-keeper-design.md`

## Global Constraints
- minSdk 26.
- targetSdk 33.
- compileSdk 35.
- No root, accessibility abuse, VPN, or hidden APIs.
- Foreground service is best-effort and must not claim to prevent third-party force-stop.
- Selected packages persist across app restarts.

---

### Task 1: Pure selection rules
**Files:** Create `keeper/app/src/test/kotlin/com/atmaca/keeper/TargetRulesTest.kt`; Create `keeper/app/src/main/kotlin/com/atmaca/keeper/TargetRules.kt`.
**Interfaces:** Produces `TargetRules.normalize(input: Collection<String>, selfPackage: String): List<String>`.
- [ ] Write tests covering whitespace, duplicates, blank values, and self-package removal.
- [ ] Run `gradle testDebugUnitTest` and verify RED because `TargetRules` is missing.
- [ ] Implement the minimal normalization function.
- [ ] Run `gradle testDebugUnitTest` and verify PASS.

### Task 2: Android keeper service and persistence
**Files:** Create `Prefs.kt`, `KeeperService.kt`, `BootReceiver.kt`, `AppRepository.kt`, and `AndroidManifest.xml`.
**Interfaces:** `Prefs.targets()`, `Prefs.setTargets()`, `Prefs.enabled`, `KeeperService.ACTION_START`, `KeeperService.ACTION_STOP`.
- [ ] Implement preferences and launcher app discovery.
- [ ] Implement foreground service with PARTIAL_WAKE_LOCK, WifiLock, NetworkCallback, START_STICKY, and persistent notification.
- [ ] Implement boot restart only when `Prefs.enabled` is true.
- [ ] Add manifest permissions and launcher queries.
- [ ] Run unit tests and `assembleDebug`.

### Task 3: UI
**Files:** Create `MainActivity.kt`, drawable icon resources, app Gradle files.
**Interfaces:** UI starts/stops service and persists selected package names.
- [ ] Build yellow/navy native Views UI.
- [ ] Add installed-app multi-select dialog and selected-app launch dialog.
- [ ] Add battery optimization settings shortcut and notification permission request on Android 13.
- [ ] Run unit tests and `assembleDebug`.

### Task 4: CI artifact
**Files:** Create `.github/workflows/app-keeper-build.yml`.
- [ ] Configure Java 17 + Gradle 8.10.2.
- [ ] Run `testDebugUnitTest assembleDebug`.
- [ ] Upload `keeper/app/build/outputs/apk/debug/app-debug.apk` as `ATMACA-Koruma-APK`.
- [ ] Download artifact and verify ZIP/APK integrity before delivery.
