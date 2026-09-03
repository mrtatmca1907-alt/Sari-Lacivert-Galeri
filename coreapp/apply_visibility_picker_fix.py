from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def replace_once(path: Path, old: str, new: str):
    text = path.read_text(encoding="utf-8")
    if old not in text:
        raise SystemExit(f"pattern not found: {path}\n{old[:220]}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")

repo = ROOT / "coreapp/src/main/kotlin/com/atmaca/gallery/MediaStoreRepository.kt"
replace_once(
    repo,
    '''                add(MediaStore.MediaColumns.DATE_TAKEN)\n''',
    ''''''
)
replace_once(
    repo,
    '''                val takenI = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_TAKEN)\n''',
    '''                val takenI = -1\n'''
)
old_load_all = '''    suspend fun loadAllInAlbum(album: GalleryAlbum): List<GalleryMedia> {\n        val all = ArrayList<GalleryMedia>()\n        var offset = 0\n        while (true) {\n            val page = loadMixedPage(\n                offset = offset,\n                limit = PAGE_SIZE,\n                albumPath = album.relativePath.ifBlank { null },\n                albumBucketId = album.bucketId,\n                albumBucketName = album.bucketName\n            )\n            if (page.isEmpty()) break\n            all += page\n            if (page.size < PAGE_SIZE) break\n            offset += page.size\n        }\n        return all\n    }\n'''
new_load_all = '''    suspend fun loadAllInAlbum(album: GalleryAlbum): List<GalleryMedia> = loadAllInAlbumOemSafe(album)\n\n    suspend fun loadAllInAlbumOemSafe(album: GalleryAlbum): List<GalleryMedia> = withContext(Dispatchers.IO) {\n        val result = ArrayList<GalleryMedia>()\n        val locator = albumLocator(\n            album.relativePath.ifBlank { null },\n            album.bucketId,\n            album.bucketName\n        )\n\n        fun scan(collection: Uri, isVideo: Boolean) {\n            val projection = buildList {\n                add(MediaStore.MediaColumns._ID)\n                add(MediaStore.MediaColumns.DISPLAY_NAME)\n                add(MediaStore.MediaColumns.MIME_TYPE)\n                add(MediaStore.MediaColumns.DATE_ADDED)\n                add(MediaStore.MediaColumns.DATE_MODIFIED)\n                add(MediaStore.MediaColumns.WIDTH)\n                add(MediaStore.MediaColumns.HEIGHT)\n                add(MediaStore.Images.ImageColumns.BUCKET_ID)\n                add(MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME)\n                if (Build.VERSION.SDK_INT >= 29) add(MediaStore.MediaColumns.RELATIVE_PATH)\n                add(MediaStore.MediaColumns.SIZE)\n                if (isVideo) add(MediaStore.Video.VideoColumns.DURATION)\n                if (Build.VERSION.SDK_INT >= 30) add(MediaStore.MediaColumns.IS_TRASHED)\n            }.toTypedArray()\n\n            val selectionParts = mutableListOf<String>()\n            val selectionArgs = mutableListOf<String>()\n            if (Build.VERSION.SDK_INT >= 30) selectionParts += "${MediaStore.MediaColumns.IS_TRASHED}=0"\n            when (locator) {\n                is AlbumLocator.Path -> {\n                    selectionParts += "${MediaStore.MediaColumns.RELATIVE_PATH}=?"\n                    selectionArgs += locator.path\n                }\n                is AlbumLocator.Bucket -> {\n                    selectionParts += "${MediaStore.Images.ImageColumns.BUCKET_ID}=?"\n                    selectionArgs += locator.id.toString()\n                }\n                is AlbumLocator.Name -> {\n                    selectionParts += "${MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME}=?"\n                    selectionArgs += locator.name\n                }\n                AlbumLocator.Unknown -> return\n            }\n\n            val selection = selectionParts.takeIf { it.isNotEmpty() }?.joinToString(" AND ")\n            val args = selectionArgs.takeIf { it.isNotEmpty() }?.toTypedArray()\n            val sort = "${MediaStore.MediaColumns.DATE_ADDED} DESC, ${MediaStore.MediaColumns._ID} DESC"\n            resolver.query(collection, projection, selection, args, sort)?.use { cursor ->\n                val idI = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)\n                val nameI = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)\n                val mimeI = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)\n                val dateI = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_ADDED)\n                val modifiedI = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)\n                val widthI = cursor.getColumnIndex(MediaStore.MediaColumns.WIDTH)\n                val heightI = cursor.getColumnIndex(MediaStore.MediaColumns.HEIGHT)\n                val bucketI = cursor.getColumnIndex(MediaStore.Images.ImageColumns.BUCKET_ID)\n                val bucketNameI = cursor.getColumnIndex(MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME)\n                val pathI = if (Build.VERSION.SDK_INT >= 29) cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH) else -1\n                val sizeI = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)\n                val durationI = if (isVideo) cursor.getColumnIndex(MediaStore.Video.VideoColumns.DURATION) else -1\n                while (cursor.moveToNext()) {\n                    coroutineContext.ensureActive()\n                    val id = cursor.getLong(idI)\n                    result += GalleryMedia(\n                        id = id,\n                        uri = ContentUris.withAppendedId(collection, id),\n                        name = cursor.getString(nameI).orEmpty(),\n                        mimeType = if (mimeI >= 0) cursor.getString(mimeI) else null,\n                        isVideo = isVideo,\n                        dateAdded = if (dateI >= 0) cursor.getLong(dateI) else 0L,\n                        dateModified = if (modifiedI >= 0) cursor.getLong(modifiedI) else 0L,\n                        dateTaken = 0L,\n                        width = if (widthI >= 0) cursor.getInt(widthI) else 0,\n                        height = if (heightI >= 0) cursor.getInt(heightI) else 0,\n                        bucketId = if (bucketI >= 0) cursor.getLong(bucketI) else 0L,\n                        bucketName = if (bucketNameI >= 0) cursor.getString(bucketNameI) else null,\n                        relativePath = if (pathI >= 0) cursor.getString(pathI).orEmpty() else "",\n                        size = if (sizeI >= 0) cursor.getLong(sizeI) else 0L,\n                        durationMs = if (durationI >= 0) cursor.getLong(durationI) else 0L,\n                        isTrashed = false\n                    )\n                }\n            }\n        }\n\n        scan(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, false)\n        scan(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true)\n        result.sortedWith(compareByDescending<GalleryMedia> { it.dateAdded }.thenByDescending { it.id })\n    }\n'''
replace_once(repo, old_load_all, new_load_all)

