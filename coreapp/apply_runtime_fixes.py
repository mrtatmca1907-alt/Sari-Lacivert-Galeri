from pathlib import Path

path = Path("coreapp/src/main/kotlin/com/atmaca/gallery/GalleryApp.kt")
text = path.read_text(encoding="utf-8")


def replace_once(old: str, new: str, label: str) -> None:
    global text
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: beklenen 1 eslesme, bulunan {count}")
    text = text.replace(old, new, 1)


if "import androidx.compose.foundation.gestures.scrollBy\n" not in text:
    replace_once(
        "import androidx.compose.foundation.gestures.detectTransformGestures\n",
        "import androidx.compose.foundation.gestures.detectTransformGestures\nimport androidx.compose.foundation.gestures.scrollBy\n",
        "scrollBy import"
    )

if "private const val HIGH_QUALITY_THUMBNAIL_EDGE = 720" not in text:
    replace_once(
        "private enum class PathAction { COPY, MOVE }\n",
        "private enum class PathAction { COPY, MOVE }\nprivate const val HIGH_QUALITY_THUMBNAIL_EDGE = 720\n",
        "high quality thumbnail constant"
    )

if "loadThumbnailCompat(context, item, HIGH_QUALITY_THUMBNAIL_EDGE)" not in text:
    replace_once(
        "val key = \"${item.uri}:${item.dateModified}:${item.size}:360\"",
        "val key = \"${item.uri}:${item.dateModified}:${item.size}:$HIGH_QUALITY_THUMBNAIL_EDGE\"",
        "thumbnail cache key"
    )
    replace_once(
        "loadThumbnailCompat(context, item, 360)",
        "loadThumbnailCompat(context, item, HIGH_QUALITY_THUMBNAIL_EDGE)",
        "thumbnail quality load"
    )

if "shouldLoadMoreForEmptyFilteredPage(\n            totalLoaded = state.items.size" not in text:
    replace_once(
        "        if (mediaSort == MediaSort.RANDOM) ordered else applySortDirection(ordered, sortDirection)\n    }\n\n    when {",
        "        if (mediaSort == MediaSort.RANDOM) ordered else applySortDirection(ordered, sortDirection)\n    }\n\n    LaunchedEffect(\n        state.items.size,\n        visibleItems.size,\n        state.hasMore,\n        state.loading,\n        mediaFilter\n    ) {\n        if (\n            shouldLoadMoreForEmptyFilteredPage(\n                totalLoaded = state.items.size,\n                filteredVisible = visibleItems.size,\n                hasMore = state.hasMore,\n                loading = state.loading\n            )\n        ) {\n            onLoadMore()\n        }\n    }\n\n    when {",
        "filtered paging wiring"
    )

if "var gridHeightPx by remember { mutableIntStateOf(0) }" not in text:
    replace_once(
        "    var dragY by remember { mutableFloatStateOf(0f) }\n    var lastDragIndex by remember { mutableIntStateOf(-1) }\n",
        "    var dragY by remember { mutableFloatStateOf(0f) }\n    var lastDragIndex by remember { mutableIntStateOf(-1) }\n    var gridHeightPx by remember { mutableIntStateOf(0) }\n    var dragActive by remember { mutableStateOf(false) }\n    val dragEdgePx = with(density) { 96.dp.toPx() }\n    val dragStepPx = with(density) { 36.dp.toPx() }\n",
        "drag state"
    )

if "dragAutoScrollDelta(" not in text:
    replace_once(
        "    LaunchedEffect(gridState, items.size, hasMore) {\n",
        "    LaunchedEffect(dragActive, gridHeightPx, items.size) {\n        while (dragActive) {\n            val edgeDelta = dragAutoScrollDelta(\n                pointerY = dragY,\n                viewportHeight = gridHeightPx.toFloat(),\n                edgePx = dragEdgePx\n            )\n            if (edgeDelta != 0f) {\n                gridState.scrollBy(edgeDelta * dragStepPx)\n                val safeY = dragY.coerceIn(\n                    0f,\n                    (gridHeightPx - 1).coerceAtLeast(0).toFloat()\n                )\n                selectAt(dragX, safeY)\n            }\n            delay(16L)\n        }\n    }\n\n    LaunchedEffect(gridState, items.size, hasMore) {\n",
        "drag auto scroll loop"
    )

