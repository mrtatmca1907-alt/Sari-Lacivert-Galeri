# ATMACA Offline Sync Design

## Goal

Complete the existing `atmaca-wifi-files` Android/PC workflow so the phone remains useful while the PC and HDD are powered off, then automatically reconciles queued work when the PC/HDD service becomes reachable. Also add direct cloud-to-phone download inside the Android app. The PC side does not need a general file-manager move feature for this scope.

## Existing Baseline

The Android app already keeps an offline catalog, queues metadata operations, can browse the HDD catalog, can upload selected videos to an HDD folder when the PC service is reachable, and `AtmacaApi` already exposes health, catalog fetch, download, upload, and queue endpoints.

The missing behavior is durable staging of new file payloads while the PC is offline. Today HDD upload attempts are tied directly to a live PC service. Cloud-to-phone download also lacks a complete user flow.

## Required User Experience

### 1. Offline HDD file staging

When the user selects one or more phone files for an HDD destination, ATMACA must immediately accept the action even if the PC/HDD is offline.

For every selected file the app must:

1. Copy the file into an app-managed staging area on the phone.
2. Persist a queue record containing at least: local staged path, original display name, MIME type, byte size when known, target HDD directory, creation time, retry count/state, and a stable operation id.
3. Show the file as pending in the app instead of failing just because `/health` is unavailable.
4. Preserve the staged payload across app restarts and phone reboots until upload succeeds or the user explicitly cancels it.

The source file chosen by the user is not deleted by the staging step. The staged copy is ATMACA's durable upload payload.

### 2. Automatic sync when PC/HDD returns

A single sync engine will process both metadata operations and staged file uploads.

A sync attempt begins when any of these occur:

- user taps Wi-Fi sync / send queue,
- app is opened/resumed and the host is reachable,
- Android background work runs while network is available.

For each pending staged upload:

1. Verify the PC service is reachable.
2. Upload to the persisted target HDD directory using the existing upload endpoint.
3. Treat only a successful 2xx response as completion.
4. After successful upload, remove the staged phone copy and mark/remove the queue record.
5. If upload fails, keep both queue record and staged file intact for retry.

Metadata operations (mkdir, rename, move, delete) continue to use the existing JSON operation queue. The sync engine must not clear metadata operations until the PC confirms the queue request succeeded.

After successful outbound work, fetch the latest HDD catalog and replace/update the phone's local catalog so both sides converge.

### 3. Crash and interruption safety

Queue state and staged payloads must be durable before UI reports the item as queued.

If the app, phone, Wi-Fi, PC service, or HDD disappears during transfer:

- the queue record remains pending,
- the staged file remains on the phone,
- the next sync retries it,
- the app never deletes the only staged payload before server success.

This first version does not implement byte-range resume. A partially failed upload restarts that file from byte zero on the next attempt. Correctness and durability take priority over resumable transfer.

### 4. Duplicate/retry behavior

Each staged upload has a stable operation id. The Android side must avoid starting the same queued item twice concurrently.

The PC upload endpoint should accept an operation id and keep a small durable completion ledger. If the same id is received again after a prior successful upload, the server returns success without creating another copy. This makes retries after ambiguous connection loss idempotent.

Filename collision handling remains explicit: if a different file already occupies the requested HDD name, the server chooses a duplicate-safe name or returns a clear collision response according to the existing server convention; it must never silently overwrite an unrelated file.

### 5. Background behavior

Use Android WorkManager for deferred sync. Work must be constrained to an available network connection and use a unique work name so repeated triggers do not create parallel upload workers.

The app must still expose a manual sync button. Background execution is best-effort under Android battery restrictions; manual sync remains authoritative when the user wants immediate transfer.

### 6. Queue visibility and control

The UI must show a combined pending count and enough state to distinguish staged-file uploads from metadata operations.

For staged files, show at least file name, target HDD path, pending/failed state, and a cancel action. Cancel removes the queue record and its staged copy only after explicit confirmation.

### 7. Cloud to phone download

The existing cloud target must gain the reverse flow.

