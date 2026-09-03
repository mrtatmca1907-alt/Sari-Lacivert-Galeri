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

app = Path("coreapp/src/main/kotlin/com/atmaca/gallery/GalleryApp.kt")
tools = Path("coreapp/src/main/kotlin/com/atmaca/gallery/CompleteSettingsExtras.kt")
engine = Path("coreapp/src/main/kotlin/com/atmaca/gallery/CompleteToolEngine.kt")

replace_once(
    app,
'''    var message by remember { mutableStateOf<String?>(null) }
    var cropItem by remember { mutableStateOf<GalleryMedia?>(null) }
''',
'''    var message by remember { mutableStateOf<String?>(null) }
    var cropItem by remember { mutableStateOf<GalleryMedia?>(null) }
    var albums by remember { mutableStateOf<List<GalleryAlbum>>(emptyList()) }
    var albumsLoading by remember { mutableStateOf(false) }
''',
    "persistent album state"
)

replace_once(
    app,
'''    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        pendingCameraUri?.let { uri ->
            actions.finishCameraImage(uri, result.resultCode == Activity.RESULT_OK)
        }
        pendingCameraUri = null
        refreshToken++
        albumsRefresh++
        vm.reload()
    }
''',
'''    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val success = result.resultCode == Activity.RESULT_OK
        pendingCameraUri?.let { uri ->
            actions.finishCameraImage(uri, success)
        }
        pendingCameraUri = null
        if (success) {
            albumsRefresh++
            if (shouldReloadPrimaryMediaAfterCamera(section, success = true)) refreshToken++
        }
    }
''',
    "camera return refresh"
)

replace_once(
    app,
'''    val albums by produceState<List<GalleryAlbum>>(
        initialValue = emptyList(), albumsRefresh, section, pathAction
    ) {
        if (section == HomeSection.ALBUMS || pathAction != null) {
            value = runCatching { repository.loadAlbums() }.getOrDefault(emptyList())
        }
    }
''',
'''    LaunchedEffect(albumsRefresh, section, pathAction) {
        if (section == HomeSection.ALBUMS || pathAction != null) {
            albumsLoading = true
            val fresh = runCatching { repository.loadAlbums() }.getOrDefault(emptyList())
            albums = albumListWhileRefreshing(albums, fresh, refreshing = false)
            albumsLoading = false
        }
    }
''',
    "stable album refresh"
)

replace_once(
    app,
'''                onRefresh = {
                    selectedIds = emptySet()
                    refreshToken++
                    albumsRefresh++
                    duplicatesRefresh++
                    when (section) {
                        HomeSection.ALBUMS -> if (state.mode == CollectionMode.ALBUM) vm.reload()
                        HomeSection.DUPLICATES -> Unit
                        else -> vm.reload()
                    }
                },
''',
'''                onRefresh = {
                    selectedIds = emptySet()
                    when (section) {
                        HomeSection.ALBUMS -> {
                            albumsRefresh++
                            if (state.mode == CollectionMode.ALBUM) vm.reload()
                        }
                        HomeSection.DUPLICATES -> duplicatesRefresh++
                        else -> refreshToken++
                    }
                },
''',
    "single refresh path"
)

replace_once(
    tools,
'''    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        selectedUris = uris
        done = 0
        total = uris.size
    }
''',
'''    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        uris.forEach { uri ->
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        selectedUris = uris.distinct()
        done = 0
        total = selectedUris.size
    }
''',
    "persistent file picker permissions"
)

old_extract = '''    suspend fun extractVideoFrames(
        videos: List<Uri>,
        framesPerSecond: Int = 1,
        onProgress: (doneVideos: Int, totalVideos: Int) -> Unit = { _, _ -> }
    ): ToolRunResult = withContext(Dispatchers.IO) {
        val interval = frameIntervalMs(framesPerSecond)
        var created = 0
        var skipped = 0
        var failed = 0
        videos.forEachIndexed { videoIndex, uri ->
            coroutineContext.ensureActive()
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                val videoName = queryNameMime(uri).first.ifBlank { "video_${videoIndex + 1}.mp4" }
                val base = videoName.substringBeforeLast('.', videoName)
                val count = frameCount(duration, interval)
                if (count == 0) {
                    skipped++
                } else {
                    for (frameIndex in 0 until count) {
                        coroutineContext.ensureActive()
                        val timeUs = frameIndex.toLong() * interval * 1000L
                        val frame = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
                        if (frame == null) {
                            failed++
                            continue
                        }
                        val path = "Pictures/ATMACA Video Kareleri/${sanitizePathSegment(base)}/"
                        val ok = saveBitmapJpeg(frame, frameName(videoName, frameIndex + 1), path, 93)
                        frame.recycle()
                        if (ok) created++ else failed++
                    }
                }
            } catch (_: Throwable) {
                failed++
            } finally {
                runCatching { retriever.release() }
            }
            onProgress(videoIndex + 1, videos.size)
        }
        ToolRunResult(videos.size, created, skipped, failed)
    }
'''

new_extract = '''    suspend fun extractVideoFrames(
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
replace_once(engine, old_extract, new_extract, "frame level progress")

print("Stability/performance migration complete")
