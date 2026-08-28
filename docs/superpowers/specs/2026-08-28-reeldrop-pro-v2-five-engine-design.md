# ReelDrop Pro V2 Five-Engine Design

## Goal
Replace the queue-centric ReelDrop Pro UI and execution model with five independent download engines which can run concurrently, keep Instagram profile/hashtag handling separate from Facebook, survive ordinary app/background lifecycle events, and expose clear per-engine progress.

## Approved behavior
- Exactly five independent engine slots are visible on the main screen.
- Each slot can be configured as Instagram Profile, Instagram Hashtag, or Facebook.
- Starting one slot never waits for another slot. Up to five slots may be active concurrently.
- A failure, retry wait, pause, or rate limit in one slot must not stop the other active slots.
- There is no user-facing shared queue.
- Instagram profile and hashtag extraction use a gallery-dl based adapter; Facebook uses yt-dlp.
- Only publicly accessible content is targeted; the app does not bypass private-account controls, CAPTCHAs, authentication restrictions, or platform access controls.
- Rate limits are handled with bounded retry/backoff rather than aggressive request flooding.

## Storage
- Instagram hashtag: `Download/ReelDrop Pro/Hashtag/<tag>/Fotoğraflar` and `Videolar`.
- Instagram profile: `Download/ReelDrop Pro/Profil/<username>/Fotoğraflar` and `Videolar`.
- Facebook: `Download/ReelDrop Pro/Facebook/<source>/Fotoğraflar` and `Videolar`.
- Filenames are collision-safe. Existing completed files are not downloaded again when a reliable identity/path match exists.
- Partial work stays in app-private temporary storage until publishing succeeds.

## Reliability
- A foreground service owns active work. The notification remains while any slot is active.
- CPU wake-lock may be held only while actual work is active and must be released when no engine is running. No permanent high-performance Wi-Fi lock.
- Connectivity loss pauses/retries without discarding completed files.
- Active slot state and progress are persisted so normal process recreation can resume work.
- Android force-stop cannot be bypassed and is outside the guarantee.

## Progress UI
Each slot shows source, localized state, downloaded photos, downloaded videos, errors, progress when known, current filename, elapsed time, speed when available, and destination folder. Unknown totals must be shown as unknown instead of fabricated.

## Performance
- Target device class: Android 13, arm64-v8a, 8 GB physical RAM.
- Maximum top-level concurrency is five, one worker per visible slot.
- Extraction and file IO run off the main thread.
- Progress persistence is throttled to avoid database/UI storms.

## Icon
ReelDrop Pro V2 gets a distinct yellow/navy adaptive launcher icon visually representing five simultaneous download lanes while staying in the ATMACA visual family.

## Verification
Unit tests cover five-slot concurrency policy, folder routing, localized state mapping, retry isolation, duplicate prevention, and lifecycle restart policy. CI must build the arm64 APK and run tests before the artifact is delivered.