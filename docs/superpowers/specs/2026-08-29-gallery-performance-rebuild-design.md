# Gallery Performance Rebuild Design

## Goal
Rebuild the gallery experience around stability first: no UI-thread media scanning, no uncontrolled bitmap decoding, no large-list freezes, and graceful recovery from media/provider errors. Preserve the useful gallery features while modernizing app identity and visual polish.

## Priorities
1. Prevent ANR, freeze, crash, and memory pressure before adding optional features.
2. Keep scrolling smooth in very large albums.
3. Open photos and videos without blocking the main thread.
4. Keep file operations cancellable/observable and off the UI thread.
5. Make failures local: one bad media file must not break the gallery session.
6. Preserve existing photo/video, album, favorites, search, trash, duplicate, viewer, copy/move and metadata flows where practical.

## Architecture
Use the existing Kotlin/Jetpack Compose application as the base. MediaStore remains the source of truth. All I/O and decoding runs through bounded coroutine dispatchers or semaphores. UI receives immutable snapshots/state and never performs filesystem or bitmap work directly.

Introduce a media-index/cache layer between MediaStore and screens. It will avoid rebuilding the full media list on every navigation and will invalidate selectively after mutations. Album summaries are computed in one pass. Search/filter/sort work is moved away from the main dispatcher.

Thumbnail and full-image loading stay separate. Thumbnails use a memory-bounded LRU cache and bounded parallelism. Fullscreen images use one heavy decode at a time and decode to the actual viewport/zoom requirement rather than blindly decoding the original. Video playback uses one Media3 player owned by the viewer lifecycle and releases resources when leaving the viewer.

## Performance and ANR Controls
- No MediaStore query, DocumentFile traversal, EXIF read, hashing, duplicate scan, copy, move, delete, or bitmap decode on the main thread.
- Bound thumbnail concurrency to avoid I/O storms and RAM spikes.
- Cancel obsolete thumbnail/image jobs when cells leave composition or the user navigates away.
- Avoid retaining full-resolution bitmaps in long-lived Compose state.
- Use stable media IDs/keys in lazy grids.
- Do not re-query the whole gallery for every small UI change.
- Use chunked/batched operations for large selections.
- Catch corrupt or inaccessible media per item and continue processing remaining items.
- Release MediaPlayer/ExoPlayer and bitmap-heavy resources deterministically.

## Large Library Behaviour
The app must remain usable with tens or hundreds of thousands of media records. Album screens should show lightweight metadata first, then thumbnails lazily. Loading indicators should not block navigation. A refresh should replace data atomically instead of clearing the whole UI and rebuilding it visibly.

## Image Quality
Grid thumbnails should be sharp enough for the displayed cell size without decoding oversized bitmaps. Fullscreen images should use ARGB_8888 and a larger decode target based on display dimensions. Zoom should request higher-detail content only when necessary rather than keeping every image at full source resolution in memory.

## Video Stability
Use AndroidX Media3 ExoPlayer. Maintain a single active player, prepare asynchronously, stop/release on viewer disposal, handle unsupported/corrupt media with a visible error instead of a frozen white/black screen, and avoid generating video frames continuously while scrolling the grid.

## File Operations
Copy/move/delete/rename/trash operations run on Dispatchers.IO. Large operations expose progress and can be cancelled. The repository invalidates only affected cached data after mutations. A failed item is reported and skipped instead of aborting the whole batch.

## Duplicate Scanning
Duplicate detection remains a background worker/task. It must not hash/decode the entire library on the main thread. Persist partial/progress state so the UI can show status rather than appear frozen.

## Memory Strategy
Keep thumbnail cache size proportional to the process memory class with a conservative upper bound. Use RGB_565 only for small grid thumbnails where acceptable; use ARGB_8888 for viewer quality. Do not cache full-size viewer bitmaps globally. Clear transient caches on memory pressure callbacks.

## UI / Identity
Modernize the visual layer without adding heavy effects. Keep a clean dark/navy base with high-contrast yellow accents, large media-first grids, minimal chrome, and a simple modern app icon. Rename the app to `ATMACA Galeri` unless a later explicit user instruction overrides it. Remove unnecessary visual effects that increase recomposition or GPU load.

## Reliability
Use `runCatching`/typed result paths around provider and decoder operations, but never swallow failures that need user feedback. Corrupt files, permission loss, deleted media, and unsupported codecs must degrade gracefully. App state should survive rotation/recreation where practical through saveable state or ViewModel-owned state.

## Testing and Verification
Add unit tests for cache invalidation/index behaviour and pure sorting/filtering logic. Add instrumentation or compile-time smoke coverage for media permission paths where practical. Build debug APK in GitHub Actions. Before release, verify tests, assembleDebug, and inspect the workflow result. Performance-sensitive changes are accepted only if they reduce or bound main-thread work and memory pressure compared with the current implementation.

## Non-goals
Do not add cloud sync, online services, ads, analytics, or unrelated AI features in this stabilization pass. Do not trade stability for animation-heavy UI. Do not remove existing useful gallery capabilities merely to simplify implementation unless a feature is proven to be the direct cause of instability and cannot be safely isolated.
