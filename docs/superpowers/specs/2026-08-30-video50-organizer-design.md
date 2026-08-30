# Video50 Organizer Design

## Goal
Create a completely separate Android APK that finds videos either from a user-selected source folder or by scanning the phone, then moves those videos into user-selected destination storage in folders of at most 50 videos each.

## Product boundaries
- This is a new standalone APK and does not modify the existing ATMACA file manager.
- Videos are moved only; they are not copied, transcoded, resized, renamed, or recompressed.
- Existing source files disappear from their original locations after a successful move.
- The destination is always explicitly selected by the user before moving.

## Modes
1. **Klasör Seç**: user selects a source folder with Android Storage Access Framework (SAF). The app recursively finds video files under that selected tree.
2. **Telefonu Tara**: app scans device-visible videos through MediaStore across available external volumes and de-duplicates by content URI/path identity.

## Destination flow
The user selects a destination folder using SAF. The app creates child folders named `Video 1`, `Video 2`, `Video 3`, ... under that destination. Each folder contains at most 50 videos.

## Move behavior
- For SAF-backed items, create the destination document, stream the bytes, then delete the source only after the destination write closes successfully.
- For MediaStore items, write to the selected SAF tree then delete the original source through ContentResolver. On Android versions requiring user consent for deletion, request consent and continue only after approval.
- Never delete the source when destination creation or write fails.
- Persist a lightweight progress journal so a reopened app can avoid re-moving completed items from the same operation.

## UI
Single-screen yellow/navy UI with four main actions: `Kaynak Klasör Seç`, `Telefonu Tara`, `Hedef Klasör Seç`, `50'şerli Ayır ve Taşı`. Show found count, selected source/destination summaries, moved/remaining counters, and errors.

## Android compatibility
- compile/target SDK 35
- min SDK 26
- Android 13+ requests `READ_MEDIA_VIDEO` for phone scan.
- Android 12 and below requests `READ_EXTERNAL_STORAGE` where required.
- SAF is the primary destination mechanism so both internal shared storage and SD card destinations can be selected without hard-coded paths.

## Success criteria
- 1–50 videos => `Video 1` only.
- 51 videos => `Video 1` has 50 and `Video 2` has 1.
- 327 videos => six full folders and `Video 7` with 27.
- No source is deleted before its destination file is fully written.
- Duplicate scan results are processed once.
- Existing ATMACA project files remain untouched.