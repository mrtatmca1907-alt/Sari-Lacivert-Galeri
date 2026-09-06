# ATMACA Gallery Complete Tools & Settings Design

## Goal
Build one stable Android gallery that keeps the current smooth zoom/free-rotation viewer, consolidates photo/video browsing, moves secondary tools into Settings, adds a full recycle-bin workflow, powerful bulk operations, slideshow, and integrates the useful capabilities of the three user-supplied APKs as isolated native modules.

## Product priorities
1. Stability and responsiveness are more important than APK size.
2. Preserve the current photo zoom and free-rotation behavior that the user explicitly accepted.
3. Do not make video playback less stable; video behavior should remain isolated from photo-tool changes.
4. Prefer modular components so a failure in face crop, frame extraction, packaging, trash, or slideshow cannot crash the core viewer.
5. Avoid unnecessary full-device rescans on every UI action.
6. Android 13 / HiOS 13 on Tecno Spark 10 Pro is the primary device target; shared-media access uses MediaStore/SAF permissions supported by Android.

## Home navigation
The current separate PHOTOS, VIDEOS, DUPLICATES and TRASH bottom sections are removed from the primary navigation.

Primary home becomes:
- `Medya`: photos and videos mixed together in one chronological/sorted grid.
- `Albümler`: folder/album based browsing; photo/video separation remains available naturally through albums and filters.
- `Ayarlar`: advanced settings and tools.

Duplicate finder, recycle bin, smart person crop, image packager, video frame extraction and related utilities move under Settings/Tools instead of occupying permanent home navigation slots.

## Media grid
The media grid supports:
- Photos and videos together.
- Configurable column count.
- Sort by name, path, size, last modified, taken date, random; ascending/descending.
- Media filters for photos, videos, GIF, RAW and SVG where the Android decoder supports display.
- File/media name rendered over the thumbnail near the lower edge, not as a separate top title.
- Video duration badge when enabled.
- Album media count when enabled.
- Square thumbnail option.
- Animated GIF thumbnail option.
- Pull-to-refresh option.
- Horizontal thumbnail-scroll option where applicable.

## Selection and bulk operations
Long press enters selection mode.

Selection mode supports:
- Tap to toggle individual items.
- Drag-selection across grid cells while the finger remains pressed, selecting every crossed item once.
- `Tümünü seç` for the current filtered/album scope.
- `Taşı`.
- `Kopyala`.
- `Paylaş`.
- `Çöpe taşı`.
- Permanent delete only from explicit destructive actions and the recycle bin.

Move/copy operations use background I/O and show progress without blocking Compose UI. Successful move must not leave a duplicate in the source location.

## Photo viewer
Opening a photo uses the accepted smooth viewer engine and visually follows the supplied reference screenshot.

Top controls:
- Back.
- Current media name.
- Rotate/free-rotation shortcut or current transform control.
- Edit/crop shortcut.
- Overflow menu.

Bottom controls:
- Favorite.
- Edit.
- Share.
- Trash.
- Info/details.
- Slideshow/media action where appropriate.

Viewer background:
- Main media remains centered.
- Surrounding area may use a dark or blurred image-derived background according to Settings.
- UI can hide for immersive fullscreen without altering image transform state.

Gesture rules:
- Existing pinch zoom is preserved.
- Existing free rotation is preserved.
- One-finger swipe at fit continues to navigate to adjacent media.
- Panning owns the gesture when the image is zoomed/transformed.
- Double tap toggles immersive/fullscreen chrome visibility, not the accepted pinch/free-rotation math.
- Viewer transform state should not continuously rebuild the parent pager during a gesture.

## Slideshow
Slideshow is available from the photo viewer overflow menu and Settings.

Options:
- Start/pause/stop.
- Configurable interval.
- Optional loop.
- Optional random order.
- Keep screen awake while slideshow is active.
- Slideshow uses the current filtered/album set and does not mutate files.

## Recycle bin
Recycle bin is removed from permanent home navigation and placed under Settings.

Behavior:
- On supported Android versions, use MediaStore trash state where possible.
- Trash operations are batched to minimize repeated consent/dialog overhead.
- Legacy fallback must not silently destroy data when restore is unavailable; destructive fallback is explicit.

Recycle bin screen supports:
- Thumbnail grid.
- Multi-select.
- `Tümünü seç`.
- `Geri yükle`.
- `Kalıcı sil`.
- `Geri Dönüşüm Kutusunu boşalt`.
- Display total trash size.
- Option to show/hide recycle bin from folder views.
- Option to show recycle bin as last item on main screen remains disabled by default because the bin lives in Settings.

## Settings structure
Settings combines the requested controls from the supplied screenshots with ATMACA-specific tools.

### Browsing
- Sort criteria and direction.
- Media filters.
- View type.
- Show all folder contents.
- Temporarily show hidden items.
- Included folders.
- Excluded folders.
- Hidden folders.
- Show hidden items.
- New folder.
- Increase/decrease column count.

