# ATMACA File Manager Design

## Goal
Build a clean Android file manager for Android 13+ that keeps the familiar two-pane/file-browser feel of File Manager Plus while replacing its unstable file-operation paths with our own code.

## Required behavior
- Show folders and files reliably, including photos and videos.
- Generate image/video thumbnails without blocking the UI.
- Delete files and folders on background workers so large deletions do not trigger ANR.
- Move must be a true move: verify destination, then remove source; never leave an accidental duplicate after a reported success.
- Copy and move are separate operations.
- No forced "Add to directory" workflow. User picks source item(s), chooses Move/Copy, chooses target folder, operation runs.
- Never trigger a whole-device or whole-storage rescan after move/copy/delete/rename.
- After an operation, refresh only the affected source and destination directory listings and update only the relevant MediaStore rows.
- Keep state through portrait/landscape changes and avoid activity recreation crashes.
- Support internal storage and removable/shared storage using direct File APIs where permitted and Storage Access Framework where Android requires it.
- No root requirement.

## Architecture
- Native Kotlin Android app.
- Single Activity with fragments/screens for browser, media preview, search and operation progress.
- Repository layer abstracts File, DocumentFile and MediaStore access.
- FileOperationEngine runs delete/copy/move jobs on Dispatchers.IO with cancellation, progress, retries only for transient failures, and post-operation verification.
- File operations are serialized per destination directory to avoid races, while unrelated operations may run with bounded concurrency.
- ThumbnailLoader uses bounded background concurrency and an in-memory cache.
- FolderViewModel owns visible directory state so rotation does not restart scans.
- Operations update only the source and destination listings after completion.
- Global rescans are not part of normal file-operation completion.

## Performance rules
- Never enumerate large directories on the main thread.
- Directory listing is paged/chunked and sortable without materializing unnecessary metadata.
- Thumbnail work is bounded and cancelled for off-screen rows.
- Bulk operations use iterative traversal rather than deep recursion.
- MediaStore updates are batched and targeted to changed items only.
- Progress updates are throttled so thousands of file operations do not flood the UI thread.
- No post-operation full-storage scan.

## Stability rules
- Every destructive or mutating operation has one owner job and one lifecycle-aware progress stream.
- Jobs survive rotation through ViewModel scope and do not restart because the Activity is recreated.
- UI refreshes are debounced and keyed by directory so duplicate refresh storms cannot occur.
- File-operation callbacks never perform disk I/O on the main thread.
- Cancellation leaves the current file in a known state and reports partial completion instead of hanging.
- Transient provider errors use bounded retries; permanent permission/path errors fail fast with the affected item reported.
- The same source item cannot be queued twice concurrently.

## Safety rules
- For move across filesystems/providers: copy -> verify size/existence -> delete source. Report failure if verification fails.
- For same-filesystem direct paths: atomic rename/move where supported.
- Never claim success until post-operation verification passes.
- Failed items stay visible and are reported; no silent loss.
- Delete confirmation stays enabled for destructive bulk actions.

## First release scope
Browser, thumbnails, search, multi-select, copy, true move, delete, rename, create folder, basic image/video open, storage usage, operation progress, refresh, Android 13 storage permissions/SAF support, rotation stability.

## Explicitly out of scope for v1
Cloud storage, FTP/SMB, archive extraction, root browsing, app manager, cleaner/optimizer, duplicate finder. These can be added only after the core file engine is proven stable.

## Verification
- Unit tests for move/copy/delete planning and verification.
- Unit tests that prove a successful operation requests only source/destination refresh and never a global scan.
- Instrumented tests for cancellation and rotation during operations.
- Stress test repeated move/delete operations to ensure no duplicate queueing or refresh storms.
- Manual stress test with thousands of files and mixed photo/video folders.
- APK is not considered complete if delete freezes the UI, thumbnails disappear, move leaves the source behind after success, an operation triggers full-storage scanning, or rotation closes the app.
