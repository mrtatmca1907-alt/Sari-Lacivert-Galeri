# ReelDrop Pro Design

## Goal

Replace the fragile split downloader setup with one strong Android-native application that handles Instagram public profile downloads, Instagram hashtag downloads, and Facebook public-media downloads behind a shared download engine. The primary target device is TECNO Spark 10 Pro / Android 13 / Helio G88 / 8 GB physical RAM. APK size is not a constraint when bundled components materially improve reliability, speed, offline capability, or recovery.

## Product shape

ReelDrop Pro will be a single app with three clearly separated entry points:

- Instagram Profile
- Instagram Hashtag
- Facebook

Each section has its own input validation and platform adapter, but all jobs flow into the same queue, persistence, network, retry, progress, logging, and file-writing layers.

## Architecture choice

Use a native Kotlin Android shell as the app core. Avoid Python as the application runtime. Platform extraction may use a bundled proven native/CLI extractor only where required, launched as an isolated worker process with captured stdout/stderr and explicit exit handling. FFmpeg and aria2 may be bundled only where they produce a measurable benefit.

The app is split into these units:

1. UI layer: Compose or classic Android views, lightweight and non-blocking.
2. Job database: Room-backed durable queue that survives process death and reboot.
3. Download coordinator: foreground service for active long-running jobs, with WorkManager for deferred/resume work.
4. Platform adapters: Instagram profile, Instagram hashtag, Facebook.
5. Transfer engine: bounded concurrent downloads, resumable HTTP where supported, temp files, checksum/size validation when metadata permits, atomic final rename.
6. Media post-processing: FFmpeg only when remux/merge is required.
7. Storage layer: MediaStore-compatible writes to Download/ReelDrop Pro/<platform>/<source>/ with photo and video separation.
8. Diagnostics: structured per-job logs, stderr capture, platform error classification, exportable debug report.

## Performance rules

The app must be designed around 8 GB physical RAM rather than treating virtual RAM as equivalent. Concurrency is adaptive and bounded. Large files stream directly to disk; they are never buffered entirely in memory. Thumbnail generation is lazy and cached. The UI never performs network, extractor, database, FFmpeg, directory enumeration, or large JSON parsing on the main thread.

Default transfer concurrency should start conservatively and adapt to error rate and device pressure. Platform extraction is serialized where necessary to avoid rate-limit bursts. The app should favor sustained throughput over short peaks that trigger thermal throttling or Android process pressure.

## Reliability and recovery

Every job has persistent states: queued, resolving, downloading, post-processing, completed, retry-wait, failed, cancelled. A job is resumable after app restart when the underlying transfer supports range requests or the extractor can reconstruct a media URL. Partial files use a temporary extension and are promoted only after successful completion.

Retries use exponential backoff with jitter and error classification. Authentication-required, removed/private, unsupported, rate-limited, network, storage-full, and extractor-failed errors are surfaced separately. Exit code 1 alone is never shown as the final explanation when stderr provides a more specific cause.

## Network behavior

The app detects connectivity changes. If the network disappears, active transfers enter waiting state instead of being marked failed. When connectivity returns, resumable jobs continue automatically. Wi-Fi and mobile data are both supported. The app does not hold a permanent high-performance Wi-Fi lock or permanent wake lock; locks are scoped only to active work that needs them.

## Storage behavior

Each source gets a deterministic folder. Photos and videos are stored separately. Filenames avoid collisions and preserve useful source identifiers and timestamps when available. Existing completed files are detected to prevent duplicate redownloads. A completed-item index is maintained in Room instead of rescanning the whole storage tree on each launch.

## User experience

The main screen shows the three platform modes plus a global queue. Each running job shows discovered count, downloaded count, photo/video totals, failures, current filename, speed, transferred bytes, elapsed time, and current state. The persistent notification shows aggregate active progress without forcing the app to stay open.

A failed-item screen lets the user retry only failed items. A diagnostics screen can export a compact log for troubleshooting. The UI follows the ATMACA/ReelDrop family but receives a distinct icon and stronger visual identity so it is not confused with other apps.

## Security and privacy

The app targets public content workflows. It will not attempt to bypass private-account access controls. Cookies or session data, when the user intentionally supplies them for their own authorized session, remain local to the device and are not uploaded elsewhere. Secrets are stored with Android secure storage APIs where practical and are excluded from exported diagnostic logs.

## Testing strategy

Core queue state transitions, retry policy, filename/path rules, duplicate detection, network pause/resume decisions, and error classification are unit-tested first. Platform adapters receive fixture-based parser tests. Instrumented tests cover foreground-service lifecycle and MediaStore writes. CI builds an arm64-v8a APK and runs tests on every change. Final artifacts are integrity-checked before delivery.

## Rollout

Phase 1 replaces the downloader core and ships Instagram profile + hashtag through the shared engine. Phase 2 adds Facebook through the same job system. Phase 3 tunes throughput and recovery from real device logs, then the old ReelDrop/ReelDropTag/FacebookDrop builds can be retired once the new app proves stable.

## Success criteria

- Long jobs survive app UI closure and normal process recreation.
- Network loss pauses instead of corrupting or silently losing the queue.
- A single bad post does not abort an otherwise healthy bulk job.
- Errors are specific enough to diagnose instead of only showing generic exit code 1.
- No ANR from downloader work on the main thread.
- Storage output is deterministic, duplicate-aware, and Android 13 compatible.
- The app remains usable while hundreds or thousands of items are queued.
