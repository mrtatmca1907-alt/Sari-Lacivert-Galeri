# Insget Organizer Design

## Goal
Build a small Android utility that groups Insget-downloaded media into folders by the media filename's logical base name.

## Scope
- Images: process only direct files in `Pictures/Insget`.
- Videos: process only direct files in `Movies/Insget`.
- Move files; never copy them as the normal operation.
- Keep images and videos in their original top-level media locations.
- Create one folder per derived group name inside the corresponding Insget folder.
- Do not recursively rescan folders already created by the app.
- Show moved, skipped, and error counts.

## Group-name rule
1. Remove the file extension.
2. Remove trailing numeric Insget suffix segments separated by `_` or `-`.
3. Preserve underscores inside the actual account/name portion.

Examples:
- `aysetolga_12345.jpg` -> `aysetolga`
- `ayse_tolga_12345_2.jpg` -> `ayse_tolga`
- `ayse_tolga.mp4` -> `ayse_tolga`

## Storage strategy
The app is intended for sideload use on Android 13 and requests Android's All files access (`MANAGE_EXTERNAL_STORAGE`). It performs direct same-volume file moves for speed. On Android 10 and below it also declares legacy read/write storage permissions where applicable.

## Collision behavior
If a destination filename already exists, preserve both files by adding `(1)`, `(2)`, etc. before the extension. The app never overwrites an existing file silently.

## UI
A single screen contains:
- permission status,
- the two fixed source paths,
- one `DÜZENLE` button,
- progress/status text,
- moved / skipped / error counters.

## Performance and stability
- Only direct child files of each Insget folder are inspected.
- Processing runs on a background executor so the UI does not freeze.
- Files are moved one at a time without loading media contents into memory.
- No thumbnails, image decoding, video probing, or deep directory scans.

## Success criteria
On a device with All files access granted, pressing `DÜZENLE` groups eligible photos under `Pictures/Insget/<group>/` and eligible videos under `Movies/Insget/<group>/`, while keeping the app responsive and reporting final counts.