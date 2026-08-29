# ATMACA File Manager V1 Design

## Goal
Build a clean Android file manager focused on stability and responsiveness on large local storage rather than modifying or redistributing the proprietary File Manager Plus APK.

## Scope
V1 provides local/internal-storage browsing, category shortcuts for images/videos/documents/APKs/archives, search, multi-select, copy, move, delete, rename, create-folder, share/open intents, storage summary, and image/video thumbnails.

FTP, SMB, WebDAV, cloud drives, archive editing, and root access are intentionally excluded from V1 so the local file-management core can remain small, testable, and stable.

## Platform
- Android app written in Kotlin/Java-compatible Android SDK APIs.
- compileSdk 36, targetSdk 36, minSdk 23.
- Android Gradle Plugin 9.3.0 and Gradle 9.5.0.
- Java 17 toolchain.
- App id: com.atmaca.filemanager
- App name: ATMACA Dosya Yöneticisi

## Storage strategy
Use normal File APIs only after the user explicitly grants All Files Access on Android 11+. On Android 10 and below use legacy storage permission where required. Protected private app directories remain subject to Android platform restrictions.

Directory enumeration happens off the UI thread. Results are sorted using directories-first, locale-insensitive filename ordering. UI receives immutable result batches. A generation token prevents stale scans from replacing a newer folder view.

## Stability rules
- Never perform directory recursion, copying, hashing, or thumbnail decoding on the main thread.
- Do not recursively scan the entire device for ordinary folder navigation.
- Limit concurrent file operations to one serialized worker to avoid competing copy/move/delete jobs.
- Cancel stale directory loads when navigating quickly.
- Catch per-file failures so one corrupt or inaccessible entry cannot abort an entire listing or batch operation.
- Avoid retaining full-resolution bitmaps; thumbnails use bounded dimensions and an LRU memory cache.
- RecyclerView updates use complete immutable lists from one owner rather than mutating the backing list during layout.
- Rotation does not own long-running work; ViewModel/executor state survives Activity recreation.

## UI
Sarı-lacivert ATMACA theme. Home screen shows storage usage and category shortcuts. Browser screen shows current path, folders first, then files, with multi-select actions. A small status line shows active operation progress and errors without blocking the UI.

## File operations
Copy streams through a fixed buffer and fsyncs before reporting success. Move prefers atomic rename when possible and falls back to copy + verified delete. Delete and rename operate one item at a time and report partial failures. Name collisions generate `name (2).ext`, `name (3).ext`, etc. rather than overwrite silently.

## Testing
Unit tests cover file-type classification, collision-safe naming, sorting, and path safety. GitHub Actions runs unit tests and assembles a debug APK. The produced APK is then ZIP-tested and hashed before delivery.
