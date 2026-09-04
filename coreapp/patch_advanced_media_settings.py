from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one match, found {count}")
    return text.replace(old, new, 1)


def replace_exact(text: str, old: str, new: str, expected: int, label: str) -> str:
    count = text.count(old)
    if count != expected:
        raise RuntimeError(f"{label}: expected {expected} matches, found {count}")
    return text.replace(old, new)

# Extend MediaStore metadata so 'modified' and 'taken' are real columns, not aliases.
repo_path = Path("coreapp/src/main/kotlin/com/atmaca/gallery/MediaStoreRepository.kt")
repo = repo_path.read_text(encoding="utf-8")
repo = replace_once(repo,
    "    val dateAdded: Long,\n    val width: Int,",
    "    val dateAdded: Long,\n    val dateModified: Long,\n    val dateTaken: Long,\n    val width: Int,",
    "GalleryMedia dates")
repo = replace_once(repo,
    "        private val date = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)\n        private val width",
    "        private val date = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)\n        private val dateModified = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)\n        private val dateTaken = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_TAKEN)\n        private val width",
    "cursor date columns")
repo = replace_once(repo,
    "                dateAdded = cursor.getLong(date),\n                width = cursor.getInt(width),",
    "                dateAdded = cursor.getLong(date),\n                dateModified = cursor.getLong(dateModified),\n                dateTaken = if (dateTaken >= 0) cursor.getLong(dateTaken) else 0L,\n                width = cursor.getInt(width),",
    "read dates")
repo = replace_once(repo,
    "            add(MediaStore.MediaColumns.DATE_ADDED)\n            add(MediaStore.MediaColumns.WIDTH)",
    "            add(MediaStore.MediaColumns.DATE_ADDED)\n            add(MediaStore.MediaColumns.DATE_MODIFIED)\n            add(MediaStore.MediaColumns.DATE_TAKEN)\n            add(MediaStore.MediaColumns.WIDTH)",
    "projection dates")
repo_path.write_text(repo, encoding="utf-8")

# Base home/tools patches create the Settings UI; upgrade their filter/sort implementation here.
app_path = Path("coreapp/src/main/kotlin/com/atmaca/gallery/GalleryApp.kt")
app = app_path.read_text(encoding="utf-8")
app = app.replace("MediaSort.NEWEST.name", "MediaSort.TAKEN.name")
app = app.replace("}.getOrDefault(MediaSort.NEWEST)", "}.getOrDefault(MediaSort.TAKEN)")

app = replace_once(app,
    "    var showSettings by remember { mutableStateOf(false) }",
    '''    var sortDirection by rememberSaveable {
        mutableStateOf(
            runCatching {
                SortDirection.valueOf(
                    prefs.getString("sort_direction", SortDirection.DESCENDING.name)
                        ?: SortDirection.DESCENDING.name
                )
            }.getOrDefault(SortDirection.DESCENDING)
        )
    }
    var showSettings by remember { mutableStateOf(false) }''',
    "persistent sort direction state")

app = replace_once(app,
    '''            onMediaSort = { sort ->
                mediaSort = sort
                selectedIds = emptySet()
                prefs.edit().putString("media_sort", sort.name).apply()
            },
            onOpenTrash = {''',
    '''            onMediaSort = { sort ->
                mediaSort = sort
                selectedIds = emptySet()
                prefs.edit().putString("media_sort", sort.name).apply()
            },
            onSortDirection = { direction ->
                sortDirection = direction
                selectedIds = emptySet()
                prefs.edit().putString("sort_direction", direction.name).apply()
            },
            onOpenTrash = {''',
    "settings sort direction callback")

app = replace_exact(app,
    "                        mediaSort = mediaSort,\n                        selectedIds = selectedIds,",
    "                        mediaSort = mediaSort,\n                        sortDirection = sortDirection,\n                        selectedIds = selectedIds,",
    1,
    "main sort direction argument")
app = replace_exact(app,
    "                            mediaSort = mediaSort,\n                            selectedIds = selectedIds,",
    "                            mediaSort = mediaSort,\n                            sortDirection = sortDirection,\n                            selectedIds = selectedIds,",
    1,
    "album sort direction argument")

