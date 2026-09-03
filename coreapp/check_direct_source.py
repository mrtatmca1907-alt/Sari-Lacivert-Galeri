from pathlib import Path

app = Path("coreapp/src/main/kotlin/com/atmaca/gallery/GalleryApp.kt").read_text(encoding="utf-8")
repo = Path("coreapp/src/main/kotlin/com/atmaca/gallery/MediaStoreRepository.kt").read_text(encoding="utf-8")
photo = Path("coreapp/src/main/kotlin/com/atmaca/gallery/StablePhotoPage.kt").read_text(encoding="utf-8")
settings = Path("coreapp/src/main/kotlin/com/atmaca/gallery/ModernSettingsDialog.kt").read_text(encoding="utf-8")
tools = Path("coreapp/src/main/kotlin/com/atmaca/gallery/CompleteSettingsExtras.kt").read_text(encoding="utf-8")
engine = Path("coreapp/src/main/kotlin/com/atmaca/gallery/CompleteToolEngine.kt").read_text(encoding="utf-8")
scanner = Path("coreapp/src/main/kotlin/com/atmaca/gallery/ToolFolderScanner.kt").read_text(encoding="utf-8")
loader = Path("coreapp/src/main/kotlin/com/atmaca/gallery/ImageLoader.kt").read_text(encoding="utf-8")
gradle = Path("coreapp/build.gradle.kts").read_text(encoding="utf-8")

required_app = [
    "enum class HomeSection { MEDIA, ALBUMS, SETTINGS",
    'label = { Text("Medya") }',
    'label = { Text("Albümler") }',
    'label = { Text("Ayarlar") }',
    "ModernSettingsDialog(",
    "detectDragGesturesAfterLongPress",
    "SortDirection",
    "slideshowPrefetchIndices(",
    "prefetchViewerBitmap(",
]
for marker in required_app:
    if marker not in app:
        raise SystemExit(f"DIRECT SOURCE FAIL: GalleryApp missing {marker!r}")

for legacy in ['label = { Text("Foto") }', 'label = { Text("Video") }', 'label = { Text("Kopya") }', 'label = { Text("Çöp") }']:
    if legacy in app:
        raise SystemExit(f"DIRECT SOURCE FAIL: legacy bottom navigation still active: {legacy}")

for marker in ["val dateModified: Long", "val dateTaken: Long", "MediaStore.MediaColumns.DATE_MODIFIED", "MediaStore.MediaColumns.DATE_TAKEN"]:
    if marker not in repo:
        raise SystemExit(f"DIRECT SOURCE FAIL: MediaStoreRepository missing {marker!r}")

for marker in ["val lastTapTime", "isViewerDoubleTap(", "nextDoubleTapScale(", "zoomOffsetAroundFocus(", "viewportWidth", "viewportHeight", "loadHighResolutionBitmap("]:
    if marker not in photo:
        raise SystemExit(f"DIRECT SOURCE FAIL: StablePhotoPage missing {marker!r}")

for marker in ["DialogProperties(usePlatformDefaultWidth = false)", "LazyColumn(", "SettingsChoiceRow("]:
    if marker not in settings:
        raise SystemExit(f"DIRECT SOURCE FAIL: ModernSettingsDialog missing {marker!r}")

for marker in [
    "AtmacaToolPage.PERSON_CROP",
    "AtmacaToolPage.PACKAGER",
    "AtmacaToolPage.VIDEO_FRAMES",
    "LinearProgressIndicator",
    "job?.cancel()",
    "ActivityResultContracts.OpenDocumentTree()",
    "collectToolUrisFromTree(",
    'Text("Klasör seç ve alt klasörleri tara")',
]:
    if marker not in tools:
        raise SystemExit(f"DIRECT SOURCE FAIL: ATMACA tool UI missing {marker!r}")

for marker in [
    "DocumentsContract.buildChildDocumentsUriUsingTree",
    "DocumentsContract.buildDocumentUriUsingTree",
    "toolAcceptsMime(",
    "coroutineContext.ensureActive()",
]:
    if marker not in scanner:
        raise SystemExit(f"DIRECT SOURCE FAIL: tool folder scanner missing {marker!r}")

for marker in ["FaceDetection.getClient", "FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE", "personCropBounds(", "InputImage.fromBitmap"]:
    if marker not in engine:
        raise SystemExit(f"DIRECT SOURCE FAIL: ML smart crop engine missing {marker!r}")
if "android.media.FaceDetector" in engine:
    raise SystemExit("DIRECT SOURCE FAIL: legacy android.media.FaceDetector returned")
if 'implementation("com.google.mlkit:face-detection:16.1.7")' not in gradle:
    raise SystemExit("DIRECT SOURCE FAIL: bundled ML Kit face detection dependency missing")

for marker in ["ViewerBitmapCache", "prefetchViewerBitmap("]:
    if marker not in loader:
        raise SystemExit(f"DIRECT SOURCE FAIL: viewer loader missing {marker!r}")

print("Direct source architecture OK")
