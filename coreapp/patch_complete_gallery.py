from pathlib import Path

path = Path("coreapp/src/main/kotlin/com/atmaca/gallery/GalleryApp.kt")
text = path.read_text(encoding="utf-8")


def replace_once(old: str, new: str, label: str) -> None:
    global text
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one match, found {count}")
    text = text.replace(old, new, 1)


replace_once(
    "enum class HomeSection { PHOTOS, VIDEOS, ALBUMS, DUPLICATES, TRASH }",
    "enum class HomeSection { MEDIA, ALBUMS, SETTINGS, PHOTOS, VIDEOS, DUPLICATES, TRASH }",
    "HomeSection enum",
)
replace_once(
    "var section by rememberSaveable { mutableStateOf(HomeSection.PHOTOS) }",
    "var section by rememberSaveable { mutableStateOf(HomeSection.MEDIA) }",
    "default home section",
)
replace_once(
    '''    var gridColumns by rememberSaveable { mutableIntStateOf(prefs.getInt("grid_columns", 4).coerceIn(3, 6)) }
    var showSettings by remember { mutableStateOf(false) }''',
    '''    var gridColumns by rememberSaveable { mutableIntStateOf(prefs.getInt("grid_columns", 4).coerceIn(3, 6)) }
    var mediaFilter by rememberSaveable {
        mutableStateOf(
            runCatching {
                MediaFilter.valueOf(
                    prefs.getString("media_filter", MediaFilter.ALL.name) ?: MediaFilter.ALL.name
                )
            }.getOrDefault(MediaFilter.ALL)
        )
    }
    var mediaSort by rememberSaveable {
        mutableStateOf(
            runCatching {
                MediaSort.valueOf(
                    prefs.getString("media_sort", MediaSort.NEWEST.name) ?: MediaSort.NEWEST.name
                )
            }.getOrDefault(MediaSort.NEWEST)
        )
    }
    var showSettings by remember { mutableStateOf(false) }''',
    "persistent media settings state",
)
replace_once(
    '''    LaunchedEffect(section) {
        selectedIds = emptySet()
        viewerIndex = null
        when (section) {
            HomeSection.PHOTOS -> vm.switchTab(GalleryTab.PHOTOS)
            HomeSection.VIDEOS -> vm.switchTab(GalleryTab.VIDEOS)
            HomeSection.TRASH -> vm.openTrash()
            HomeSection.ALBUMS, HomeSection.DUPLICATES -> Unit
        }
    }

    LaunchedEffect(refreshToken) {
        if (refreshToken > 0 && section in listOf(HomeSection.PHOTOS, HomeSection.VIDEOS, HomeSection.TRASH)) {
            vm.reload()
        }
    }''',
    '''    LaunchedEffect(section) {
        selectedIds = emptySet()
        viewerIndex = null
        when (section) {
            HomeSection.MEDIA -> vm.openMedia()
            HomeSection.SETTINGS -> showSettings = true
            HomeSection.PHOTOS -> vm.switchTab(GalleryTab.PHOTOS)
            HomeSection.VIDEOS -> vm.switchTab(GalleryTab.VIDEOS)
            HomeSection.TRASH -> vm.openTrash()
            HomeSection.ALBUMS, HomeSection.DUPLICATES -> Unit
        }
    }

    LaunchedEffect(refreshToken) {
        if (refreshToken > 0 && section in listOf(HomeSection.MEDIA, HomeSection.PHOTOS, HomeSection.VIDEOS, HomeSection.TRASH)) {
            vm.reload()
        }
    }''',
    "section effects",
)
replace_once(
    '''    if (showSettings) {
        SettingsDialog(
            gridColumns = gridColumns,
            onDismiss = { showSettings = false },
            onGridColumns = { columns ->
                gridColumns = columns
                prefs.edit().putInt("grid_columns", columns).apply()
            }
        )
    }''',
    '''    if (showSettings) {
        SettingsDialog(
            gridColumns = gridColumns,
            mediaFilter = mediaFilter,
            mediaSort = mediaSort,
            onDismiss = {
                showSettings = false
                if (section == HomeSection.SETTINGS) section = HomeSection.MEDIA
            },
            onGridColumns = { columns ->
                gridColumns = columns
                prefs.edit().putInt("grid_columns", columns).apply()
            },
            onMediaFilter = { filter ->
                mediaFilter = filter
                selectedIds = emptySet()
                prefs.edit().putString("media_filter", filter.name).apply()
            },
            onMediaSort = { sort ->
                mediaSort = sort
                selectedIds = emptySet()
                prefs.edit().putString("media_sort", sort.name).apply()
            }
        )
    }''',
    "settings dialog wiring",
)
replace_once(
    '''            NavigationBar {
                NavigationBarItem(
                    selected = section == HomeSection.PHOTOS,
                    onClick = { section = HomeSection.PHOTOS },
                    icon = { Text("▣") },
                    label = { Text("Foto") }
                )
                NavigationBarItem(
                    selected = section == HomeSection.VIDEOS,
                    onClick = { section = HomeSection.VIDEOS },
                    icon = { Icon(Icons.Default.PlayArrow, null) },
                    label = { Text("Video") }
                )
                NavigationBarItem(
                    selected = section == HomeSection.ALBUMS,
                    onClick = { section = HomeSection.ALBUMS },
                    icon = { Icon(Icons.Default.Collections, null) },
                    label = { Text("Albüm") }
                )
                NavigationBarItem(
                    selected = section == HomeSection.DUPLICATES,
                    onClick = { section = HomeSection.DUPLICATES },
                    icon = { Icon(Icons.Default.FindInPage, null) },
                    label = { Text("Kopya") }
                )
                NavigationBarItem(
                    selected = section == HomeSection.TRASH,
                    onClick = { section = HomeSection.TRASH },
                    icon = { Icon(Icons.Default.Delete, null) },
                    label = { Text("Çöp") }
                )
            }''',
    '''            NavigationBar {
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
            }''',
    "bottom navigation",
)
replace_once(
    '''                    section == HomeSection.PHOTOS -> "Fotoğraflar"
                    section == HomeSection.VIDEOS -> "Videolar"
                    section == HomeSection.ALBUMS -> "Albümler"
                    section == HomeSection.DUPLICATES -> "Yinelenenler"
                    else -> "Çöp Kutusu"''',
    '''                    section == HomeSection.MEDIA -> "Medya"
                    section == HomeSection.SETTINGS -> "Ayarlar"
                    section == HomeSection.PHOTOS -> "Fotoğraflar"
                    section == HomeSection.VIDEOS -> "Videolar"
                    section == HomeSection.ALBUMS -> "Albümler"
                    section == HomeSection.DUPLICATES -> "Yinelenenler"
                    else -> "Çöp Kutusu"''',
    "top title",
)
replace_once(
    "HomeSection.PHOTOS, HomeSection.VIDEOS, HomeSection.TRASH -> {",
    "HomeSection.MEDIA, HomeSection.SETTINGS, HomeSection.PHOTOS, HomeSection.VIDEOS, HomeSection.TRASH -> {",
    "media collection branch",
)
replace_once(
    '''                        state = state,
                        gridColumns = gridColumns,
                        selectedIds = selectedIds,''',
    '''                        state = state,
                        gridColumns = gridColumns,
                        mediaFilter = mediaFilter,
                        mediaSort = mediaSort,
                        selectedIds = selectedIds,''',
    "main media settings arguments",
)
replace_once(
    '''                            state = state,
                            gridColumns = gridColumns,
                            selectedIds = selectedIds,''',
    '''                            state = state,
                            gridColumns = gridColumns,
                            mediaFilter = mediaFilter,
                            mediaSort = mediaSort,
                            selectedIds = selectedIds,''',
    "album media settings arguments",
)
replace_once(
    '''private fun MediaCollection(
    state: GalleryUiState,
    gridColumns: Int,
    selectedIds: Set<Long>,''',
    '''private fun MediaCollection(
    state: GalleryUiState,
    gridColumns: Int,
    mediaFilter: MediaFilter,
    mediaSort: MediaSort,
    selectedIds: Set<Long>,''',
    "MediaCollection signature",
)
replace_once(
    '''    when {
        state.items.isEmpty() && state.loading -> Box(''',
    '''    val visibleItems = remember(state.items, mediaFilter, mediaSort) {
        val filtered = state.items.filter { mediaFilterAccepts(it.isVideo, mediaFilter) }
        when (mediaSort) {
            MediaSort.NEWEST -> filtered.sortedByDescending { it.dateAdded }
            MediaSort.OLDEST -> filtered.sortedBy { it.dateAdded }
            MediaSort.NAME -> filtered.sortedBy { it.name.lowercase() }
        }
    }

    when {
        state.items.isEmpty() && state.loading -> Box(''',
    "MediaCollection visible items",
)
replace_once(
    '''        state.items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Burada medya yok")
        }

        else -> MediaGrid(
            items = state.items,
            loading = state.loading,
            hasMore = state.hasMore,
            columns = gridColumns,
            selectedIds = selectedIds,
            onToggleSelection = onToggleSelection,
            onOpen = onOpen,
            onLoadMore = onLoadMore
        )''',
    '''        state.items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Burada medya yok")
        }

        visibleItems.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Bu filtrede medya yok")
        }

        else -> MediaGrid(
            items = visibleItems,
            loading = state.loading,
            hasMore = state.hasMore,
            columns = gridColumns,
            selectedIds = selectedIds,
            onToggleSelection = onToggleSelection,
            onOpen = { visibleIndex ->
                val target = visibleItems.getOrNull(visibleIndex)
                val originalIndex = target?.let { item ->
                    state.items.indexOfFirst { it.id == item.id && it.isVideo == item.isVideo }
                } ?: -1
                if (originalIndex >= 0) onOpen(originalIndex)
            },
            onLoadMore = onLoadMore
        )''',
    "MediaCollection filtered grid",
)
replace_once(
    '''private fun SettingsDialog(
    gridColumns: Int,
    onDismiss: () -> Unit,
    onGridColumns: (Int) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Galeri ayarları") },
        text = {
            Column {
                Text("Izgara sütunu")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (3..6).forEach { columns ->
                        TextButton(onClick = { onGridColumns(columns) }) {
                            Text(if (columns == gridColumns) "[$columns]" else "$columns")
                        }
                    }
                }
                Text(
                    "Yüksek çözünürlüklü görüntüleyici, video kontrolleri ve sayfalı medya yükleme etkindir.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tamam") } }
    )
}''',
    '''private fun SettingsDialog(
    gridColumns: Int,
    mediaFilter: MediaFilter,
    mediaSort: MediaSort,
    onDismiss: () -> Unit,
    onGridColumns: (Int) -> Unit,
    onMediaFilter: (MediaFilter) -> Unit,
    onMediaSort: (MediaSort) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Galeri ayarları") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Gösterilecek medya", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    MediaFilter.entries.forEachIndexed { index, filter ->
                        TextButton(onClick = { onMediaFilter(filter) }) {
                            val label = mediaFilterLabels()[index]
                            Text(if (filter == mediaFilter) "[$label]" else label)
                        }
                    }
                }
                Text("Sıralama", style = MaterialTheme.typography.labelLarge)
                Column {
                    MediaSort.entries.forEachIndexed { index, sort ->
                        TextButton(onClick = { onMediaSort(sort) }) {
                            val label = mediaSortLabels()[index]
                            Text(if (sort == mediaSort) "[$label]" else label)
                        }
                    }
                }
                Text("Izgara sütunu", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (3..6).forEach { columns ->
                        TextButton(onClick = { onGridColumns(columns) }) {
                            Text(if (columns == gridColumns) "[$columns]" else "$columns")
                        }
                    }
                }
                Text(
                    "Filtre, sıralama ve ızgara seçimi cihazda saklanır.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tamam") } }
    )
}''',
    "SettingsDialog filter and sort UI",
)
replace_once(
    '''        if (selected) {
            Box(''',
    '''        Text(
            mediaNameOverlay(item.name),
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.58f))
                .padding(horizontal = 5.dp, vertical = 3.dp)
        )
        if (selected) {
            Box(''',
    "thumbnail name overlay",
)

path.write_text(text, encoding="utf-8")
print("Complete gallery home patch applied")
