package com.atmaca.gallery

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import android.view.View
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

enum class HomeSection { MEDIA, ALBUMS, SETTINGS, PHOTOS, VIDEOS, DUPLICATES, TRASH }
private enum class PathAction { COPY, MOVE }

@Composable
fun AtmacaGalleryApp(vm: GalleryViewModel = viewModel()) {
    val context = LocalContext.current
    val required = remember { requiredMediaPermissions(Build.VERSION.SDK_INT) }
    var permissionTick by remember { mutableIntStateOf(0) }
    val granted = remember(permissionTick) {
        required.all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionTick++ }

    if (!granted) {
        PermissionScreen { launcher.launch(required.toTypedArray()) }
        return
    }

    LaunchedEffect(permissionTick) { vm.start() }
    GalleryHome(vm)
}

@Composable
private fun PermissionScreen(onRequest: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(28.dp)
        ) {
            Text("ATMACA Galeri", style = MaterialTheme.typography.headlineMedium)
            Text("Fotoğraf ve videoları gösterebilmek için medya izni gerekiyor.")
            Button(onClick = onRequest) { Text("Medya izni ver") }
        }
    }
}

@Composable
private fun GalleryHome(vm: GalleryViewModel) {
    val context = LocalContext.current
    val activity = context as? Activity
    val state by vm.state.collectAsState()
    val repository = remember { MediaStoreRepository(context) }
    val actions = remember { GalleryActions(context) }
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("atmaca_gallery", Context.MODE_PRIVATE) }

    var section by rememberSaveable { mutableStateOf(HomeSection.MEDIA) }
    var viewerIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var refreshToken by remember { mutableIntStateOf(0) }
    var albumsRefresh by remember { mutableIntStateOf(0) }
    var duplicatesRefresh by remember { mutableIntStateOf(0) }
    var gridColumns by rememberSaveable { mutableIntStateOf(prefs.getInt("grid_columns", 4).coerceIn(3, 6)) }
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
                    prefs.getString("media_sort", MediaSort.TAKEN.name) ?: MediaSort.TAKEN.name
                )
            }.getOrDefault(MediaSort.TAKEN)
        )
    }
    var sortDirection by rememberSaveable {
        mutableStateOf(
            runCatching {
                SortDirection.valueOf(
                    prefs.getString("sort_direction", SortDirection.DESCENDING.name)
                        ?: SortDirection.DESCENDING.name
                )
            }.getOrDefault(SortDirection.DESCENDING)
        )
    }
    var showSettings by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }
    var renameItem by remember { mutableStateOf<GalleryMedia?>(null) }
    var pathAction by remember { mutableStateOf<PathAction?>(null) }
    var pathItems by remember { mutableStateOf<List<GalleryMedia>>(emptyList()) }
    var pendingWriteAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingMutationIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var cropItem by remember { mutableStateOf<GalleryMedia?>(null) }
    var albums by remember { mutableStateOf<List<GalleryAlbum>>(emptyList()) }
    var albumsLoading by remember { mutableStateOf(false) }

    val selected = remember(selectedIds, state.items) {
        state.items.filter { it.id in selectedIds }
    }

    val writeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val action = pendingWriteAction
        pendingWriteAction = null
        if (result.resultCode == Activity.RESULT_OK) action?.invoke()
    }

    val mutationConsentLauncher = rememberLauncherForActivityResult(
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

    val cameraLauncher = rememberLauncherForActivityResult(
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

    fun runAfterWriteAccess(items: List<GalleryMedia>, action: () -> Unit) {
        val request = actions.writeRequest(items)
        if (request == null) action() else {
            pendingWriteAction = action
            writeLauncher.launch(request)
        }
    }

    fun share(items: List<GalleryMedia>) {
        val intent = actions.shareIntent(items) ?: return
        runCatching { context.startActivity(Intent.createChooser(intent, "Paylaş")) }
            .onFailure { message = "Paylaşma ekranı açılamadı" }
    }

    fun trash(items: List<GalleryMedia>, trashed: Boolean) {
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

    fun openCamera() {
        val uri = actions.prepareCameraImage()
        if (uri == null) {
            message = "Kamera için kayıt dosyası oluşturulamadı"
            return
        }
        pendingCameraUri = uri
        runCatching { cameraLauncher.launch(actions.cameraIntent(uri)) }
            .onFailure {
                actions.finishCameraImage(uri, false)
                pendingCameraUri = null
                message = "Kamera açılamadı"
            }
    }

    LaunchedEffect(section) {
        selectedIds = emptySet()
        viewerIndex = null
        when (section) {
            HomeSection.MEDIA -> vm.openMedia()
            HomeSection.SETTINGS -> showSettings = true
            HomeSection.PHOTOS -> vm.switchTab(GalleryTab.PHOTOS)
            HomeSection.VIDEOS -> vm.switchTab(GalleryTab.VIDEOS)
            HomeSection.TRASH -> vm.openTrash()
            HomeSection.ALBUMS -> albumsRefresh++
            HomeSection.DUPLICATES -> duplicatesRefresh++
        }
    }

    LaunchedEffect(refreshToken) {
        if (refreshToken > 0 && section in listOf(HomeSection.MEDIA, HomeSection.PHOTOS, HomeSection.VIDEOS, HomeSection.TRASH)) {
            vm.reload()
        }
    }

    cropItem?.let { item ->
        CropEditor(
            item = item,
            actions = actions,
            onCancel = { cropItem = null },
            onSaved = { cropItem = null; refreshToken++; albumsRefresh++; vm.reload() },
            onMessage = { message = it }
        )
        return
    }

    viewerIndex?.let { index ->
        if (state.items.isNotEmpty()) {
            MediaViewer(
                items = state.items,
                initialIndex = index.coerceIn(0, state.items.lastIndex),
                onBack = { viewerIndex = null },
                onNeedMore = vm::loadNextPage,
                onShare = { share(listOf(it)) },
                onTrash = { item ->
                    if (state.mode == CollectionMode.TRASH) trash(listOf(item), false)
                    else trash(listOf(item), true)
                },
                onRename = { renameItem = it },
                onCrop = { item -> runAfterWriteAccess(listOf(item)) { cropItem = item } }
            )
            renameItem?.let { item ->
                RenameDialog(
                    item = item,
                    onDismiss = { renameItem = null },
                    onConfirm = { name ->
                        renameItem = null
                        runAfterWriteAccess(listOf(item)) {
                            scope.launch {
                                val ok = actions.rename(item, name)
                                message = if (ok) "Ad değiştirildi" else "Ad değiştirilemedi"
                                vm.reload()
                            }
                        }
                    }
                )
            }
            return
        }
    }

    LaunchedEffect(albumsRefresh, section, pathAction) {
        if (section == HomeSection.ALBUMS || pathAction != null) {
            albumsLoading = true
            val fresh = runCatching { repository.loadAlbumsOemSafe() }.getOrDefault(emptyList())
            albums = albumListWhileRefreshing(albums, fresh, refreshing = false)
            albumsLoading = false
        }
    }

    if (pathAction != null) {
        PathDialog(
            action = pathAction!!,
            albums = albums,
            onDismiss = {
                pathAction = null
                pathItems = emptyList()
            },
            onConfirm = { target ->
                val action = pathAction
                val items = pathItems
                pathAction = null
                pathItems = emptyList()
                if (action == PathAction.COPY) {
                    scope.launch {
                        val count = actions.copy(items, target)
                        message = "$count öğe kopyalandı"
                        selectedIds = emptySet()
                        refreshToken++
                        albumsRefresh++
                        vm.reload()
                    }
                } else {
                    runAfterWriteAccess(items) {
                        scope.launch {
                            val count = actions.move(items, target)
                            message = "$count öğe taşındı"
                            selectedIds = emptySet()
                            refreshToken++
                            albumsRefresh++
                            vm.reload()
                        }
                    }
                }
            }
        )
    }

    renameItem?.let { item ->
        RenameDialog(
            item = item,
            onDismiss = { renameItem = null },
            onConfirm = { name ->
                renameItem = null
                runAfterWriteAccess(listOf(item)) {
                    scope.launch {
                        val ok = actions.rename(item, name)
                        message = if (ok) "Ad değiştirildi" else "Ad değiştirilemedi"
                        vm.reload()
                    }
                }
            }
        )
    }

    if (showSettings) {
        ModernSettingsDialog(
            gridColumns = gridColumns,
            mediaFilter = mediaFilter,
            mediaSort = mediaSort,
            sortDirection = sortDirection,
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
            },
            onSortDirection = { direction ->
                sortDirection = direction
                selectedIds = emptySet()
                prefs.edit().putString("sort_direction", direction.name).apply()
            },
            onOpenTrash = {
                showSettings = false
                section = HomeSection.TRASH
            },
            onOpenDuplicates = {
                showSettings = false
                section = HomeSection.DUPLICATES
            }
        )
    }

    BackHandler(enabled = selectedIds.isNotEmpty() && state.mode != CollectionMode.ALBUM) {
        selectedIds = emptySet()
    }

    Scaffold(
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
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            GalleryTopBar(
                title = when {
                    state.mode == CollectionMode.ALBUM && section == HomeSection.ALBUMS ->
                        albumDisplayName(state.albumPath.orEmpty())
                    section == HomeSection.MEDIA -> "Medya"
                    section == HomeSection.SETTINGS -> "Ayarlar"
                    section == HomeSection.PHOTOS -> "Fotoğraflar"
                    section == HomeSection.VIDEOS -> "Videolar"
                    section == HomeSection.ALBUMS -> "Albümler"
                    section == HomeSection.DUPLICATES -> "Yinelenenler"
                    else -> "Çöp Kutusu"
                },
                showBack = state.mode == CollectionMode.ALBUM && section == HomeSection.ALBUMS,
                onBack = {
                    selectedIds = emptySet()
                    vm.switchTab(GalleryTab.PHOTOS)
                },
                onCamera = ::openCamera,
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
            )

            if (state.mode == CollectionMode.TRASH && selected.isEmpty() && state.items.isNotEmpty()) {
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
                SelectionBar(
                    selectedCount = selected.size,
                    trashMode = state.mode == CollectionMode.TRASH,
                    canRename = selected.size == 1 && state.mode != CollectionMode.TRASH,
                    onClear = { selectedIds = emptySet() },
                    onSelectAll = { selectedIds = state.items.mapTo(mutableSetOf()) { it.id } },
                    onShare = { share(selected) },
                    onCopy = {
                        pathItems = selected
                        pathAction = PathAction.COPY
                    },
                    onMove = {
                        pathItems = selected
                        pathAction = PathAction.MOVE
                    },
                    onRename = { renameItem = selected.singleOrNull() },
                    onTrashOrRestore = {
                        if (state.mode == CollectionMode.TRASH) trash(selected, false)
                        else trash(selected, true)
                    },
                    onDeleteForever = { permanentDelete(selected) }
                )
            }

            message?.let {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(it, modifier = Modifier.weight(1f))
                        TextButton(onClick = { message = null }) { Text("Kapat") }
                    }
                }
            }

            when (section) {
                HomeSection.MEDIA, HomeSection.SETTINGS, HomeSection.PHOTOS, HomeSection.VIDEOS, HomeSection.TRASH -> {
                    MediaCollection(
                        state = state,
                        gridColumns = gridColumns,
                        mediaFilter = mediaFilter,
                        mediaSort = mediaSort,
                        sortDirection = sortDirection,
                        selectedIds = selectedIds,
                        onToggleSelection = { id ->
                            selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
                        },
                        onDragSelection = { id -> selectedIds = selectedIds + id },
                        onOpen = { index ->
                            if (selectedIds.isEmpty()) viewerIndex = index
                            else {
                                val id = state.items[index].id
                                selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
                            }
                        },
                        onLoadMore = vm::loadNextPage,
                        onRetry = vm::reload
                    )
                }

                HomeSection.ALBUMS -> {
                    if (state.mode == CollectionMode.ALBUM) {
                        BackHandler {
                            when (galleryBackAction(selectedIds.size, inAlbum = true)) {
                                GalleryBackAction.CLEAR_SELECTION -> selectedIds = emptySet()
                                GalleryBackAction.CLOSE_ALBUM -> vm.switchTab(GalleryTab.PHOTOS)
                                GalleryBackAction.EXIT -> Unit
                            }
                        }
                        MediaCollection(
                            state = state,
                            gridColumns = gridColumns,
                            mediaFilter = mediaFilter,
                            mediaSort = mediaSort,
                            sortDirection = sortDirection,
                            selectedIds = selectedIds,
                            onToggleSelection = { id ->
                                selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
                            },
                            onDragSelection = { id -> selectedIds = selectedIds + id },
                            onOpen = { index ->
                                if (selectedIds.isEmpty()) viewerIndex = index
                                else {
                                    val id = state.items[index].id
                                    selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
                                }
                            },
                            onLoadMore = vm::loadNextPage,
                            onRetry = vm::reload
                        )
                    } else {
                        AlbumGrid(
                            albums = if (albums.isNotEmpty()) albums else quickAlbums(state.items),
                            onOpen = { album -> vm.openAlbum(album) }
                        )
                    }
                }

                HomeSection.DUPLICATES -> DuplicateScreen(
                    repository = repository,
                    actions = actions,
                    refreshToken = duplicatesRefresh,
                    onShare = ::share,
                    onTrash = { items -> trash(items, true) },
                    onMessage = { message = it }
                )
            }
        }
    }
}