picker = ROOT / "coreapp/src/main/kotlin/com/atmaca/gallery/InternalToolAlbumPicker.kt"
replace_once(picker, 'repository.loadAllInAlbum(album)', 'repository.loadAllInAlbumOemSafe(album)')

worker = ROOT / "coreapp/src/main/kotlin/com/atmaca/gallery/VideoFrameWorker.kt"
replace_once(
    worker,
    '''            ) { done, total ->\n                setProgressAsync(workDataOf(KEY_DONE to done, KEY_TOTAL to total))\n            }\n            runCatching { queue.delete() }\n            Result.success(''',
    '''            ) { done, total ->\n                setProgressAsync(workDataOf(KEY_DONE to done, KEY_TOTAL to total))\n                setForegroundAsync(createForegroundInfo(done, total))\n            }\n            runCatching { queue.delete() }\n            showCompletionNotification(result.created, result.failed)\n            Result.success('''
)
replace_once(
    worker,
    '''    private fun createForegroundInfo(): ForegroundInfo {''',
    '''    private fun createForegroundInfo(done: Int = 0, total: Int = 0): ForegroundInfo {'''
)
replace_once(
    worker,
    '''            .setContentText("Videolar arka planda işleniyor")\n            .setOngoing(true)''',
    '''            .setContentText(videoFrameProgressText(done, total))\n            .setProgress(total.coerceAtLeast(0), done.coerceIn(0, total.coerceAtLeast(0)), total <= 0)\n            .setOngoing(true)'''
)
replace_once(
    worker,
    '''        return ForegroundInfo(\n            FRAME_NOTIFICATION_ID,\n            notification,\n            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC\n        )\n    }\n}''',
    '''        return ForegroundInfo(\n            FRAME_NOTIFICATION_ID,\n            notification,\n            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC\n        )\n    }\n\n    private fun showCompletionNotification(created: Int, failed: Int) {\n        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager\n        val notification = Notification.Builder(applicationContext, FRAME_CHANNEL_ID)\n            .setSmallIcon(android.R.drawable.stat_sys_download_done)\n            .setContentTitle("ATMACA Video Kareleri tamamlandı")\n            .setContentText("$created kare oluşturuldu${if (failed > 0) " • $failed hata" else ""}")\n            .setAutoCancel(true)\n            .build()\n        manager.notify(FRAME_NOTIFICATION_ID + 1, notification)\n    }\n}'''
)