### Deep zoom and image behavior
- Allow deep zoom.
- Allow gesture rotation.
- Highest-quality image display.
- Optional 1:1 double-tap zoom setting is not used by default because double tap is reserved for immersive fullscreen; if enabled later it must be mutually exclusive with fullscreen double-tap.
- Expanded details on fullscreen media.
- File loading priority: speed / balanced / quality.

### Fullscreen media
- Maximum brightness.
- Black background/status-bar mode.
- Auto-hide system UI.
- Edge tap navigation.
- Vertical photo brightness gesture.
- Swipe down to exit fullscreen.
- Display cutout/notch usage.
- Fullscreen rotation policy.

### Video
- Autoplay video.
- Remember last playback position.
- Loop video.
- Optional horizontal gesture opening policy.
- Vertical volume/brightness gestures.
- Show video duration.

### Thumbnail and scrolling
- Animate GIF thumbnails.
- Crop thumbnails to squares.
- Show album media count.
- Drag scrollbar tooltip.
- Horizontal thumbnail scrolling.
- Pull to refresh.

### File operations
- Delete empty folders after content removal.
- Preserve original last-modified timestamps where Android APIs permit.
- Skip repeated delete confirmation only where platform APIs and user-granted permissions allow it safely.
- Show/manage bottom actions.

### Security/visibility
- Password-protected hidden-items visibility.
- Optional whole-app lock.
- Optional protect delete/move actions.
- These controls are only implemented when a secure credential flow is present; no fake switches.

### Recycle bin
- Move items to recycle bin instead of permanent delete.
- Show recycle bin in folder screen.
- Recycle-bin visibility options.
- Empty recycle bin and display current size.

### Tools
- Duplicate finder.
- Smart Person Crop.
- Image Packager.
- Video Frames.
- Slideshow defaults.

## Three supplied APK tool integrations
The supplied APKs are behavioral references and test fixtures, not source-code dependencies. Their useful behavior is reimplemented as native isolated modules in this gallery rather than embedding or launching the APK binaries.

### Smart Person Crop
Reference APK: `Akilli-Kisi-Kirpma-Yas-Filtresiz.apk`.

Observed package contents include TensorFlow Lite / Task Vision native libraries and an EfficientDet Lite model. The gallery module therefore uses an on-device vision path with bounded worker concurrency.

Requirements:
- User selects one or multiple photos or a folder/album.
- Detect people/regions.
- Create crops without modifying originals by default.
- Provide output folder selection.
- Batch progress, cancellation, skipped/error count.
- Processing is off the main thread.
- Age filtering is not required; the reference is explicitly age-unfiltered.

### Image Packager
Reference APK: `ATMACA-Gorsel-Paketleyici-v2.apk`.

Requirements:
- Select source folder/album or selected media.
- Group/package images according to configurable batch size.
- Preserve source files unless explicit move mode is selected.
- Background processing, progress, cancellation, error reporting.
- Save only to user-authorized shared-storage destinations.

### Video Frames
Reference APK: `ATMACA-Video-Kareleri-5.0.apk`.

Requirements:
- Select one/multiple videos or an album/folder.
- Extract frames at configurable cadence, default one JPEG per second.
- Each video's outputs go into its own folder derived from video name.
- Frame filenames derive from the video name and sequential number.
- Background processing with bounded parallelism.
- Progress/cancel/resume-safe queue semantics where practical.
- A failed video does not abort the whole queue.

## Performance architecture
Core gallery browsing and viewer remain lightweight.

Heavy tasks use isolated repositories/workers:
- Media querying: MediaStore repository with paging/chunking instead of rebuilding huge lists after every mutation.
- Thumbnail/image decode: decode near display size with cache; avoid full-resolution decode for grid thumbnails.
- Viewer: retain high-quality decode path with viewport-aware sampling and bounded cache.
- File moves/copies/trash: coroutine/worker background I/O; UI observes progress.
- Vision/frame extraction/packaging: WorkManager or equivalent bounded background worker path.

The app must never intentionally load all media bitmaps into RAM at once.

## Error handling
- Every destructive action has a clear outcome message.
- Failed item operations are reported by count and can be retried.
- Consent cancellation leaves media unchanged.
- Tools produce per-item failure accounting and continue processing other items.
- Viewer must remain usable if a tool module fails to initialize.

## Testing and release gates
Before shipping the new APK:
- Unit tests cover selection-range logic, menu/settings rules, sort/filter rules, slideshow timing state, trash selection actions, file-operation state transitions and tool queue naming/routing logic.
- Instrumentation or platform-level tests cover MediaStore trash/restore where CI/device support permits.
- Build must pass on CI with Android API 37 toolchain currently used by the project.
- APK artifact is integrity-checked after download.
- Physical Tecno Spark 10 Pro test remains required for real-device smoothness, long-running tool workloads and OEM MediaStore permission behavior.

## Non-goals
- No requirement to keep APK size small.
- No direct copying/decompilation of proprietary source code from reference APKs.
- No hidden permanent deletion disguised as trash.
- No changes to the accepted photo zoom/free-rotation feel unless a regression test proves a required fix.