@Composable
private fun GalleryTopBar(
    title: String,
    showBack: Boolean,
    onBack: () -> Unit,
    onCamera: () -> Unit,
    onRefresh: () -> Unit,
    onSettings: () -> Unit,
    showMore: Boolean,
    onMoreChange: (Boolean) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showBack) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Geri") }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                visibleBuildBadge(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1
            )
        }
        IconButton(onClick = onCamera) { Icon(Icons.Default.PhotoCamera, "Kamera") }
        IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, "Yenile") }
        Box {
            IconButton(onClick = { onMoreChange(true) }) { Icon(Icons.Default.MoreVert, "Menü") }
            DropdownMenu(expanded = showMore, onDismissRequest = { onMoreChange(false) }) {
                DropdownMenuItem(
                    text = { Text("Ayarlar") },
                    leadingIcon = { Icon(Icons.Default.Settings, null) },
                    onClick = {
                        onMoreChange(false)
                        onSettings()
                    }
                )
            }
        }
    }
}

@Composable
private fun SelectionBar(
    selectedCount: Int,
    trashMode: Boolean,
    canRename: Boolean,
    onClear: () -> Unit,
    onSelectAll: () -> Unit,
    onShare: () -> Unit,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onRename: () -> Unit,
    onTrashOrRestore: () -> Unit,
    onDeleteForever: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("$selectedCount seçili", maxLines = 1, softWrap = false, modifier = Modifier.width(94.dp))
        IconButton(onClick = onSelectAll, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.SelectAll, "Tümünü seç") }
        IconButton(onClick = onShare, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.Share, "Paylaş") }
        if (!trashMode) {
            IconButton(onClick = onCopy, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.ContentCopy, "Kopyala") }
            IconButton(onClick = onMove, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.DriveFileMove, "Taşı") }
            if (canRename) IconButton(onClick = onRename, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.Edit, "Ad değiştir") }
        }
        IconButton(onClick = onTrashOrRestore, modifier = Modifier.size(40.dp)) {
            Icon(if (trashMode) Icons.Default.Restore else Icons.Default.Delete, if (trashMode) "Geri al" else "Çöpe taşı")
        }
        if (trashMode) {
            IconButton(onClick = onDeleteForever, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.Delete, "Kalıcı sil") }
        }
        TextButton(onClick = onClear) { Text("İptal") }
    }
}

