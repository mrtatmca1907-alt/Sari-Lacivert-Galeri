from pathlib import Path

app = Path("coreapp/src/main/kotlin/com/atmaca/gallery/GalleryApp.kt").read_text(encoding="utf-8")
repo = Path("coreapp/src/main/kotlin/com/atmaca/gallery/MediaStoreRepository.kt").read_text(encoding="utf-8")
photo = Path("coreapp/src/main/kotlin/com/atmaca/gallery/StablePhotoPage.kt").read_text(encoding="utf-8")

required_app = [
    "enum class HomeSection { MEDIA, ALBUMS, SETTINGS",
    'label = { Text("Medya") }',
    'label = { Text("Albümler") }',
    'label = { Text("Ayarlar") }',
    "CompleteSettingsExtras(",
    "detectDragGesturesAfterLongPress",
    "SortDirection",
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

for marker in ["lastTapUpMs", "viewportWidth", "nextDoubleTapScale"]:
    if marker not in photo:
        raise SystemExit(f"DIRECT SOURCE FAIL: StablePhotoPage missing {marker!r}")

print("Direct source architecture OK")
