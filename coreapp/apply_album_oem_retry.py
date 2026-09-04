from pathlib import Path

path = Path("coreapp/src/main/kotlin/com/atmaca/gallery/MediaStoreRepository.kt")
text = path.read_text(encoding="utf-8")

if "queryAlbumCollectionOemSafe" not in text:
    marker = '''        val grouped = linkedMapOf<String, Acc>()

        fun scan(collection: Uri, isVideo: Boolean) {
'''
    replacement = '''        val grouped = linkedMapOf<String, Acc>()

        val ALBUM_RICH_PROJECTION = buildList {
            add(MediaStore.MediaColumns._ID)
            add(MediaStore.MediaColumns.DISPLAY_NAME)
            add(MediaStore.MediaColumns.MIME_TYPE)
            add(MediaStore.MediaColumns.DATE_ADDED)
            add(MediaStore.Images.ImageColumns.BUCKET_ID)
            add(MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME)
            if (Build.VERSION.SDK_INT >= 29) add(MediaStore.MediaColumns.RELATIVE_PATH)
        }.toTypedArray()
        val ALBUM_CORE_PROJECTION = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.Images.ImageColumns.BUCKET_ID,
            MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME
        )

        fun queryAlbumCollectionOemSafe(collection: Uri, selection: String?, sort: String) =
            runCatching { resolver.query(collection, ALBUM_RICH_PROJECTION, selection, null, sort) }.getOrNull()
                ?: runCatching { resolver.query(collection, ALBUM_CORE_PROJECTION, selection, null, sort) }.getOrNull()
                ?: runCatching { resolver.query(collection, ALBUM_CORE_PROJECTION, null, null, sort) }.getOrNull()

        fun scan(collection: Uri, isVideo: Boolean) {
'''
    if text.count(marker) != 1:
        raise SystemExit(f"album retry insertion marker: beklenen 1 eslesme, bulunan {text.count(marker)}")
    text = text.replace(marker, replacement, 1)

old_projection = '''                val projection = buildList {
                    add(MediaStore.MediaColumns._ID)
                    add(MediaStore.MediaColumns.DISPLAY_NAME)
                    add(MediaStore.MediaColumns.MIME_TYPE)
                    add(MediaStore.MediaColumns.DATE_ADDED)
                    add(MediaStore.Images.ImageColumns.BUCKET_ID)
                    add(MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME)
                    if (Build.VERSION.SDK_INT >= 29) add(MediaStore.MediaColumns.RELATIVE_PATH)
                }.toTypedArray()
                val selection = if (Build.VERSION.SDK_INT >= 30) "${MediaStore.MediaColumns.IS_TRASHED}=0" else null
                val sort = "${MediaStore.MediaColumns.DATE_ADDED} DESC, ${MediaStore.MediaColumns._ID} DESC"
                resolver.query(collection, projection, selection, null, sort)?.use { cursor ->
'''
new_projection = '''                val selection = if (Build.VERSION.SDK_INT >= 30) "${MediaStore.MediaColumns.IS_TRASHED}=0" else null
                val sort = "${MediaStore.MediaColumns.DATE_ADDED} DESC, ${MediaStore.MediaColumns._ID} DESC"
                queryAlbumCollectionOemSafe(collection, selection, sort)?.use { cursor ->
'''
if old_projection in text:
    text = text.replace(old_projection, new_projection, 1)
elif "queryAlbumCollectionOemSafe(collection, selection, sort)?.use" not in text:
    raise SystemExit("album retry query replacement marker bulunamadi")

path.write_text(text, encoding="utf-8")
print("OEM-safe album retry applied")