@Composable
private fun MediaCollection(
    state: GalleryUiState,
    gridColumns: Int,
    mediaFilter: MediaFilter,
    mediaSort: MediaSort,
    sortDirection: SortDirection,
    selectedIds: Set<Long>,
    onToggleSelection: (Long) -> Unit,
    onDragSelection: (Long) -> Unit,
    onOpen: (Int) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit
) {
    val visibleItems = remember(state.items, mediaFilter, mediaSort, sortDirection) {
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
    }

    when {
        state.items.isEmpty() && state.loading -> Box(
            Modifier.fillMaxSize(), contentAlignment = Alignment.Center
        ) { CircularProgressIndicator() }

        state.items.isEmpty() && state.error != null -> Box(
            Modifier.fillMaxSize(), contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(state.error ?: "Medya okunamadı")
                Spacer(Modifier.height(12.dp))
                Button(onClick = onRetry) { Text("Tekrar dene") }
            }
        }

        state.items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
            onDragSelection = onDragSelection,
            onOpen = { visibleIndex ->
                val target = visibleItems.getOrNull(visibleIndex)
                val originalIndex = target?.let { item ->
                    state.items.indexOfFirst { it.id == item.id && it.isVideo == item.isVideo }
                } ?: -1
                if (originalIndex >= 0) onOpen(originalIndex)
            },
            onLoadMore = onLoadMore
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaGrid(
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
    val gridScope = rememberCoroutineScope()
    val density = LocalDensity.current
    var scrollbarHeightPx by remember { mutableIntStateOf(0) }
    var dragX by remember { mutableFloatStateOf(0f) }
    var dragY by remember { mutableFloatStateOf(0f) }
    var lastDragIndex by remember { mutableIntStateOf(-1) }

    fun selectAt(x: Float, y: Float) {
        val info = gridState.layoutInfo.visibleItemsInfo.firstOrNull { cell ->
            x >= cell.offset.x && x < cell.offset.x + cell.size.width &&
                y >= cell.offset.y && y < cell.offset.y + cell.size.height
        } ?: return
        val currentIndex = info.index
        val indexes = if (lastDragIndex >= 0) dragSelectionIndexes(lastDragIndex, currentIndex) else listOf(currentIndex)
        indexes.forEach { index -> items.getOrNull(index)?.let { onDragSelection(it.id) } }
        lastDragIndex = currentIndex
    }

    LaunchedEffect(gridState, items.size, hasMore) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .map { last -> items.size - last }
            .distinctUntilChanged()
            .collect { remaining ->
                if (hasMore && items.isNotEmpty() && remaining <= columns * 4) onLoadMore()
            }
    }

    Box(Modifier.fillMaxSize()) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        state = gridState,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(items.size, columns) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        lastDragIndex = -1
                        dragX = offset.x; dragY = offset.y
                        selectAt(dragX, dragY)
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        dragX += amount.x; dragY += amount.y
                        selectAt(dragX, dragY)
                    },
                    onDragEnd = { lastDragIndex = -1 },
                    onDragCancel = { lastDragIndex = -1 }
                )
            },
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        itemsIndexed(
            items = items,
            key = { _, item -> "${item.isVideo}-${item.id}" }
        ) { index, item ->
            MediaTile(
                item = item,
                selected = item.id in selectedIds,
                modifier = Modifier
                    .aspectRatio(1f)
                    .clickable { onOpen(index) }
            )
        }
        if (loading) {
            item(key = "loading") {
                Box(Modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(28.dp))
                }
            }
        }
    }
        if (items.size > gridState.layoutInfo.visibleItemsInfo.size) {
            val thumbHeightPx = with(density) { 64.dp.toPx() }
            val travelPx = (scrollbarHeightPx - thumbHeightPx).coerceAtLeast(1f)
            val maxFirst = (items.lastIndex - gridState.layoutInfo.visibleItemsInfo.size + 1).coerceAtLeast(1)
            val fraction = (gridState.firstVisibleItemIndex.toFloat() / maxFirst.toFloat()).coerceIn(0f, 1f)
            val thumbOffsetPx = travelPx * fraction

            fun jumpScrollbar(pointerY: Float) {
                val targetFraction = ((pointerY - thumbHeightPx / 2f) / travelPx).coerceIn(0f, 1f)
                val targetIndex = (targetFraction * maxFirst).roundToInt().coerceIn(0, items.lastIndex)
                gridScope.launch { gridState.scrollToItem(targetIndex) }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(18.dp)
                    .onSizeChanged { scrollbarHeightPx = it.height }
                    .pointerInput(items.size, scrollbarHeightPx) {
                        detectDragGestures(
                            onDragStart = { jumpScrollbar(it.y) },
                            onDrag = { change, _ ->
                                change.consume()
                                jumpScrollbar(change.position.y)
                            }
                        )
                    }
            ) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset { IntOffset(0, thumbOffsetPx.roundToInt()) }
                        .padding(end = 2.dp)
                        .width(7.dp)
                        .height(64.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color.DarkGray.copy(alpha = 0.78f))
                )
            }
        }
    }
}

