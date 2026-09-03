from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def replace_once(path: Path, old: str, new: str):
    text = path.read_text(encoding="utf-8")
    if old not in text:
        raise SystemExit(f"pattern not found: {path}\n{old[:160]}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")

# 1) Prefer a real RELATIVE_PATH when we have one. This merges photo/video rows
# from separate MediaStore collections into the same visible album.
feature = ROOT / "coreapp/src/main/kotlin/com/atmaca/gallery/FeatureRules.kt"
replace_once(
    feature,
    '''fun albumLocator(relativePath:String?,bucketId:Long,bucketName:String?):AlbumLocator {\n    if(bucketId!=0L) return AlbumLocator.Bucket(bucketId)\n    val cleanPath=relativePath?.trim().orEmpty()\n    if(cleanPath.isNotEmpty()) return AlbumLocator.Path(normalizeRelativePath(cleanPath))\n    val cleanName=bucketName?.trim().orEmpty()\n    return if(cleanName.isNotEmpty()) AlbumLocator.Name(cleanName) else AlbumLocator.Unknown\n}''',
    '''fun albumLocator(relativePath:String?,bucketId:Long,bucketName:String?):AlbumLocator {\n    val cleanPath=relativePath?.trim().orEmpty()\n    if(cleanPath.isNotEmpty()) return AlbumLocator.Path(normalizeRelativePath(cleanPath))\n    if(bucketId!=0L) return AlbumLocator.Bucket(bucketId)\n    val cleanName=bucketName?.trim().orEmpty()\n    return if(cleanName.isNotEmpty()) AlbumLocator.Name(cleanName) else AlbumLocator.Unknown\n}'''
)

# 2) Tool dialogs: image-side tools use the gallery's own album picker.
settings = ROOT / "coreapp/src/main/kotlin/com/atmaca/gallery/CompleteSettingsExtras.kt"
replace_once(
    settings,
    '''    val engine = remember { CompleteToolEngine(context) }\n    val scope = rememberCoroutineScope()''',
    '''    val engine = remember { CompleteToolEngine(context) }\n    val repository = remember { MediaStoreRepository(context) }\n    val scope = rememberCoroutineScope()'''
)
replace_once(
    settings,
    '''    var maxFaces by remember(tool) { mutableIntStateOf(12) }''',
    '''    var maxFaces by remember(tool) { mutableIntStateOf(12) }\n    var showInternalAlbumPicker by remember(tool) { mutableStateOf(false) }'''
)
replace_once(
    settings,
    '''                    OutlinedButton(onClick = { folderPicker.launch(null) }, enabled = !running && !scanning, modifier = Modifier.fillMaxWidth()) {\n                        Text("Klasör seç ve alt klasörleri tara")\n                    }''',
    '''                    OutlinedButton(\n                        onClick = {\n                            if (toolUsesInternalAlbumPicker(tool)) showInternalAlbumPicker = true\n                            else folderPicker.launch(null)\n                        },\n                        enabled = !running && !scanning,\n                        modifier = Modifier.fillMaxWidth()\n                    ) {\n                        Text(if (toolUsesInternalAlbumPicker(tool)) "Galeriden klasör seç" else "Klasör seç ve alt klasörleri tara")\n                    }'''
)
replace_once(
    settings,
    '''    Dialog(\n        onDismissRequest = { if (!running && !scanning) onDismiss() },''',
    '''    if (showInternalAlbumPicker) {\n        InternalToolAlbumPicker(\n            tool = tool,\n            repository = repository,\n            onDismiss = { showInternalAlbumPicker = false },\n            onSelected = { uris ->\n                selectedUris = uris.distinct()\n                done = 0\n                total = selectedUris.size\n                showInternalAlbumPicker = false\n                Toast.makeText(context, "Klasörden ${selectedUris.size} uygun dosya seçildi", Toast.LENGTH_SHORT).show()\n            }\n        )\n    }\n\n    Dialog(\n        onDismissRequest = { if (!running && !scanning && !showInternalAlbumPicker) onDismiss() },'''
)

# 3) Main album screen uses the OEM-safe image+video collection scan.
app = ROOT / "coreapp/src/main/kotlin/com/atmaca/gallery/GalleryApp.kt"
replace_once(app, 'repository.loadAlbums()', 'repository.loadAlbumsOemSafe()')

# 4) Drag-selection fills every crossed cell, including cells skipped by a fast pointer event.
replace_once(
    app,
    '''    var dragX by remember { mutableFloatStateOf(0f) }\n    var dragY by remember { mutableFloatStateOf(0f) }\n\n    fun selectAt(x: Float, y: Float) {\n        val info = gridState.layoutInfo.visibleItemsInfo.firstOrNull { cell ->\n            x >= cell.offset.x && x < cell.offset.x + cell.size.width &&\n                y >= cell.offset.y && y < cell.offset.y + cell.size.height\n        } ?: return\n        items.getOrNull(info.index)?.let { onDragSelection(it.id) }\n    }''',
    '''    var dragX by remember { mutableFloatStateOf(0f) }\n    var dragY by remember { mutableFloatStateOf(0f) }\n    var lastDragIndex by remember { mutableIntStateOf(-1) }\n\n    fun selectAt(x: Float, y: Float) {\n        val info = gridState.layoutInfo.visibleItemsInfo.firstOrNull { cell ->\n            x >= cell.offset.x && x < cell.offset.x + cell.size.width &&\n                y >= cell.offset.y && y < cell.offset.y + cell.size.height\n        } ?: return\n        val currentIndex = info.index\n        val indexes = if (lastDragIndex >= 0) dragSelectionIndexes(lastDragIndex, currentIndex) else listOf(currentIndex)\n        indexes.forEach { index -> items.getOrNull(index)?.let { onDragSelection(it.id) } }\n        lastDragIndex = currentIndex\n    }'''
)
replace_once(
    app,
    '''                    onDragStart = { offset ->\n                        dragX = offset.x; dragY = offset.y\n                        selectAt(dragX, dragY)\n                    },\n                    onDrag = { change, amount ->\n                        change.consume()\n                        dragX += amount.x; dragY += amount.y\n                        selectAt(dragX, dragY)\n                    }\n                )''',
    '''                    onDragStart = { offset ->\n                        lastDragIndex = -1\n                        dragX = offset.x; dragY = offset.y\n                        selectAt(dragX, dragY)\n                    },\n                    onDrag = { change, amount ->\n                        change.consume()\n                        dragX += amount.x; dragY += amount.y\n                        selectAt(dragX, dragY)\n                    },\n                    onDragEnd = { lastDragIndex = -1 },\n                    onDragCancel = { lastDragIndex = -1 }\n                )'''
)

print("OEM gallery migration applied")