if ".onSizeChanged { gridHeightPx = it.height }\n            .pointerInput(items.size, columns)" not in text:
    replace_once(
        "        modifier = Modifier\n            .fillMaxSize()\n            .pointerInput(items.size, columns) {",
        "        modifier = Modifier\n            .fillMaxSize()\n            .onSizeChanged { gridHeightPx = it.height }\n            .pointerInput(items.size, columns) {",
        "grid size tracking"
    )

if "dragActive = true\n                        lastDragIndex = -1" not in text:
    replace_once(
        "                    onDragStart = { offset ->\n                        lastDragIndex = -1",
        "                    onDragStart = { offset ->\n                        dragActive = true\n                        lastDragIndex = -1",
        "drag start"
    )

if "onDragEnd = { dragActive = false; lastDragIndex = -1 }" not in text:
    replace_once(
        "                    onDragEnd = { lastDragIndex = -1 },\n                    onDragCancel = { lastDragIndex = -1 }",
        "                    onDragEnd = { dragActive = false; lastDragIndex = -1 },\n                    onDragCancel = { dragActive = false; lastDragIndex = -1 }",
        "drag end cancel"
    )

# Album-first sade galeri duzeni. Kök dosya secimine dokunmaz.
if "var section by rememberSaveable { mutableStateOf(HomeSection.ALBUMS) }" not in text:
    replace_once(
        "var section by rememberSaveable { mutableStateOf(HomeSection.MEDIA) }",
        "var section by rememberSaveable { mutableStateOf(HomeSection.ALBUMS) }",
        "album first home"
    )

if "NavigationBarItem(" in text:
    old_scaffold = '''    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = section == HomeSection.MEDIA,
                    onClick = { section = HomeSection.MEDIA },
                    icon = { Text("▣") },
                    label = { Text("Medya") }
                )
                NavigationBarItem(
                    selected = section == HomeSection.ALBUMS,
                    onClick = { section = HomeSection.ALBUMS },
                    icon = { Icon(Icons.Default.Collections, null) },
                    label = { Text("Albümler") }
                )
                NavigationBarItem(
                    selected = section == HomeSection.SETTINGS,
                    onClick = { section = HomeSection.SETTINGS; showSettings = true },
                    icon = { Icon(Icons.Default.Settings, null) },
                    label = { Text("Ayarlar") }
                )
            }
        }
    ) { padding ->'''
    new_scaffold = '''    Scaffold { padding ->'''
    replace_once(old_scaffold, new_scaffold, "remove bottom navigation")

if "showAllMedia = section == HomeSection.MEDIA" not in text:
    old_call = '''                onCamera = ::openCamera,
                onRefresh = {
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
                onSettings = { showSettings = true },
                showMore = showMore,
                onMoreChange = { showMore = it }
            )'''
    new_call = '''                onCamera = ::openCamera,
                onSettings = { showSettings = true },
                showAllMedia = section == HomeSection.MEDIA,
                onToggleView = {
                    selectedIds = emptySet()
                    section = if (section == HomeSection.MEDIA) HomeSection.ALBUMS else HomeSection.MEDIA
                },
                showMore = showMore,
                onMoreChange = { showMore = it }
            )'''
    replace_once(old_call, new_call, "top bar simple controls call")

if "showAllMedia: Boolean," not in text:
    old_signature = '''    onCamera: () -> Unit,
    onRefresh: () -> Unit,
    onSettings: () -> Unit,
    showMore: Boolean,'''
    new_signature = '''    onCamera: () -> Unit,
    onSettings: () -> Unit,
    showAllMedia: Boolean,
    onToggleView: () -> Unit,
    showMore: Boolean,'''
    replace_once(old_signature, new_signature, "top bar simple controls signature")

if "Icon(Icons.Default.Refresh, \"Yenile\")" in text:
    replace_once(
        '''        IconButton(onClick = onCamera) { Icon(Icons.Default.PhotoCamera, "Kamera") }
        IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, "Yenile") }
        Box {''',
        '''        Box {''',
        "remove permanent camera refresh buttons"
    )