@Composable
private fun MediaTile(
    item: GalleryMedia,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val clickModifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier
    Box(
        clickModifier
            .clip(RoundedCornerShape(3.dp))
            .background(Color(0xFF17233A))
    ) {
        MediaThumbnail(item)?.let { bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        if (item.isVideo) {
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = "Video",
                tint = Color.White,
                modifier = Modifier.align(Alignment.Center).size(34.dp)
            )
            if (item.durationMs > 0) {
                Text(
                    formatDuration(item.durationMs),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
        }
        Text(
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
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.30f))
            )
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.TopEnd).padding(5.dp).size(24.dp)
            ) {
                Box(contentAlignment = Alignment.Center) { Text("✓", color = Color.Black) }
            }
        }
    }
}

private object ThumbnailCache : LruCache<String, Bitmap>(64 * 1024) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
}

@Composable
private fun MediaThumbnail(item: GalleryMedia): Bitmap? {
    val context = LocalContext.current
    val key = "${item.uri}:360"
    val bitmap by produceState<Bitmap?>(initialValue = ThumbnailCache.get(key), key) {
        if (value == null) {
            value = withContext(Dispatchers.IO) {
                loadThumbnailCompat(context, item, 360)?.also { ThumbnailCache.put(key, it) }
            }
        }
    }
    return bitmap
}

