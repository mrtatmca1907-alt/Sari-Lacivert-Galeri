from pathlib import Path


def replace_once(path: Path, old: str, new: str, label: str):
    text = path.read_text(encoding="utf-8")
    count = text.count(old)
    if count == 0:
        if new in text:
            print(f"{label}: already migrated")
            return
        raise SystemExit(f"{label}: source block not found")
    if count != 1:
        raise SystemExit(f"{label}: expected one source block, found {count}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")
    print(f"{label}: migrated")

engine = Path("coreapp/src/main/kotlin/com/atmaca/gallery/CompleteToolEngine.kt")
tools = Path("coreapp/src/main/kotlin/com/atmaca/gallery/CompleteSettingsExtras.kt")
app = Path("coreapp/src/main/kotlin/com/atmaca/gallery/GalleryApp.kt")

replace_once(
    engine,
    "import android.content.ContentValues\n",
    "import android.content.ContentUris\nimport android.content.ContentValues\n",
    "engine ContentUris import",
)
replace_once(
    engine,
    "import android.provider.MediaStore\n",
    "import android.provider.DocumentsContract\nimport android.provider.MediaStore\n",
    "engine DocumentsContract import",
)

old_extract = '''    suspend fun extractVideoFrames(
        videos: List<Uri>,
        framesPerSecond: Int = 1,
        onProgress: (doneFrames: Int, totalFrames: Int) -> Unit = { _, _ -> }
    ): ToolRunResult = withContext(Dispatchers.IO) {
        val interval = frameIntervalMs(framesPerSecond)
        data class FramePlan(val uri: Uri, val name: String, val count: Int)
        val plans = ArrayList<FramePlan>(videos.size)
        var skipped = 0
        var failed = 0

        videos.forEachIndexed { index, uri ->
            coroutineContext.ensureActive()
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                val name = queryNameMime(uri).first.ifBlank { "video_${index + 1}.mp4" }
                val count = frameCount(duration, interval)
                if (count <= 0) skipped++ else plans += FramePlan(uri, name, count)
            } catch (_: Throwable) {
                failed++
            } finally {
                runCatching { retriever.release() }
            }
        }

        val totalFrames = plans.sumOf { it.count }
        var doneFrames = 0
        var created = 0
        onProgress(0, totalFrames)

        plans.forEach { plan ->
            coroutineContext.ensureActive()
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, plan.uri)
                val base = plan.name.substringBeforeLast('.', plan.name)
                val outputPath = "Pictures/ATMACA Video Kareleri/${sanitizePathSegment(base)}/"
                for (frameIndex in 0 until plan.count) {
                    coroutineContext.ensureActive()
                    val timeUs = frameIndex.toLong() * interval * 1000L
                    val frame = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
                    if (frame == null) {
                        failed++
                    } else {
                        val ok = saveBitmapJpeg(frame, frameName(plan.name, frameIndex + 1), outputPath, 93)
                        frame.recycle()
                        if (ok) created++ else failed++
                    }
                    doneFrames++
                    onProgress(doneFrames, totalFrames)
                }
            } catch (_: Throwable) {
                failed++
            } finally {
                runCatching { retriever.release() }
            }
        }
        ToolRunResult(videos.size, created, skipped, failed)
    }
'''
new_extract = '''    suspend fun extractVideoFrames(
        videos: List<Uri>,
        framesPerSecond: Int = 1,
        moveSourceAfterSuccess: Boolean = false,
        onProgress: (doneFrames: Int, totalFrames: Int) -> Unit = { _, _ -> }
    ): ToolRunResult = withContext(Dispatchers.IO) {
        val interval = frameIntervalMs(framesPerSecond)
        data class FramePlan(val uri: Uri, val name: String, val count: Int)
        val plans = ArrayList<FramePlan>(videos.size)
        var skipped = 0
        var failed = 0

        videos.forEachIndexed { index, uri ->
            coroutineContext.ensureActive()
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                val name = queryNameMime(uri).first.ifBlank { "video_${index + 1}.mp4" }
                val count = frameCount(duration, interval)
                if (count <= 0) skipped++ else plans += FramePlan(uri, name, count)
            } catch (_: Throwable) {
                failed++
            } finally {
                runCatching { retriever.release() }
            }
        }

        val totalFrames = plans.sumOf { it.count }
        var doneFrames = 0
        var created = 0
        onProgress(0, totalFrames)

        plans.forEach { plan ->
            coroutineContext.ensureActive()
            val retriever = MediaMetadataRetriever()
            var videoCreated = 0
            var videoFailed = 0
            val outputPath = videoFrameOutputPath(plan.name)
            try {
                retriever.setDataSource(context, plan.uri)
                for (frameIndex in 0 until plan.count) {
                    coroutineContext.ensureActive()
                    val timeUs = frameIndex.toLong() * interval * 1000L
                    val frame = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
                    if (frame == null) {
                        failed++
                        videoFailed++
                    } else {
                        val ok = saveBitmapJpeg(frame, frameName(plan.name, frameIndex + 1), outputPath, 93)
                        frame.recycle()
                        if (ok) {
                            created++
                            videoCreated++
                        } else {
                            failed++
                            videoFailed++
                        }
                    }
                    doneFrames++
                    onProgress(doneFrames, totalFrames)
                }
            } catch (_: Throwable) {
                failed++
                videoFailed++
            } finally {
                runCatching { retriever.release() }
            }

            if (moveSourceAfterSuccess && shouldMoveVideoAfterFrames(videoCreated, videoFailed)) {
                if (!moveVideoToOutputFolder(plan.uri, outputPath)) failed++
            }
        }
        ToolRunResult(videos.size, created, skipped, failed)
    }
'''
replace_once(engine, old_extract, new_extract, "background frame extraction and move")

