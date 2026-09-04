# Background Media Tools and Complete Albums Design

## Goal

Keep Akıllı Kişi Kırpma, Görsel Paketleyici, and Video Kareleri running reliably after their dialog is left, while making every MediaStore album available in both the main Albums screen and the tools' gallery picker.

## Confirmed behavior

- Starting a job does not automatically close its tool dialog.
- The user may return to the gallery manually; the job continues in the background.
- A running job remains visible through progress in the tool UI and an Android notification.
- Closing the app screen or turning off the display must not cancel the job.
- The root-folder picker is not part of this fix. It remains secondary and the gallery album picker is the supported selection path.

## Architecture

### Shared background job runner

Replace the video-only worker path with one shared WorkManager foreground worker for all three tools. A job request contains a tool type plus its options. Because Android WorkManager input data is size-limited, selected media URIs are written to a private queue file and only the queue path and small options are passed to the worker.

The worker calls the existing `CompleteToolEngine` operation for the requested tool, reports `done`, `total`, `created`, and `failed` through WorkManager, and updates a low-priority ongoing notification. Queue files are deleted after terminal success, cancellation, or non-retryable failure. Retryable failures retain enough state to restart safely without silently duplicating completed output.

The settings UI observes WorkInfo by job ID. Leaving or recreating the dialog does not own or cancel execution. Reopening the relevant tool displays any active job and allows explicit cancellation. Only the user's cancel action cancels work.

### Complete album source

Use one repository operation as the source of truth for both the Albums screen and `InternalToolAlbumPicker`. It queries the Images and Video MediaStore collections independently with a minimal OEM-safe projection, aggregates rows by normalized relative path and bucket identity, chooses the newest valid cover, and sorts albums consistently.

Failure of the Files collection or an optional projection column must not collapse the result to albums derived from the currently loaded media page. The current `quickAlbums(state.items)` four-album fallback is removed from user-visible album rendering. If one media collection fails, albums from the other collection are still shown. If both fail, the UI shows a clear permission/query error and keeps the refresh action available.

Refresh performs a fresh repository query and replaces the displayed snapshot atomically. It does not retain a stale four-item fallback. The tool picker uses the same fresh snapshot and can enter any returned album, select one or many items, and pass those URIs to the background queue.

## Data flow

1. User opens a tool and chooses media from a real MediaStore album.
2. UI writes the distinct selected URI list to an app-private job file.
3. UI enqueues the shared foreground WorkManager request and continues observing it.
4. The user may keep the dialog open or manually return to Gallery.
5. The worker processes media and publishes progress to WorkInfo and its notification.
6. On completion, MediaStore is notified by the existing engine; the gallery and album snapshot refresh so newly created media appears without duplication.

## Error handling and stability

- Empty or unreadable selections fail before enqueue with a Turkish message.
- Large selections never travel in WorkManager `Data`.
- Album cursor reads tolerate unavailable optional columns and isolate image-query failure from video-query failure.
- Worker exceptions produce a bounded retry only for recoverable failures; permanent per-file failures increment `failed` and processing continues.
- Cancellation closes engine resources and removes the job queue.
- Existing output naming and crop/package/frame rules remain unchanged.
- No broad filesystem scan is introduced; album discovery remains MediaStore-based for speed and Android 13 compatibility.

## Tests and acceptance criteria

- Unit tests prove all three tool types create a file-backed request and map to the correct engine operation.
- A large synthetic selection cannot exceed WorkManager's input-data limit.
- UI/view-model tests prove leaving the dialog does not cancel work and explicit cancel does.
- Repository tests cover image-only, video-only, mixed, missing optional columns, one failed collection, normalized duplicate buckets, and refresh replacement.
- The Albums screen and internal picker do not call `quickAlbums` as a fallback.
- Existing gallery, viewer, crop, packaging, and video-frame tests continue to pass.
- A clean Gradle build produces the installable APK with an incremented build identity.

## Out of scope

- Repairing Android's root-folder/document picker.
- Changing crop geometry, package size rules, frame extraction rate semantics, or gallery viewer gestures.
- Automatically navigating away from a tool after enqueue.