@Suppress("DEPRECATION")
private fun loadThumbnailCompat(context: Context, item: GalleryMedia, edge: Int): Bitmap? =
    runCatching {
        if (Build.VERSION.SDK_INT >= 29) {
            context.contentResolver.loadThumbnail(item.uri, Size(edge, edge), null)
        } else if (item.isVideo) {
            MediaStore.Video.Thumbnails.getThumbnail(
                context.contentResolver,
                item.id,
                MediaStore.Video.Thumbnails.MINI_KIND,
                null
            )
        } else {
            MediaStore.Images.Thumbnails.getThumbnail(
                context.contentResolver,
                item.id,
                MediaStore.Images.Thumbnails.MINI_KIND,
                null
            )
        }
    }.getOrNull()

@Composable
private fun AlbumGrid(albums: List<GalleryAlbum>, onOpen: (GalleryAlbum) -> Unit) {
    if (albums.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Albüm bulunamadı") }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(albums, key = { _, album -> albumGridKey(album) }) { _, album ->
            Column(
                Modifier
                    .padding(4.dp)
                    .clickable { onOpen(album) }
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.35f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    album.cover?.let { cover ->
                        MediaThumbnail(cover)?.let { bitmap ->
                            Image(
                                bitmap.asImageBitmap(),
                                contentDescription = album.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    } ?: Icon(
                        Icons.Default.Folder,
                        contentDescription = null,
                        modifier = Modifier.align(Alignment.Center).size(52.dp)
                    )
                }
                Text(album.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${album.count} öğe", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun DuplicateScreen(
    repository: MediaStoreRepository,
    actions: GalleryActions,
    refreshToken: Int,
    onShare: (List<GalleryMedia>) -> Unit,
    onTrash: (List<GalleryMedia>) -> Unit,
    onMessage: (String) -> Unit
) {
    var groups by remember(refreshToken) { mutableStateOf<List<List<GalleryMedia>>>(emptyList()) }
    var scanning by remember(refreshToken) { mutableStateOf(true) }
    var scanned by remember(refreshToken) { mutableIntStateOf(0) }
    var selectedIds by remember(refreshToken) { mutableStateOf(setOf<Long>()) }

    LaunchedEffect(refreshToken) {
        scanning = true
        groups = runCatching {
            repository.findExactDuplicates { scanned = it }
        }.getOrElse {
            onMessage("Yinelenen taraması tamamlanamadı: ${it.message ?: "hata"}")
            emptyList()
        }
        scanning = false
    }

    val selected = remember(groups, selectedIds) {
        groups.flatten().filter { it.id in selectedIds }
    }

    Column(Modifier.fillMaxSize()) {
        if (scanning) {
            Row(
                Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(Modifier.size(26.dp))
                Spacer(Modifier.width(10.dp))
                Text("Yinelenenler doğrulanıyor… $scanned dosya")
            }
        }

        if (!scanning && groups.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Birebir aynı dosya bulunamadı")
            }
            return@Column
        }

        if (groups.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("${groups.size} grup", modifier = Modifier.weight(1f))
                TextButton(onClick = {
                    selectedIds = groups.flatMap { group -> group.drop(1).map { it.id } }.toSet()
                }) {
                    Icon(Icons.Default.SelectAll, null)
                    Spacer(Modifier.width(4.dp))
                    Text("Fazlaları seç")
                }
                if (selected.isNotEmpty()) {
                    IconButton(onClick = { onShare(selected) }) { Icon(Icons.Default.Share, "Paylaş") }
                    IconButton(onClick = { onTrash(selected) }) { Icon(Icons.Default.Delete, "Çöpe taşı") }
                }
            }
        }

        LazyColumn(Modifier.fillMaxSize()) {
            items(groups, key = { group -> group.joinToString(":") { it.id.toString() } }) { group ->
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Text(
                        "${group.size} aynı dosya • ${formatBytes(group.firstOrNull()?.size ?: 0L)}",
                        modifier = Modifier.padding(horizontal = 10.dp)
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 6.dp)
                    ) {
                        items(group, key = { "${it.isVideo}-${it.id}" }) { item ->
                            MediaTile(
                                item = item,
                                selected = item.id in selectedIds,
                                modifier = Modifier.size(112.dp),
                                onClick = {
                                    selectedIds = if (item.id in selectedIds) selectedIds - item.id
                                    else selectedIds + item.id
                                }
                            )
                        }
                    }
                    Text(
                        group.joinToString("  •  ") { albumDisplayName(it.relativePath) },
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaViewer(
    items: List<GalleryMedia>,
    initialIndex: Int,
    onBack: () -> Unit,
    onNeedMore: () -> Unit,
    onShare: (GalleryMedia) -> Unit,
    onTrash: (GalleryMedia) -> Unit,
    onRename: (GalleryMedia) -> Unit,
    onCrop: (GalleryMedia) -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val prefs = remember { context.getSharedPreferences("gallery", Context.MODE_PRIVATE) }
    val screenshotActions = remember { GalleryActions(context) }
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState(initialPage = initialIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))) { items.size }
    val rotations = remember { mutableStateMapOf<Long, Float>() }
    val zooms = remember { mutableStateMapOf<Long, Float>() }
    var gestureActive by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
    var optionsExpanded by remember { mutableStateOf(false) }
    var screenshotMode by remember { mutableStateOf(false) }
    var screenshotSourceItem by remember { mutableStateOf<GalleryMedia?>(null) }
    var screenshotFolderUri by remember { mutableStateOf(prefs.getString("screenshot_tree_uri", null)) }
    var captureInProgress by remember { mutableStateOf(false) }
    var screenshotOffsetX by remember { mutableFloatStateOf(0f) }
    var screenshotOffsetY by remember { mutableFloatStateOf(0f) }
    var showInfo by remember { mutableStateOf(false) }
    var slideshowRunning by remember { mutableStateOf(false) }
    var favoriteIds by remember {
        mutableStateOf(prefs.getStringSet("favorite_ids", emptySet())?.toSet() ?: emptySet())
    }

    val slideshowSeconds = clampSlideshowSeconds(prefs.getInt("slideshow_seconds", 4))
    val slideshowLoop = prefs.getBoolean("slideshow_loop", true)
    val slideshowRandom = prefs.getBoolean("slideshow_random", false)

    val screenshotSourcePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val displayName = runCatching {
                context.contentResolver.query(
                    uri,
                    arrayOf(MediaStore.MediaColumns.DISPLAY_NAME),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0).orEmpty() else ""
                }.orEmpty()
            }.getOrDefault("").ifBlank { "Screenshot kaynağı" }
            screenshotSourceItem = GalleryMedia(
                id = Long.MIN_VALUE,
                uri = uri,
                name = displayName,
                mimeType = context.contentResolver.getType(uri),
                isVideo = false,
                dateAdded = 0L,
                dateModified = 0L,
                dateTaken = 0L,
                width = 0,
                height = 0,
                bucketId = 0L,
                bucketName = null,
                relativePath = "",
                size = 0L,
                durationMs = 0L,
                isTrashed = false
            )
            screenshotMode = true
            Toast.makeText(context, "Screenshot için fotoğraf seçildi", Toast.LENGTH_SHORT).show()
        }
    }

    val screenshotFolderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
            screenshotFolderUri = uri.toString()
            prefs.edit().putString("screenshot_tree_uri", uri.toString()).apply()
            Toast.makeText(context, "Screenshot klasörü seçildi", Toast.LENGTH_SHORT).show()
        }
    }

    DisposableEffect(activity) {
        val decor = activity?.window?.decorView
        val oldFlags = decor?.systemUiVisibility ?: 0
        if (decor != null) {
            @Suppress("DEPRECATION")
            decor.systemUiVisibility =
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        }
        onDispose {
            if (decor != null) {
                @Suppress("DEPRECATION")
                decor.systemUiVisibility = oldFlags
            }
        }
    }

    DisposableEffect(slideshowRunning, activity) {
        val window = activity?.window
        if (slideshowRunning) {
            window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            if (slideshowRunning) {
                window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    LaunchedEffect(pager, items.size) {
        snapshotFlow { pager.currentPage }
            .distinctUntilChanged()
            .collect { page -> if (items.size - page <= 4) onNeedMore() }
    }

    LaunchedEffect(slideshowRunning, pager.currentPage, items.size, slideshowSeconds, slideshowLoop, slideshowRandom) {
        if (!slideshowRunning || items.size <= 1) return@LaunchedEffect
        val current = pager.currentPage
        val controller = SlideshowController(items.size, slideshowLoop)
        if (!controller.canAdvance(current)) {
            slideshowRunning = false
            return@LaunchedEffect
        }

        val next = if (slideshowRandom && items.size > 1) {
            var candidate = current
            while (candidate == current) candidate = kotlin.random.Random.nextInt(items.size)
            candidate
        } else controller.nextIndex(current)

        val viewerWidth = (activity?.window?.decorView?.width ?: 1080).coerceAtLeast(1)
        val viewerHeight = (activity?.window?.decorView?.height ?: 1920).coerceAtLeast(1)
        val pagesToPrepare = if (slideshowRandom) listOf(next)
            else slideshowPrefetchIndices(current, items.size, slideshowLoop, ahead = 2)
        pagesToPrepare.forEach { page ->
            items.getOrNull(page)?.let { media ->
                if (!media.isVideo) prefetchViewerBitmap(context, media, viewerWidth, viewerHeight)
            }
        }

        delay(slideshowSeconds * 1000L)
        if (!slideshowRunning) return@LaunchedEffect
        pager.animateScrollToPage(next)
    }

    BackHandler(onBack = onBack)

    val currentItem = items.getOrNull(pager.currentPage)
    val currentScale = currentItem?.let { zooms[it.id] } ?: 1f
    val currentRotation = currentItem?.let { rotations[it.id] } ?: 0f
    val renderChrome = shouldRenderViewerChrome(
        captureInProgress = captureInProgress,
        controlsVisible = controlsVisible,
        scale = currentScale,
        gestureActive = gestureActive
    )

    val backgroundBitmap by produceState<Bitmap?>(initialValue = null, currentItem?.uri) {
        val current = currentItem
        value = if (current != null && !current.isVideo && Build.VERSION.SDK_INT >= 29) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    context.contentResolver.loadThumbnail(
                        current.uri,
                        android.util.Size(420, 720),
                        null
                    )
                }.getOrNull()
            }
        } else null
    }

    fun captureCleanScreenshot() {
        if (captureInProgress || (screenshotSourceItem == null && currentItem?.isVideo != false)) return
        val host = activity ?: return
        scope.launch {
            captureInProgress = true
            optionsExpanded = false
            delay(140)
            val root = host.window.decorView.rootView
            val bitmap = if (root.width > 0 && root.height > 0) {
                Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            } else null
            val saved = bitmap?.let { shot ->
                runCatching {
                    root.draw(android.graphics.Canvas(shot))
                    val selectedTree = screenshotFolderUri
                        ?.takeIf(::hasCustomScreenshotFolder)
                        ?.let(Uri::parse)
                    if (selectedTree != null) saveScreenshotToTree(context, shot, selectedTree)
                    else screenshotActions.saveScreenshot(shot)
                }.getOrNull().also { shot.recycle() }
            }
            captureInProgress = false
            Toast.makeText(
                context,
                if (saved != null) "Screenshot kaydedildi" else "Screenshot kaydedilemedi",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun toggleFavorite(item: GalleryMedia) {
        val id = item.id.toString()
        favoriteIds = if (id in favoriteIds) favoriteIds - id else favoriteIds + id
        prefs.edit().putStringSet("favorite_ids", favoriteIds.toSet()).apply()
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        backgroundBitmap?.let { bg ->
            Image(
                bitmap = bg.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(32.dp)
                    .graphicsLayer(alpha = 0.42f, scaleX = 1.12f, scaleY = 1.12f)
            )
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.34f)))
        }

        val explicitScreenshotSource = screenshotSourceItem
        if (screenshotMode && explicitScreenshotSource != null) {
            val rotation = rotations[explicitScreenshotSource.id] ?: 0f
            StablePhotoPage(
                item = explicitScreenshotSource,
                rotation = rotation,
                onRotationChanged = { rotations[explicitScreenshotSource.id] = it },
                onScaleChanged = { zooms[explicitScreenshotSource.id] = it },
                onGestureActive = { gestureActive = it },
                onFitTap = { controlsVisible = !controlsVisible }
            )
        } else {
            HorizontalPager(
                state = pager,
                userScrollEnabled = shouldEnablePager(currentScale, currentRotation),
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val item = items[page]
                val rotation = rotations[item.id] ?: 0f
                if (item.isVideo) {
                    VideoPage(item, rotation)
                } else {
                    StablePhotoPage(
                        item = item,
                        rotation = rotation,
                        onRotationChanged = { rotations[item.id] = it },
                        onScaleChanged = { zooms[item.id] = it },
                        onGestureActive = { gestureActive = it },
                        onFitTap = { controlsVisible = !controlsVisible }
                    )
                }
            }
        }

        if (renderChrome) {
            val current = currentItem
            if (current != null) {
                Row(
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.62f))
                        .padding(top = 14.dp, start = 2.dp, end = 2.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        current.name,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (!current.isVideo) {
                        IconButton(onClick = {
                            rotations[current.id] = nextQuarterRotation(rotations[current.id] ?: 0f)
                        }) {
                            Icon(Icons.Default.RotateRight, "Döndür", tint = Color.White)
                        }
                        IconButton(onClick = { onCrop(current) }) {
                            Icon(Icons.Default.Crop, "Düzenle", tint = Color.White)
                        }
                    }
                    Box {
                        IconButton(onClick = { optionsExpanded = true }) {
                            Icon(Icons.Default.MoreVert, "Diğer", tint = Color.White)
                        }
                        DropdownMenu(
                            expanded = optionsExpanded,
                            onDismissRequest = { optionsExpanded = false }
                        ) {
                            if (!current.isVideo) {
                                DropdownMenuItem(
                                    text = { Text("Screenshot modu") },
                                    leadingIcon = { Icon(Icons.Default.PhotoCamera, null) },
                                    onClick = {
                                        screenshotMode = !screenshotMode
                                        optionsExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Screenshot için fotoğraf seç") },
                                    leadingIcon = { Icon(Icons.Default.PhotoCamera, null) },
                                    onClick = {
                                        optionsExpanded = false
                                        screenshotSourcePicker.launch(arrayOf("image/*"))
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Screenshot klasörü seç") },
                                    leadingIcon = { Icon(Icons.Default.Folder, null) },
                                    onClick = {
                                        optionsExpanded = false
                                        screenshotFolderPicker.launch(null)
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Ad değiştir") },
                                leadingIcon = { Icon(Icons.Default.Edit, null) },
                                onClick = {
                                    optionsExpanded = false
                                    onRename(current)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(if (slideshowRunning) "Slaytı durdur" else "Slayt gösterisi") },
                                onClick = {
                                    optionsExpanded = false
                                    slideshowRunning = !slideshowRunning
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Çöpe taşı / sil") },
                                leadingIcon = { Icon(Icons.Default.Delete, null) },
                                onClick = {
                                    optionsExpanded = false
                                    onTrash(current)
                                }
                            )
                        }
                    }
                }

                Row(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.66f))
                        .padding(horizontal = 2.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Geri", tint = Color.White)
                    }
                    IconButton(onClick = { toggleFavorite(current) }) {
                        Text(if (current.id.toString() in favoriteIds) "♥" else "♡", color = Color.White, style = MaterialTheme.typography.headlineSmall)
                    }
                    IconButton(onClick = { if (!current.isVideo) onCrop(current) }) {
                        Icon(Icons.Default.Edit, "Düzenle", tint = if (current.isVideo) Color.Gray else Color.White)
                    }
                    IconButton(onClick = { onShare(current) }) {
                        Icon(Icons.Default.Share, "Paylaş", tint = Color.White)
                    }
                    IconButton(onClick = { onTrash(current) }) {
                        Icon(Icons.Default.Delete, "Çöp", tint = Color.White)
                    }
                    IconButton(onClick = { showInfo = !showInfo }) {
                        Text("ⓘ", color = Color.White, style = MaterialTheme.typography.headlineSmall)
                    }
                    IconButton(onClick = { slideshowRunning = !slideshowRunning }) {
                        Text(if (slideshowRunning) "Ⅱ" else "▶", color = Color.White, style = MaterialTheme.typography.titleLarge)
                    }
                }

                if (showInfo) {
                    Column(
                        Modifier
                            .align(Alignment.CenterStart)
                            .padding(16.dp)
                            .background(Color.Black.copy(alpha = 0.82f), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Text(current.name, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("${current.width} × ${current.height}", color = Color.White)
                        Text(formatBytes(current.size), color = Color.White)
                        Text(current.relativePath, color = Color.LightGray, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (current.isVideo) Text("Süre: ${formatDuration(current.durationMs)}", color = Color.White)
                    }
                }
            }
        }

        if (screenshotMode && currentItem?.isVideo == false && !captureInProgress) {
            Box(
                Modifier
                    .align(Alignment.Center)
                    .offset {
                        IntOffset(
                            screenshotOffsetX.roundToInt(),
                            screenshotOffsetY.roundToInt()
                        )
                    }
                    .background(Color.Black.copy(alpha = 0.68f), CircleShape)
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            screenshotOffsetX += dragAmount.x
                            screenshotOffsetY += dragAmount.y
                        }
                    }
            ) {
                IconButton(onClick = { captureCleanScreenshot() }) {
                    Icon(Icons.Default.PhotoCamera, "Screenshot", tint = Color.White)
                }
            }
        }
    }
}
@Composable
private fun PhotoPage(item: GalleryMedia, rotation: Float) {
    val context = LocalContext.current
    var scale by remember(item.id) { mutableFloatStateOf(1f) }
    var offsetX by remember(item.id) { mutableFloatStateOf(0f) }
    var offsetY by remember(item.id) { mutableFloatStateOf(0f) }
    val bitmap by produceState<Bitmap?>(initialValue = null, item.uri) {
        value = loadHighResolutionBitmap(context, item)
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (bitmap == null) {
            CircularProgressIndicator()
        } else {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = item.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(item.id) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val newScale = clampViewerScale(scale * zoom)
                            if (newScale <= 1.01f) {
                                offsetX = 0f
                                offsetY = 0f
                            } else {
                                offsetX += pan.x
                                offsetY += pan.y
                            }
                            scale = newScale
                        }
                    }
                    .pointerInput(item.id, scale) {
                        detectTapGestures(
                            onTap = {
                                if (scale > 1.01f || offsetX != 0f || offsetY != 0f) {
                                    scale = 1f; offsetX = 0f; offsetY = 0f
                                }
                            },
                            onDoubleTap = {
                                scale = nextDoubleTapScale(scale)
                                if (scale <= 1.01f) { offsetX = 0f; offsetY = 0f }
                            }
                        )
                    }
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offsetX,
                        translationY = offsetY,
                        rotationZ = rotation
                    )
            )
        }
    }
}