When browsing cloud content, selecting a file exposes `Telefona indir`. The user chooses a destination folder with Android SAF. ATMACA streams the cloud file directly into the selected phone folder and reports progress/error.

A failed download must not leave a misleading completed file. Write to a temporary document/name where possible and finalize/rename only after the stream completes; if the provider does not support rename semantics, delete the incomplete destination on failure.

Cloud download is independent from the PC/HDD sync engine and must work without the PC.

### 8. Architecture

Create focused components instead of growing `MainActivity` further:

- `PendingUpload`: immutable model for one staged HDD upload.
- `PendingUploadStore`: durable SQLite/SharedPreferences-backed queue metadata CRUD. Prefer SQLite because the app already uses a catalog database and queue records need multiple fields and states.
- `StagingStore`: copies selected `Uri` payloads into app-managed files and deletes them only after success/cancel.
- `SyncEngine`: serially sends metadata queue, staged uploads, then refreshes the catalog.
- `SyncWorker`: WorkManager bridge that invokes `SyncEngine` under network constraints.
- `CloudDownloadManager`: streams a cloud item into a SAF destination with cleanup on failure.
- `AtmacaApi`: extend upload to send operation id; retain download and queue endpoints.
- PC server: persist processed upload operation ids and make upload idempotent.
- `MainActivity`: becomes orchestration/UI only; it stages offline uploads rather than directly requiring `health()` before accepting them.

## Data Flow

### Offline HDD upload

`Phone Uri -> StagingStore -> PendingUploadStore -> UI shows pending`

Later:

`SyncWorker/manual sync -> SyncEngine -> AtmacaApi.upload(opId, target, staged file) -> PC/HDD -> mark complete -> delete staged file -> refresh catalog`

### Metadata operation

`UI action -> CatalogDb pending operation -> SyncEngine -> /queue -> clear only after success -> refresh catalog`

### Cloud download

`Cloud item -> CloudDownloadManager -> SAF destination on phone`

## Storage and limits

Staged HDD files live in app-managed internal/external app storage, not in a transient cache directory that Android may purge freely. Before staging, the app checks available space against the incoming file size when size is known. If there is not enough room, it rejects the staging action with a clear message rather than creating a broken queue entry.

## Error Handling

- PC offline: staged uploads stay pending; no data loss.
- Network loss: current upload fails, staged payload remains, retry later.
- App restart: queue reloads from durable store.
- Missing staged file: mark queue item failed with a visible reason; do not silently drop it.
- Server duplicate operation id: treat as success and remove local staged payload.
- Cloud download failure: remove incomplete destination when possible and report the error.
- Insufficient phone storage: reject before staging.

## Testing

Unit tests must cover:

- queue persistence and state transitions,
- staged file lifecycle: create, retain on failure, delete on success/cancel,
- sync ordering and queue clearing only on success,
- duplicate operation-id handling,
- host-unreachable behavior leaves work intact,
- storage-space rejection,
- cloud download cleanup on failure.

Integration/CI tests must cover Android project compilation and existing tests. Server-side tests must verify idempotent upload behavior and queue application. A release is not considered complete until the GitHub Actions build and tests pass.

## Non-goals for this iteration

- General PC-side file-manager move UI.
- Byte-range/resumable uploads.
- Full offline copies of every existing HDD file.
- Automatic cloud mirroring.
- SSD-specific UI. SSD will reuse this same sync engine after the HDD version is verified.

## Acceptance Criteria

1. With PC/HDD off, select a phone file and choose an HDD folder; ATMACA reports it as pending and the payload survives app restart.
2. Turn PC/HDD on; without reselecting the file, a sync sends it to the originally chosen HDD folder.
3. A failed transfer leaves the staged payload and queue item intact.
4. A retry after an ambiguous previous success does not create a duplicate on HDD.
5. Offline rename/delete/mkdir queue operations still survive and apply when PC returns.
6. After successful synchronization, the phone catalog refreshes from the PC.
7. A cloud file can be selected and downloaded into a user-chosen phone folder without the PC.
8. GitHub Actions unit tests and APK build succeed before the build is presented as finished.