gallery = ROOT / "coreapp/src/main/kotlin/com/atmaca/gallery/GalleryApp.kt"
replace_once(
    gallery,
    '''    var screenshotMode by remember { mutableStateOf(false) }\n    var screenshotFolderUri by remember { mutableStateOf(prefs.getString("screenshot_tree_uri", null)) }''',
    '''    var screenshotMode by remember { mutableStateOf(false) }\n    var screenshotSourceItem by remember { mutableStateOf<GalleryMedia?>(null) }\n    var screenshotFolderUri by remember { mutableStateOf(prefs.getString("screenshot_tree_uri", null)) }'''
)
replace_once(
    gallery,
    '''    val screenshotFolderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->''',
    '''    val screenshotSourcePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->\n        if (uri != null) {\n            runCatching {\n                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)\n            }\n            val displayName = runCatching {\n                context.contentResolver.query(\n                    uri,\n                    arrayOf(MediaStore.MediaColumns.DISPLAY_NAME),\n                    null,\n                    null,\n                    null\n                )?.use { cursor ->\n                    if (cursor.moveToFirst()) cursor.getString(0).orEmpty() else ""\n                }.orEmpty()\n            }.getOrDefault("").ifBlank { "Screenshot kaynağı" }\n            screenshotSourceItem = GalleryMedia(\n                id = Long.MIN_VALUE,\n                uri = uri,\n                name = displayName,\n                mimeType = context.contentResolver.getType(uri),\n                isVideo = false,\n                dateAdded = 0L,\n                dateModified = 0L,\n                dateTaken = 0L,\n                width = 0,\n                height = 0,\n                bucketId = 0L,\n                bucketName = null,\n                relativePath = "",\n                size = 0L,\n                durationMs = 0L,\n                isTrashed = false\n            )\n            screenshotMode = true\n            Toast.makeText(context, "Screenshot için fotoğraf seçildi", Toast.LENGTH_SHORT).show()\n        }\n    }\n\n    val screenshotFolderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->'''
)
replace_once(
    gallery,
    '''    fun captureCleanScreenshot() {\n        if (captureInProgress || currentItem?.isVideo != false) return''',
    '''    fun captureCleanScreenshot() {\n        if (captureInProgress || (screenshotSourceItem == null && currentItem?.isVideo != false)) return'''
)
replace_once(
    gallery,
    '''        HorizontalPager(\n            state = pager,\n            userScrollEnabled = shouldEnablePager(currentScale, currentRotation),\n            modifier = Modifier.fillMaxSize()\n        ) { page ->\n            val item = items[page]\n            val rotation = rotations[item.id] ?: 0f\n            if (item.isVideo) {\n                VideoPage(item, rotation)\n            } else {\n                StablePhotoPage(\n                    item = item,\n                    rotation = rotation,\n                    onRotationChanged = { rotations[item.id] = it },\n                    onScaleChanged = { zooms[item.id] = it },\n                    onGestureActive = { gestureActive = it },\n                    onFitTap = { controlsVisible = !controlsVisible }\n                )\n            }\n        }''',
    '''        val explicitScreenshotSource = screenshotSourceItem\n        if (screenshotMode && explicitScreenshotSource != null) {\n            val rotation = rotations[explicitScreenshotSource.id] ?: 0f\n            StablePhotoPage(\n                item = explicitScreenshotSource,\n                rotation = rotation,\n                onRotationChanged = { rotations[explicitScreenshotSource.id] = it },\n                onScaleChanged = { zooms[explicitScreenshotSource.id] = it },\n                onGestureActive = { gestureActive = it },\n                onFitTap = { controlsVisible = !controlsVisible }\n            )\n        } else {\n            HorizontalPager(\n                state = pager,\n                userScrollEnabled = shouldEnablePager(currentScale, currentRotation),\n                modifier = Modifier.fillMaxSize()\n            ) { page ->\n                val item = items[page]\n                val rotation = rotations[item.id] ?: 0f\n                if (item.isVideo) {\n                    VideoPage(item, rotation)\n                } else {\n                    StablePhotoPage(\n                        item = item,\n                        rotation = rotation,\n                        onRotationChanged = { rotations[item.id] = it },\n                        onScaleChanged = { zooms[item.id] = it },\n                        onGestureActive = { gestureActive = it },\n                        onFitTap = { controlsVisible = !controlsVisible }\n                    )\n                }\n            }\n        }'''
)
replace_once(
    gallery,
    '''                                DropdownMenuItem(\n                                    text = { Text("Screenshot klasörü seç") },''',
    '''                                DropdownMenuItem(\n                                    text = { Text("Screenshot için fotoğraf seç") },\n                                    leadingIcon = { Icon(Icons.Default.PhotoCamera, null) },\n                                    onClick = {\n                                        optionsExpanded = false\n                                        screenshotSourcePicker.launch(arrayOf("image/*"))\n                                    }\n                                )\n                                DropdownMenuItem(\n                                    text = { Text("Screenshot klasörü seç") },'''
)

print("visibility + picker fix applied")
