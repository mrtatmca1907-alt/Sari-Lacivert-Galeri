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
    "import androidx.compose.foundation.gestures.detectDragGestures\n",
    "import androidx.compose.foundation.gestures.detectDragGestures\nimport androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress\n",
    "drag selection import",
)

replace_once(
    '''            onMediaSort = { sort ->
                mediaSort = sort
                selectedIds = emptySet()
                prefs.edit().putString("media_sort", sort.name).apply()
            }
        )''',
    '''            onMediaSort = { sort ->
                mediaSort = sort
                selectedIds = emptySet()
                prefs.edit().putString("media_sort", sort.name).apply()
            },
            onOpenTrash = {
                showSettings = false
                section = HomeSection.TRASH
            },
            onOpenDuplicates = {
                showSettings = false
                section = HomeSection.DUPLICATES
            }
        )''',
    "settings tool callbacks",
)

replace_once(
    '''private fun SettingsDialog(
    gridColumns: Int,
    mediaFilter: MediaFilter,
    mediaSort: MediaSort,
    onDismiss: () -> Unit,
    onGridColumns: (Int) -> Unit,
    onMediaFilter: (MediaFilter) -> Unit,
    onMediaSort: (MediaSort) -> Unit
) {''',
    '''private fun SettingsDialog(
    gridColumns: Int,
    mediaFilter: MediaFilter,
    mediaSort: MediaSort,
    onDismiss: () -> Unit,
    onGridColumns: (Int) -> Unit,
    onMediaFilter: (MediaFilter) -> Unit,
    onMediaSort: (MediaSort) -> Unit,
    onOpenTrash: () -> Unit,
    onOpenDuplicates: () -> Unit
) {''',
    "complete settings signature",
)

replace_once(
    '''                Text(
                    "Filtre, sıralama ve ızgara seçimi cihazda saklanır.",
                    style = MaterialTheme.typography.bodySmall
                )
            }''',
    '''                Text(
                    "Filtre, sıralama ve ızgara seçimi cihazda saklanır.",
                    style = MaterialTheme.typography.bodySmall
                )
                CompleteSettingsExtras(
                    onOpenTrash = onOpenTrash,
                    onOpenDuplicates = onOpenDuplicates
                )
            }''',
    "settings extras UI",
)

replace_once(
    '''                    onClear = { selectedIds = emptySet() },
                    onShare = { share(selected) },''',
    '''                    onClear = { selectedIds = emptySet() },
                    onSelectAll = { selectedIds = state.items.mapTo(mutableSetOf()) { it.id } },
                    onShare = { share(selected) },''',
    "selection select all wiring",
)
replace_once(
    '''    canRename: Boolean,
    onClear: () -> Unit,
    onShare: () -> Unit,''',
    '''    canRename: Boolean,
    onClear: () -> Unit,
    onSelectAll: () -> Unit,
    onShare: () -> Unit,''',
    "selection select all signature",
)
replace_once(
    '''        Text("$selectedCount seçili", modifier = Modifier.weight(1f))
        IconButton(onClick = onShare) { Icon(Icons.Default.Share, "Paylaş") }''',
    '''        Text("$selectedCount seçili", modifier = Modifier.weight(1f))
        IconButton(onClick = onSelectAll) { Icon(Icons.Default.SelectAll, "Tümünü seç") }
        IconButton(onClick = onShare) { Icon(Icons.Default.Share, "Paylaş") }''',
    "selection select all button",
)

replace_once(
    '''            if (selected.isNotEmpty()) {
                SelectionBar(''',
    '''            if (state.mode == CollectionMode.TRASH && selected.isEmpty() && state.items.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Geri Dönüşüm Kutusu", modifier = Modifier.weight(1f))
                    TextButton(onClick = { selectedIds = state.items.mapTo(mutableSetOf()) { it.id } }) {
                        Text("Tümünü seç")
                    }
                    TextButton(onClick = { permanentDelete(state.items) }) {
                        Text("Çöp kutusunu boşalt")
                    }
                }
            }

            if (selected.isNotEmpty()) {
                SelectionBar(''',
    "trash empty actions",
)

replace_once(
    '''                        onToggleSelection = { id ->
                            selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
                        },
                        onOpen = { index ->''',
    '''                        onToggleSelection = { id ->
                            selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
                        },
                        onDragSelection = { id -> selectedIds = selectedIds + id },
                        onOpen = { index ->''',
    "main drag selection wiring",
)
replace_once(
    '''                            onToggleSelection = { id ->
                                selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
                            },
                            onOpen = { index ->''',
    '''                            onToggleSelection = { id ->
                                selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
                            },
                            onDragSelection = { id -> selectedIds = selectedIds + id },
                            onOpen = { index ->''',
    "album drag selection wiring",
)

replace_once(
    '''private fun MediaCollection(
    state: GalleryUiState,
    gridColumns: Int,
    mediaFilter: MediaFilter,
    mediaSort: MediaSort,
    selectedIds: Set<Long>,
    onToggleSelection: (Long) -> Unit,
    onOpen: (Int) -> Unit,''',
    '''private fun MediaCollection(
    state: GalleryUiState,
    gridColumns: Int,
    mediaFilter: MediaFilter,
    mediaSort: MediaSort,
    selectedIds: Set<Long>,
    onToggleSelection: (Long) -> Unit,
    onDragSelection: (Long) -> Unit,
    onOpen: (Int) -> Unit,''',
    "MediaCollection drag signature",
)
replace_once(
    '''            selectedIds = selectedIds,
            onToggleSelection = onToggleSelection,
            onOpen = { visibleIndex ->''',
    '''            selectedIds = selectedIds,
            onToggleSelection = onToggleSelection,
            onDragSelection = onDragSelection,
            onOpen = { visibleIndex ->''',
    "MediaCollection drag argument",
)

replace_once(
    '''private fun MediaGrid(
    items: List<GalleryMedia>,
    loading: Boolean,
    hasMore: Boolean,
    columns: Int,
    selectedIds: Set<Long>,
    onToggleSelection: (Long) -> Unit,
    onOpen: (Int) -> Unit,
    onLoadMore: () -> Unit
) {
    val gridState = rememberLazyGridState()''',
    '''private fun MediaGrid(
    items: List<GalleryMedia>,
    loading: Boolean,
    hasMore: Boolean,
    columns: Int,
    selectedIds: Set<Long>,
    onToggleSelection: (Long) -> Unit,
    onDragSelection: (Long) -> Unit,
    onOpen: (Int) -> Unit,
    onLoadMore: () -> Unit
) {
    val gridState = rememberLazyGridState()
    var dragX by remember { mutableFloatStateOf(0f) }
    var dragY by remember { mutableFloatStateOf(0f) }

    fun selectAt(x: Float, y: Float) {
        val info = gridState.layoutInfo.visibleItemsInfo.firstOrNull { cell ->
            x >= cell.offset.x && x < cell.offset.x + cell.size.width &&
                y >= cell.offset.y && y < cell.offset.y + cell.size.height
        } ?: return
        items.getOrNull(info.index)?.let { onDragSelection(it.id) }
    }''',
    "MediaGrid drag signature",
)
replace_once(
    '''        state = gridState,
        modifier = Modifier.fillMaxSize(),''',
    '''        state = gridState,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(items.size, columns) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        dragX = offset.x; dragY = offset.y
                        selectAt(dragX, dragY)
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        dragX += amount.x; dragY += amount.y
                        selectAt(dragX, dragY)
                    }
                )
            },''',
    "MediaGrid drag modifier",
)

path.write_text(text, encoding="utf-8")
print("Complete feature wiring patch applied")