if "Tüm klasör içeriğini göster" not in text:
    old_menu = '''            DropdownMenu(expanded = showMore, onDismissRequest = { onMoreChange(false) }) {
                DropdownMenuItem(
                    text = { Text("Ayarlar") },
                    leadingIcon = { Icon(Icons.Default.Settings, null) },
                    onClick = {
                        onMoreChange(false)
                        onSettings()
                    }
                )
            }'''
    new_menu = '''            DropdownMenu(expanded = showMore, onDismissRequest = { onMoreChange(false) }) {
                DropdownMenuItem(
                    text = { Text(if (showAllMedia) "Klasör görünümüne geç" else "Tüm klasör içeriğini göster") },
                    onClick = { onMoreChange(false); onToggleView() }
                )
                DropdownMenuItem(
                    text = { Text("Sıralama ölçütü") },
                    onClick = { onMoreChange(false); onSettings() }
                )
                DropdownMenuItem(
                    text = { Text("Medyayı filtrele") },
                    onClick = { onMoreChange(false); onSettings() }
                )
                DropdownMenuItem(
                    text = { Text("Kamerayı aç") },
                    leadingIcon = { Icon(Icons.Default.PhotoCamera, null) },
                    onClick = { onMoreChange(false); onCamera() }
                )
                DropdownMenuItem(
                    text = { Text("Görünüm ve diğer ayarlar") },
                    leadingIcon = { Icon(Icons.Default.Settings, null) },
                    onClick = { onMoreChange(false); onSettings() }
                )
            }'''
    replace_once(old_menu, new_menu, "compact overflow menu")

path.write_text(text, encoding="utf-8")

feature_path = Path("coreapp/src/main/kotlin/com/atmaca/gallery/FeatureRules.kt")
feature_text = feature_path.read_text(encoding="utf-8")
full_quality_old = '''fun calculateViewerDecodeSample(sourceWidth:Int,sourceHeight:Int,viewportWidth:Int,viewportHeight:Int):Int {
    if(sourceWidth<=0||sourceHeight<=0||viewportWidth<=0||viewportHeight<=0)return 1
    val sourceEdge=maxOf(sourceWidth,sourceHeight).toLong(); val targetEdge=maxOf(viewportWidth,viewportHeight).toLong()*2L
    if(targetEdge<=0L)return 1
    return (sourceEdge/targetEdge).toInt().coerceAtLeast(1)
}'''
full_quality_new = '''fun calculateViewerDecodeSample(sourceWidth:Int,sourceHeight:Int,viewportWidth:Int,viewportHeight:Int):Int = 1'''
if full_quality_new not in feature_text:
    count = feature_text.count(full_quality_old)
    if count != 1:
        raise SystemExit(f"full quality viewer decode: beklenen 1 eslesme, bulunan {count}")
    feature_text = feature_text.replace(full_quality_old, full_quality_new, 1)
feature_path.write_text(feature_text, encoding="utf-8")

repository_path = Path("coreapp/src/main/kotlin/com/atmaca/gallery/MediaStoreRepository.kt")
repository_text = repository_path.read_text(encoding="utf-8")
if "suspend fun loadMixedPageAfter(" not in repository_text:
    marker = "    private fun addAlbumSelector(\n"
    if repository_text.count(marker) != 1:
        raise SystemExit("keyset repository insertion marker bulunamadi")
    keyset_function = '''    suspend fun loadMixedPageAfter(
        afterDateAdded: Long?,
        afterId: Long?,
        limit: Int = PAGE_SIZE,
        albumPath: String? = null,
        albumBucketId: Long = 0L,
        albumBucketName: String? = null,
        trashedOnly: Boolean = false
    ): List<GalleryMedia> = withContext(Dispatchers.IO) {
        if (trashedOnly && Build.VERSION.SDK_INT < 30) return@withContext emptyList()
        val selectionParts = mutableListOf("(${MediaStore.Files.FileColumns.MEDIA_TYPE}=? OR ${MediaStore.Files.FileColumns.MEDIA_TYPE}=?)")
        val selectionArgs = mutableListOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString()
        )
        addAlbumSelector(selectionParts, selectionArgs, albumPath, albumBucketId, albumBucketName)
        if (afterDateAdded != null && afterId != null) {
            selectionParts += "(${MediaStore.MediaColumns.DATE_ADDED}<? OR (${MediaStore.MediaColumns.DATE_ADDED}=? AND ${MediaStore.Files.FileColumns._ID}<?))"
            selectionArgs += afterDateAdded.toString()
            selectionArgs += afterDateAdded.toString()
            selectionArgs += afterId.toString()
        }
        queryKeysetPage(selectionParts, selectionArgs, limit, trashedOnly)
    }

'''
    repository_text = repository_text.replace(marker, keyset_function + marker, 1)