app = replace_once(app,
    '''    mediaFilter: MediaFilter,
    mediaSort: MediaSort,
    selectedIds: Set<Long>,''',
    '''    mediaFilter: MediaFilter,
    mediaSort: MediaSort,
    sortDirection: SortDirection,
    selectedIds: Set<Long>,''',
    "MediaCollection sort direction signature")

app = replace_once(app,
    '''    val visibleItems = remember(state.items, mediaFilter, mediaSort) {
        val filtered = state.items.filter { mediaFilterAccepts(it.isVideo, mediaFilter) }
        when (mediaSort) {
            MediaSort.NEWEST -> filtered.sortedByDescending { it.dateAdded }
            MediaSort.OLDEST -> filtered.sortedBy { it.dateAdded }
            MediaSort.NAME -> filtered.sortedBy { it.name.lowercase() }
        }
    }''',
    '''    val visibleItems = remember(state.items, mediaFilter, mediaSort, sortDirection) {
        val filtered = state.items.filter { mediaFilterAccepts(it.isVideo, it.mimeType, mediaFilter) }
        val ordered = when (mediaSort) {
            MediaSort.NAME -> filtered.sortedBy { it.name.lowercase() }
            MediaSort.PATH -> filtered.sortedWith(compareBy({ it.relativePath.lowercase() }, { it.name.lowercase() }))
            MediaSort.SIZE -> filtered.sortedBy { it.size }
            MediaSort.MODIFIED -> filtered.sortedBy { it.dateModified }
            MediaSort.TAKEN -> filtered.sortedBy { if (it.dateTaken > 0L) it.dateTaken else it.dateAdded * 1000L }
            MediaSort.RANDOM -> filtered.shuffled()
        }
        if (mediaSort == MediaSort.RANDOM) ordered else applySortDirection(ordered, sortDirection)
    }''',
    "advanced visible sorting")

app = replace_once(app,
    '''    mediaFilter: MediaFilter,
    mediaSort: MediaSort,
    onDismiss: () -> Unit,
    onGridColumns: (Int) -> Unit,
    onMediaFilter: (MediaFilter) -> Unit,
    onMediaSort: (MediaSort) -> Unit,
    onOpenTrash: () -> Unit,''',
    '''    mediaFilter: MediaFilter,
    mediaSort: MediaSort,
    sortDirection: SortDirection,
    onDismiss: () -> Unit,
    onGridColumns: (Int) -> Unit,
    onMediaFilter: (MediaFilter) -> Unit,
    onMediaSort: (MediaSort) -> Unit,
    onSortDirection: (SortDirection) -> Unit,
    onOpenTrash: () -> Unit,''',
    "SettingsDialog sort direction signature")

app = replace_once(app,
    '''            mediaFilter = mediaFilter,
            mediaSort = mediaSort,
            onDismiss = {''',
    '''            mediaFilter = mediaFilter,
            mediaSort = mediaSort,
            sortDirection = sortDirection,
            onDismiss = {''',
    "SettingsDialog sort direction argument")

app = replace_once(app,
    '''                Column {
                    MediaSort.entries.forEachIndexed { index, sort ->
                        TextButton(onClick = { onMediaSort(sort) }) {
                            val label = mediaSortLabels()[index]
                            Text(if (sort == mediaSort) "[$label]" else label)
                        }
                    }
                }
                Text("Izgara sütunu", style = MaterialTheme.typography.labelLarge)''',
    '''                Column {
                    MediaSort.entries.forEachIndexed { index, sort ->
                        TextButton(onClick = { onMediaSort(sort) }) {
                            val label = mediaSortLabels()[index]
                            Text(if (sort == mediaSort) "[$label]" else label)
                        }
                    }
                }
                if (mediaSort != MediaSort.RANDOM) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SortDirection.entries.forEachIndexed { index, direction ->
                            TextButton(onClick = { onSortDirection(direction) }) {
                                val label = sortDirectionLabels()[index]
                                Text(if (direction == sortDirection) "[$label]" else label)
                            }
                        }
                    }
                }
                Text("Izgara sütunu", style = MaterialTheme.typography.labelLarge)''',
    "SettingsDialog direction UI")

app_path.write_text(app, encoding="utf-8")
print("Advanced media settings patch applied")
