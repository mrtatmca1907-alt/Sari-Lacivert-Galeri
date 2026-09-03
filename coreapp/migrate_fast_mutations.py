from pathlib import Path

path = Path("coreapp/src/main/kotlin/com/atmaca/gallery/GalleryApp.kt")
text = path.read_text(encoding="utf-8")


def replace_once(old: str, new: str, label: str):
    global text
    count = text.count(old)
    if count == 0:
        if new in text:
            print(f"{label}: already migrated")
            return
        raise SystemExit(f"{label}: source block not found")
    if count != 1:
        raise SystemExit(f"{label}: expected one source block, found {count}")
    text = text.replace(old, new, 1)
    print(f"{label}: migrated")

replace_once(
'''    var pendingWriteAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
''',
'''    var pendingWriteAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingMutationIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
''',
"pending mutation ids"
)

replace_once(
'''    val mutationConsentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            selectedIds = emptySet()
            viewerIndex = null
            refreshToken++
            albumsRefresh++
            duplicatesRefresh++
            vm.reload()
        }
    }
''',
'''    val mutationConsentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val affectedIds = pendingMutationIds
        pendingMutationIds = emptySet()
        if (result.resultCode == Activity.RESULT_OK) {
            selectedIds = emptySet()
            viewerIndex = null
            if (affectedIds.isNotEmpty()) vm.removeItemsByIds(affectedIds)
        }
    }
''',
"mutation consent"
)

replace_once(
'''    fun trash(items: List<GalleryMedia>, trashed: Boolean) {
        if (items.isEmpty()) return
        val request = actions.trashRequest(items, trashed)
        if (request != null) {
            mutationConsentLauncher.launch(request)
        } else {
            scope.launch {
                val count = if (trashed) actions.deleteLegacy(items) else 0
                message = if (trashed) "$count öğe silindi" else "Bu Android sürümünde geri alma desteklenmiyor"
                selectedIds = emptySet()
                viewerIndex = null
                vm.reload()
            }
        }
    }

    fun permanentDelete(items: List<GalleryMedia>) {
        if (items.isEmpty()) return
        val request = actions.deleteRequest(items)
        if (request != null) mutationConsentLauncher.launch(request)
        else scope.launch {
            val count = actions.deleteLegacy(items)
            message = "$count öğe kalıcı silindi"
            selectedIds = emptySet()
            vm.reload()
        }
    }
''',
'''    fun trash(items: List<GalleryMedia>, trashed: Boolean) {
        if (items.isEmpty()) return
        val affectedIds = items.mapTo(mutableSetOf()) { it.id }
        val request = actions.trashRequest(items, trashed)
        if (request != null) {
            pendingMutationIds = affectedIds
            mutationConsentLauncher.launch(request)
        } else {
            scope.launch {
                val count = if (trashed) actions.deleteLegacy(items) else 0
                message = if (trashed) "$count öğe silindi" else "Bu Android sürümünde geri alma desteklenmiyor"
                selectedIds = emptySet()
                viewerIndex = null
                if (trashed && count == items.size) vm.removeItemsByIds(affectedIds)
                else if (trashed && count > 0) vm.reload()
            }
        }
    }

    fun permanentDelete(items: List<GalleryMedia>) {
        if (items.isEmpty()) return
        val affectedIds = items.mapTo(mutableSetOf()) { it.id }
        val request = actions.deleteRequest(items)
        if (request != null) {
            pendingMutationIds = affectedIds
            mutationConsentLauncher.launch(request)
        } else scope.launch {
            val count = actions.deleteLegacy(items)
            message = "$count öğe kalıcı silindi"
            selectedIds = emptySet()
            viewerIndex = null
            if (count == items.size) vm.removeItemsByIds(affectedIds)
            else if (count > 0) vm.reload()
        }
    }
''',
"trash and delete"
)

replace_once(
'''            HomeSection.TRASH -> vm.openTrash()
            HomeSection.ALBUMS, HomeSection.DUPLICATES -> Unit
''',
'''            HomeSection.TRASH -> vm.openTrash()
            HomeSection.ALBUMS -> albumsRefresh++
            HomeSection.DUPLICATES -> duplicatesRefresh++
''',
"lazy secondary refresh"
)

replace_once(
'''    val albums by produceState<List<GalleryAlbum>>(
        initialValue = emptyList(), albumsRefresh, refreshToken
    ) {
        value = runCatching { repository.loadAlbums() }.getOrDefault(emptyList())
    }
''',
'''    val albums by produceState<List<GalleryAlbum>>(
        initialValue = emptyList(), albumsRefresh, section, pathAction
    ) {
        if (section == HomeSection.ALBUMS || pathAction != null) {
            value = runCatching { repository.loadAlbums() }.getOrDefault(emptyList())
        }
    }
''',
"lazy album scan"
)

path.write_text(text, encoding="utf-8")
print("Fast mutation source migration complete")