insert_before = '''    private fun sanitizePathSegment(raw: String): String = raw
        .replace(Regex("[\\\\/:*?\\\"<>|]"), "_")
        .trim()
        .ifBlank { "Video" }
'''
helpers = '''    private fun moveVideoToOutputFolder(source: Uri, outputPath: String): Boolean {
        if (Build.VERSION.SDK_INT < 29) return false
        val mediaUri = resolveVideoMediaStoreUri(source) ?: return false
        return runCatching {
            resolver.update(
                mediaUri,
                ContentValues().apply { put(MediaStore.MediaColumns.RELATIVE_PATH, outputPath) },
                null,
                null
            ) > 0
        }.getOrDefault(false)
    }

    private fun resolveVideoMediaStoreUri(source: Uri): Uri? {
        if (source.authority == MediaStore.AUTHORITY) return source

        if (source.authority == "com.android.providers.media.documents") {
            val docId = runCatching { DocumentsContract.getDocumentId(source) }.getOrNull() ?: return null
            val parts = docId.split(':', limit = 2)
            if (parts.size == 2 && parts[0].equals("video", true)) {
                val id = parts[1].toLongOrNull() ?: return null
                return ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
            }
        }

        if (source.authority == "com.android.externalstorage.documents") {
            val docId = runCatching { DocumentsContract.getDocumentId(source) }.getOrNull() ?: return null
            val relative = docId.substringAfter(':', "").trimStart('/')
            if (relative.isBlank()) return null
            val name = relative.substringAfterLast('/')
            val folder = relative.substringBeforeLast('/', "")
            val relativePath = if (folder.isBlank()) "" else "$folder/"
            val projection = arrayOf(MediaStore.Video.Media._ID)
            val selection = if (relativePath.isBlank()) {
                "${MediaStore.Video.Media.DISPLAY_NAME}=?"
            } else {
                "${MediaStore.Video.Media.DISPLAY_NAME}=? AND ${MediaStore.Video.Media.RELATIVE_PATH}=?"
            }
            val args = if (relativePath.isBlank()) arrayOf(name) else arrayOf(name, relativePath)
            return runCatching {
                resolver.query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, projection, selection, args, null)?.use { cursor ->
                    if (!cursor.moveToFirst()) return@use null
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID))
                    ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                }
            }.getOrNull()
        }
        return null
    }

''' + insert_before
replace_once(engine, insert_before, helpers, "video source move helpers")