@Composable
private fun VideoPage(item: GalleryMedia, rotation: Float) {
    val context = LocalContext.current
    val player = remember(item.uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(androidx.media3.common.MediaItem.fromUri(item.uri))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                useController = true
            }
        },
        update = { it.player = player },
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer(rotationZ = rotation)
    )
}

@Composable
private fun RenameDialog(
    item: GalleryMedia,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember(item.id) { mutableStateOf(item.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ad değiştir") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text("Dosya adı") }
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(name) }) { Text("Kaydet") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("İptal") } }
    )
}

@Composable
private fun PathDialog(
    action: PathAction,
    albums: List<GalleryAlbum>,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var path by remember { mutableStateOf("Pictures/ATMACA/") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (action == PathAction.COPY) "Kopyalanacak klasör" else "Taşınacak klasör") },
        text = {
            Column {
                OutlinedTextField(
                    value = path,
                    onValueChange = { path = it },
                    label = { Text("Örn: Pictures/ATMACA/") },
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                Text("Mevcut albümler", style = MaterialTheme.typography.labelLarge)
                LazyColumn(Modifier.height(220.dp)) {
                    items(albums.take(60), key = { it.relativePath }) { album ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { path = album.relativePath }
                                .padding(vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Folder, null)
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(album.name)
                                Text(album.relativePath, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(normalizeRelativePath(path)) }) { Text("Uygula") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("İptal") } }
    )
}

@Composable
private fun SettingsDialog(
    gridColumns: Int,
    mediaFilter: MediaFilter,
    mediaSort: MediaSort,
    sortDirection: SortDirection,
    onDismiss: () -> Unit,
    onGridColumns: (Int) -> Unit,
    onMediaFilter: (MediaFilter) -> Unit,
    onMediaSort: (MediaSort) -> Unit,
    onSortDirection: (SortDirection) -> Unit,
    onOpenTrash: () -> Unit,
    onOpenDuplicates: () -> Unit
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
                CompleteSettingsExtras(
                    onOpenTrash = onOpenTrash,
                    onOpenDuplicates = onOpenDuplicates
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tamam") } }
    )
}

private fun formatDuration(ms: Long): String {
    val total = ms / 1000L
    val hours = total / 3600L
    val minutes = (total % 3600L) / 60L
    val seconds = total % 60L
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%d:%02d".format(minutes, seconds)
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return "%.1f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024.0) return "%.1f MB".format(mb)
    return "%.2f GB".format(mb / 1024.0)
}