if "private fun queryKeysetPage(" not in repository_text:
    marker = "    private fun sha256(uri: Uri): String? = runCatching {\n"
    if repository_text.count(marker) != 1:
        raise SystemExit("keyset query insertion marker bulunamadi")
    query_function = '''    private fun queryKeysetPage(
        selectionParts: List<String>,
        selectionArgs: List<String>,
        limit: Int,
        trashedOnly: Boolean
    ): List<GalleryMedia> {
        val args = Bundle().apply {
            putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selectionParts.joinToString(" AND "))
            putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selectionArgs.toTypedArray())
            putStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS, arrayOf(MediaStore.MediaColumns.DATE_ADDED, MediaStore.Files.FileColumns._ID))
            putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION, ContentResolver.QUERY_SORT_DIRECTION_DESCENDING)
            putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
            if (Build.VERSION.SDK_INT >= 30 && trashedOnly) putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_ONLY)
        }
        return queryMedia(args, limit)
    }

'''
    repository_text = repository_text.replace(marker, query_function + marker, 1)
repository_path.write_text(repository_text, encoding="utf-8")

view_model_path = Path("coreapp/src/main/kotlin/com/atmaca/gallery/GalleryViewModel.kt")
view_model_text = view_model_path.read_text(encoding="utf-8")
if "val lastLoaded = snapshot.items.lastOrNull()" not in view_model_text:
    marker = "            runCatching {\n                when (snapshot.mode) {\n"
    replacement = "            val lastLoaded = snapshot.items.lastOrNull()\n            runCatching {\n                when (snapshot.mode) {\n"
    if view_model_text.count(marker) != 1:
        raise SystemExit("view model last media marker bulunamadi")
    view_model_text = view_model_text.replace(marker, replacement, 1)

old_media = '''                    CollectionMode.MEDIA -> repository.loadMixedPage(
                        offset = snapshot.items.size,
                        limit = MediaStoreRepository.PAGE_SIZE
                    )'''
new_media = '''                    CollectionMode.MEDIA -> repository.loadMixedPageAfter(
                        afterDateAdded = lastLoaded?.dateAdded,
                        afterId = lastLoaded?.id,
                        limit = MediaStoreRepository.PAGE_SIZE
                    )'''
if new_media not in view_model_text:
    if view_model_text.count(old_media) != 1:
        raise SystemExit("view model media paging marker bulunamadi")
    view_model_text = view_model_text.replace(old_media, new_media, 1)

old_album = '''                        val fastPage = repository.loadMixedPage(
                            offset = snapshot.items.size,
                            limit = MediaStoreRepository.PAGE_SIZE,
                            albumPath = snapshot.albumPath,
                            albumBucketId = snapshot.albumBucketId,
                            albumBucketName = snapshot.albumBucketName
                        )'''
new_album = '''                        val fastPage = repository.loadMixedPageAfter(
                            afterDateAdded = lastLoaded?.dateAdded,
                            afterId = lastLoaded?.id,
                            limit = MediaStoreRepository.PAGE_SIZE,
                            albumPath = snapshot.albumPath,
                            albumBucketId = snapshot.albumBucketId,
                            albumBucketName = snapshot.albumBucketName
                        )'''
oem_primary_album = "val oemAll = repository.loadAllInAlbumOemSafe(album)"
if oem_primary_album not in view_model_text and new_album not in view_model_text:
    if view_model_text.count(old_album) != 1:
        raise SystemExit("view model album paging marker bulunamadi")
    view_model_text = view_model_text.replace(old_album, new_album, 1)
view_model_path.write_text(view_model_text, encoding="utf-8")

print("Runtime gallery fixes applied")
