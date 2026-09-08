# Video Kareleri V6 Design

## Goal
Build a small Android app that processes the direct child videos of a user-selected folder, extracts one JPEG for every second of video, then removes each successfully processed source video. Videos that fail extraction are moved into a `HATA` folder.

## Constraints
- Android 13 is the primary device target.
- No deep recursive scan.
- Process one video at a time to keep memory stable.
- Use Storage Access Framework so the user explicitly grants the selected folder.
- Keep processing in a foreground service so leaving the screen does not cancel the batch.
- Do not include face detection, cropping, ML, duplicate analysis, or gallery features.
- Output folder is the video base name; JPEG files are `<video>_000001.jpg`, `<video>_000002.jpg`, etc.
- 10.000 seconds of duration produces exactly 10 frames; a partial last second produces one final frame.
- UI/progress updates are throttled so extraction work has priority.

## Flow
1. User selects one folder with `ACTION_OPEN_DOCUMENT_TREE` and persistent read/write permission.
2. The app enumerates only direct child files and selects video MIME/extensions.
3. A foreground service processes videos sequentially.
4. `MediaMetadataRetriever` reads duration and decodes at 1,000,000 microsecond intervals using `OPTION_CLOSEST`.
5. Frames are written as JPEG quality 90 into a folder named after the video base name.
6. When all frames succeed, the source video is deleted.
7. On extraction error, the source video is copied into `HATA` and the original is deleted only after the copy succeeds.
8. The next video starts only after the current video reaches a terminal state.
