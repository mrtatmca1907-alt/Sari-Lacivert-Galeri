from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def replace_once(path: Path, old: str, new: str):
    text = path.read_text(encoding="utf-8")
    if old not in text:
        raise SystemExit(f"pattern not found in {path}: {old[:180]!r}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


def replace_between(path: Path, start: str, end: str, replacement: str):
    text = path.read_text(encoding="utf-8")
    a = text.find(start)
    if a < 0:
        raise SystemExit(f"start not found in {path}: {start!r}")
    b = text.find(end, a)
    if b < 0:
        raise SystemExit(f"end not found in {path}: {end!r}")
    path.write_text(text[:a] + replacement + text[b:], encoding="utf-8")

# 1) Gallery back behavior, unique album keys, compact selection count.
gallery = ROOT / "coreapp/src/main/kotlin/com/atmaca/gallery/GalleryApp.kt"
replace_once(
    gallery,
    '''    Scaffold(\n        bottomBar = {''',
    '''    BackHandler(enabled = selectedIds.isNotEmpty() && state.mode != CollectionMode.ALBUM) {\n        selectedIds = emptySet()\n    }\n\n    Scaffold(\n        bottomBar = {'''
)
replace_once(
    gallery,
    '''                        BackHandler {\n                            selectedIds = emptySet()\n                            vm.switchTab(GalleryTab.PHOTOS)\n                        }''',
    '''                        BackHandler {\n                            when (galleryBackAction(selectedIds.size, inAlbum = true)) {\n                                GalleryBackAction.CLEAR_SELECTION -> selectedIds = emptySet()\n                                GalleryBackAction.CLOSE_ALBUM -> vm.switchTab(GalleryTab.PHOTOS)\n                                GalleryBackAction.EXIT -> Unit\n                            }\n                        }'''
)
replace_once(
    gallery,
    '''        itemsIndexed(albums, key = { _, album -> album.relativePath }) { _, album ->''',
    '''        itemsIndexed(albums, key = { _, album -> albumGridKey(album) }) { _, album ->'''
)
replace_once(
    gallery,
    '''        Text("$selectedCount seçili", modifier = Modifier.weight(1f))''',
    '''        Text("$selectedCount seçili", maxLines = 1, modifier = Modifier.width(78.dp))'''
)
# Compact only the selection-bar icons, not icons elsewhere.
selection_start = gallery.read_text(encoding="utf-8").find("private fun SelectionBar(")
selection_end = gallery.read_text(encoding="utf-8").find("@Composable\nprivate fun MediaCollection", selection_start)
if selection_start < 0 or selection_end < 0:
    raise SystemExit("SelectionBar bounds not found")
text = gallery.read_text(encoding="utf-8")
block = text[selection_start:selection_end]
block = block.replace("IconButton(onClick = onSelectAll)", "IconButton(onClick = onSelectAll, modifier = Modifier.size(40.dp))")
block = block.replace("IconButton(onClick = onShare)", "IconButton(onClick = onShare, modifier = Modifier.size(40.dp))")
block = block.replace("IconButton(onClick = onCopy)", "IconButton(onClick = onCopy, modifier = Modifier.size(40.dp))")
block = block.replace("IconButton(onClick = onMove)", "IconButton(onClick = onMove, modifier = Modifier.size(40.dp))")
block = block.replace("IconButton(onClick = onRename)", "IconButton(onClick = onRename, modifier = Modifier.size(40.dp))")
block = block.replace("IconButton(onClick = onTrashOrRestore)", "IconButton(onClick = onTrashOrRestore, modifier = Modifier.size(40.dp))")
block = block.replace("IconButton(onClick = onDeleteForever)", "IconButton(onClick = onDeleteForever, modifier = Modifier.size(40.dp))")
gallery.write_text(text[:selection_start] + block + text[selection_end:], encoding="utf-8")

# 2) Tool dialog: internal album picker replaces outer Dialog; Video Frames stays open and polls WorkManager.
extras = ROOT / "coreapp/src/main/kotlin/com/atmaca/gallery/CompleteSettingsExtras.kt"
replace_once(
    extras,
    '''import androidx.compose.ui.window.DialogProperties\nimport kotlinx.coroutines.Job\nimport kotlinx.coroutines.launch''',
    '''import androidx.compose.ui.window.DialogProperties\nimport androidx.work.WorkInfo\nimport androidx.work.WorkManager\nimport kotlinx.coroutines.Dispatchers\nimport kotlinx.coroutines.Job\nimport kotlinx.coroutines.delay\nimport kotlinx.coroutines.launch\nimport kotlinx.coroutines.withContext\nimport java.util.UUID'''
)
replace_once(
    extras,
    '''    var showInternalAlbumPicker by remember(tool) { mutableStateOf(false) }''',
    '''    var showInternalAlbumPicker by remember(tool) { mutableStateOf(false) }\n    var backgroundWorkId by remember(tool) { mutableStateOf<UUID?>(null) }'''
)
replace_once(
    extras,
    '''    fun cancelActiveWork() {\n        job?.cancel()\n        running = false\n        scanning = false\n    }''',
    '''    fun cancelActiveWork() {\n        job?.cancel()\n        backgroundWorkId?.let { WorkManager.getInstance(context.applicationContext).cancelWorkById(it) }\n        backgroundWorkId = null\n        running = false\n        scanning = false\n    }'''
)
replace_once(
    extras,
    '''        if (tool == AtmacaToolPage.VIDEO_FRAMES) {\n            val workId = enqueueVideoFrameWork(context, selectedUris, framesPerSecond)\n            if (workId != null) {\n                Toast.makeText(context, "Video Kareleri arka planda başlatıldı", Toast.LENGTH_LONG).show()\n                onDismiss()\n            } else {\n                Toast.makeText(context, "Arka plan işi başlatılamadı", Toast.LENGTH_LONG).show()\n            }\n            return\n        }''',
    '''        if (tool == AtmacaToolPage.VIDEO_FRAMES) {\n            val workId = enqueueVideoFrameWork(context, selectedUris, framesPerSecond)\n            if (workId != null) {\n                backgroundWorkId = workId\n                running = true\n                done = 0\n                total = 0\n                Toast.makeText(context, "Video Kareleri arka planda başlatıldı", Toast.LENGTH_LONG).show()\n                job = scope.launch {\n                    val manager = WorkManager.getInstance(context.applicationContext)\n                    while (true) {\n                        val info = withContext(Dispatchers.IO) { runCatching { manager.getWorkInfoById(workId).get() }.getOrNull() }\n                        if (info != null) {\n                            done = info.progress.getInt("done", done)\n                            total = info.progress.getInt("total", total)\n                            when (info.state) {\n                                WorkInfo.State.SUCCEEDED -> {\n                                    running = false\n                                    backgroundWorkId = null\n                                    val created = info.outputData.getInt("created", done)\n                                    val failed = info.outputData.getInt("failed", 0)\n                                    Toast.makeText(context, "$created kare oluşturuldu${if (failed > 0) " • $failed hata" else ""}", Toast.LENGTH_LONG).show()\n                                    break\n                                }\n                                WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> {\n                                    running = false\n                                    backgroundWorkId = null\n                                    Toast.makeText(context, "Video Kareleri işi tamamlanamadı", Toast.LENGTH_LONG).show()\n                                    break\n                                }\n                                else -> Unit\n                            }\n                        }\n                        delay(500)\n                    }\n                }\n            } else {\n                Toast.makeText(context, "Arka plan işi başlatılamadı", Toast.LENGTH_LONG).show()\n            }\n            return\n        }'''
)
replace_once(
    extras,
    '''    if (showInternalAlbumPicker) {\n        InternalToolAlbumPicker(''',
    '''    if (showInternalAlbumPicker) {\n        InternalToolAlbumPicker('''
)
# Insert return after the internal picker block, immediately before the outer Dialog.
replace_once(
    extras,
    '''        )\n    }\n\n    Dialog(\n        onDismissRequest = { if (!running && !scanning && !showInternalAlbumPicker) onDismiss() },''',
    '''        )\n        if (!shouldRenderOuterToolDialog(showInternalAlbumPicker)) return\n    }\n\n    Dialog(\n        onDismissRequest = { if (!running && !scanning && !showInternalAlbumPicker) onDismiss() },'''
)
replace_once(
    extras,
    '''                    if (running) {\n                        Text("İşleniyor: $done / $total")''',
    '''                    if (running) {\n                        Text(if (tool == AtmacaToolPage.VIDEO_FRAMES) videoFrameProgressText(done, total) else "İşleniyor: $done / $total")'''
)

# 3) Truly OEM-safe album enumeration: minimal projection + per-collection failure isolation.
repo = ROOT / "coreapp/src/main/kotlin/com/atmaca/gallery/MediaStoreRepository.kt"
new_album_loader = r'''    suspend fun loadAlbumsOemSafe(): List<GalleryAlbum> = withContext(Dispatchers.IO) {
        data class Acc(
            var relativePath: String,
            var name: String,
            var count: Int,
            var cover: GalleryMedia?,
            var bucketId: Long,
            var bucketName: String?
        )
        val grouped = linkedMapOf<String, Acc>()

        fun scan(collection: Uri, isVideo: Boolean) {
            runCatching {
                val projection = buildList {
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
                    val idI = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                    val nameI = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                    val mimeI = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
                    val dateI = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_ADDED)
                    val bucketI = cursor.getColumnIndex(MediaStore.Images.ImageColumns.BUCKET_ID)
                    val bucketNameI = cursor.getColumnIndex(MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME)
                    val pathI = if (Build.VERSION.SDK_INT >= 29) cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH) else -1
                    while (cursor.moveToNext()) {
                        coroutineContext.ensureActive()
                        val id = cursor.getLong(idI)
                        val rawPath = if (pathI >= 0) cursor.getString(pathI).orEmpty().trim() else ""
                        val bucketId = if (bucketI >= 0) cursor.getLong(bucketI) else 0L
                        val bucketName = if (bucketNameI >= 0) cursor.getString(bucketNameI) else null
                        val dateAdded = if (dateI >= 0) cursor.getLong(dateI) else 0L
                        val item = GalleryMedia(
                            id = id,
                            uri = ContentUris.withAppendedId(collection, id),
                            name = cursor.getString(nameI).orEmpty(),
                            mimeType = if (mimeI >= 0) cursor.getString(mimeI) else null,
                            isVideo = isVideo,
                            dateAdded = dateAdded,
                            dateModified = 0L,
                            dateTaken = 0L,
                            width = 0,
                            height = 0,
                            bucketId = bucketId,
                            bucketName = bucketName,
                            relativePath = rawPath,
                            size = 0L,
                            durationMs = 0L,
                            isTrashed = false
                        )
                        val key = albumIdentityKey(rawPath, bucketId, bucketName)
                        val displayPath = if (rawPath.isNotBlank()) normalizeRelativePath(rawPath) else ""
                        val displayName = bucketName?.trim().orEmpty().ifBlank {
                            if (displayPath.isNotBlank()) albumDisplayName(displayPath) else "Depolama"
                        }
                        val existing = grouped[key]
                        if (existing == null) {
                            grouped[key] = Acc(displayPath, displayName, 1, item, bucketId, bucketName)
                        } else {
                            existing.count++
                            if ((existing.cover?.dateAdded ?: Long.MIN_VALUE) < dateAdded) existing.cover = item
                        }
                    }
                }
            }
        }

        scan(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, false)
        scan(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true)
        grouped.values
            .map { GalleryAlbum(it.relativePath, it.name, it.count, it.cover, it.bucketId, it.bucketName) }
            .sortedBy { it.name.lowercase() }
    }

'''
replace_between(repo, "    suspend fun loadAlbumsOemSafe(): List<GalleryAlbum>", "    suspend fun loadAllInAlbum(album: GalleryAlbum)", new_album_loader)

print("HiOS root fix applied")
