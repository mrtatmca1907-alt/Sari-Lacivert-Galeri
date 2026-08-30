# Video50 Organizer Design

## Goal
Create a completely separate Android APK that finds videos either from a user-selected source folder or by scanning the phone, then moves those videos into the phone's public `Movies` folder in folders of at most 50 videos each.

## Product boundaries
- This is a new standalone APK and does not modify the existing ATMACA file manager.
- Videos are moved only; they are not kept at the source after a successful move.
- Videos are not transcoded, resized, renamed, or recompressed.
- Destination is fixed: the phone's public `Movies` folder.

## Modes
1. **Klasör Seç**: user selects a source folder with Android Storage Access Framework (SAF). The app recursively finds video files under that selected tree.
2. **Telefonu Tara**: app scans device-visible videos through MediaStore across available external volumes and de-duplicates by content URI identity.

## Destination flow
No destination picker is shown. The app creates `Movies/Video 1`, `Movies/Video 2`, `Movies/Video 3`, ... automatically. Each generated folder contains at most 50 videos.

## Move behavior
- Android 10+ destination files are created through MediaStore with `RELATIVE_PATH = Movies/Video N`.
- Android 9 and below use the public Movies directory with legacy storage permission.
- For every item, destination writing must finish successfully before the source is deleted.
- If the source is already inside the exact generated destination path, it is treated as completed and not moved again.
- Persist a lightweight progress journal so an interrupted operation can skip completed items and continue from remaining videos.
- Duplicate scan results are processed once.

## UI
Single-screen yellow/navy UI with three main actions: `Kaynak Klasör Seç`, `Telefonu Tara`, `50'şerli Ayır ve Movies'e Taşı`. Show found count, source summary, moved/remaining counters, current `Video N` folder, and errors.

## Android compatibility
- compile/target SDK 35
- min SDK 26
- Android 13+ requests `READ_MEDIA_VIDEO` for whole-phone scan.
- Android 12 and below requests `READ_EXTERNAL_STORAGE` where required.
- Android 9 and below also requests `WRITE_EXTERNAL_STORAGE` for the public Movies directory.

## Success criteria
- 1–50 videos => `Movies/Video 1` only.
- 51 videos => `Movies/Video 1` has 50 and `Movies/Video 2` has 1.
- 327 videos => six full folders and `Movies/Video 7` with 27.
- No source is deleted before its destination file is fully written.
- Duplicate scan results are processed once.
- Existing ATMACA project files remain untouched.