old_picker = '''    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        uris.forEach { uri ->
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        selectedUris = uris.distinct()
        done = 0
        total = selectedUris.size
    }
'''
new_picker = '''    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        uris.forEach { uri ->
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        job = scope.launch {
            val filtered = filterToolUris(context, uris, tool)
            selectedUris = filtered
            done = 0
            total = filtered.size
            if (uris.isNotEmpty() && filtered.isEmpty()) {
                Toast.makeText(context, "Bu araç için uygun dosya bulunamadı", Toast.LENGTH_SHORT).show()
            }
        }
    }
'''
replace_once(tools, old_picker, new_picker, "OEM-safe picker post filter")

old_launch = '''    fun launchPicker() {
        when (tool) {
            AtmacaToolPage.PERSON_CROP -> picker.launch(arrayOf("image/*"))
            AtmacaToolPage.PACKAGER -> picker.launch(arrayOf("image/*", "video/*"))
            AtmacaToolPage.VIDEO_FRAMES -> picker.launch(arrayOf("video/*"))
        }
    }
'''
new_launch = '''    fun launchPicker() {
        picker.launch(toolPickerMimeTypes(tool).toTypedArray())
    }
'''
replace_once(tools, old_launch, new_launch, "OEM-safe picker mime")

old_start = '''    fun start() {
        if (selectedUris.isEmpty() || running || scanning) return
        running = true
        done = 0
        total = selectedUris.size
        job = scope.launch {
            val result = when (tool) {
                AtmacaToolPage.PERSON_CROP -> engine.smartPersonCrop(selectedUris, maxFacesPerPhoto = maxFaces) { d, t -> done = d; total = t }
                AtmacaToolPage.PACKAGER -> engine.packageMedia(selectedUris, batchSize = batchSize) { d, t -> done = d; total = t }
                AtmacaToolPage.VIDEO_FRAMES -> engine.extractVideoFrames(selectedUris, framesPerSecond = framesPerSecond) { d, t -> done = d; total = t }
            }
            running = false
            Toast.makeText(context, "${result.created} oluşturuldu • ${result.skipped} atlandı • ${result.failed} hata", Toast.LENGTH_LONG).show()
        }
    }
'''
new_start = '''    fun start() {
        if (selectedUris.isEmpty() || running || scanning) return
        if (tool == AtmacaToolPage.VIDEO_FRAMES) {
            val workId = enqueueVideoFrameWork(context, selectedUris, framesPerSecond)
            if (workId != null) {
                Toast.makeText(context, "Video Kareleri arka planda başlatıldı", Toast.LENGTH_LONG).show()
                onDismiss()
            } else {
                Toast.makeText(context, "Arka plan işi başlatılamadı", Toast.LENGTH_LONG).show()
            }
            return
        }
        running = true
        done = 0
        total = selectedUris.size
        job = scope.launch {
            val result = when (tool) {
                AtmacaToolPage.PERSON_CROP -> engine.smartPersonCrop(selectedUris, maxFacesPerPhoto = maxFaces) { d, t -> done = d; total = t }
                AtmacaToolPage.PACKAGER -> engine.packageMedia(selectedUris, batchSize = batchSize) { d, t -> done = d; total = t }
                AtmacaToolPage.VIDEO_FRAMES -> error("Video Kareleri WorkManager üzerinden çalışır")
            }
            running = false
            Toast.makeText(context, "${result.created} oluşturuldu • ${result.skipped} atlandı • ${result.failed} hata", Toast.LENGTH_LONG).show()
        }
    }
'''
replace_once(tools, old_start, new_start, "background video start")

replace_once(
    tools,
    'AtmacaToolPage.VIDEO_FRAMES -> "Her videodan belirlediğin kare hızında JPEG üretir; her video kendi isimli çıktı klasörüne gider."',
    'AtmacaToolPage.VIDEO_FRAMES -> "Arka planda kareleri üretir; işlenen video kopyalanmadan kendi kare klasörüne taşınır."',
    "video tool description",
)

replace_once(
    app,
    "onOpen = { album -> vm.openAlbum(album.relativePath) }",
    "onOpen = { album -> vm.openAlbum(album) }",
    "album bucket open",
)

print("Background tools/album migration complete")
