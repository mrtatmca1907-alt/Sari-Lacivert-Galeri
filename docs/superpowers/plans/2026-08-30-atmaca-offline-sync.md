# ATMACA Offline Sync Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Make ATMACA handle arbitrary files and folders, stage HDD-bound file bytes while the PC is off, sync them safely when the PC returns, back up whole folders to the selected cloud tree, and copy cloud files back to phone storage.

**Architecture:** Extend the existing SQLite catalog with a durable upload queue. Selected HDD files are copied into app-private storage before being queued. A shared SyncEngine uploads staged bytes, applies metadata operations, refreshes the catalog, and is invoked manually, on app start/resume, and by WorkManager. SAF-based tree copy helpers handle cloud backup and cloud-to-phone copy without loading whole files into memory.

**Tech Stack:** Android Java 17, SQLiteOpenHelper, Storage Access Framework, WorkManager, HttpURLConnection, JUnit 4.

**Spec:** Existing approved ATMACA offline-sync design in this conversation.

## Global Constraints

- minSdk 26, targetSdk 35.
- Never delete a staged local payload before a successful HTTP upload response.
- Keep queued work after connection failure.
- Support arbitrary file MIME types, not only video.
- Stream large files in chunks.
- Preserve folder hierarchy during cloud folder backup.
- PC-side file moving is out of scope.

---

### Task 1: Durable staged upload queue
- Add `PendingUpload` model and `upload_queue` table/migration.
- Add enqueue/list/delete/count methods plus catalog upsert for offline visibility.
- Add tests for pure queue/path helper behavior where possible.

### Task 2: HDD staging and sync engine
- Add `HddStager` to copy URI payloads into app-private outbox using temp-file then rename.
- Add `SyncEngine` that checks health, uploads pending payloads, applies metadata queue, refreshes catalog, and retains failed items.

### Task 3: Automatic retry
- Add WorkManager dependency, `SyncWorker`, and scheduler.
- Schedule one-time sync after enqueue plus periodic constrained sync.

### Task 4: General file selection and offline HDD UX
- Replace video-only picker and labels with arbitrary-file selection.
- HDD confirmation stages files even when the PC is off, shows pending counts, and triggers background sync.

### Task 5: Cloud folder backup and cloud-to-phone download
- Add SAF recursive tree copier.
- Add whole-folder backup to a chosen cloud tree.
- Add cloud document selection and destination-phone-folder copy.

### Task 6: Verification and APK build
- Run unit tests and assemble debug APK through the repository workflow.
- Do not claim completion until build/tests are green.