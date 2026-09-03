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
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class HomeSection { PHOTOS, VIDEOS, ALBUMS, DUPLICATES, TRASH }
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

    var section by rememberSaveable { mutableStateOf(HomeSection.PHOTOS) }
    var viewerIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var refreshToken by remember { mutableIntStateOf(0) }
    var albumsRefresh by remember { mutableIntStateOf(0) }
    var duplicatesRefresh by remember { mutableIntStateOf(0) }
    var gridColumns by rememberSaveable { mutableIntStateOf(prefs.getInt("grid_columns", 4).coerceIn(3, 6)) }
    var showSettings by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }
    var renameItem by remember { mutableStateOf<GalleryMedia?>(null) }
    var pathAction by remember { mutableStateOf<PathAction?>(null) }
    var pathItems by remember { mutableStateOf<List<GalleryMedia>>(emptyList()) }
    var pendingWriteAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

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
        if (result.resultCode == Activity.RESULT_OK) {
            selectedIds = emptySet()
            viewerIndex = null
            refreshToken++
            albumsRefresh++
            duplicatesRefresh++
            vm.reload()
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
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
                onRename = { renameItem = it }
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

    val albums by produceState<List<GalleryAlbum>>(
        initialValue = emptyList(), albumsRefresh, refreshToken
    ) {
        value = runCatching { repository.loadAlbums() }.getOrDefault(emptyList())
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
        SettingsDialog(
            gridColumns = gridColumns,
            onDismiss = { showSettings = false },
            onGridColumns = { columns ->
                gridColumns = columns
                prefs.edit().putInt("grid_columns", columns).apply()
            }
        )
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
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
                    refreshToken++
                    albumsRefresh++
                    duplicatesRefresh++
                    when (section) {
                        HomeSection.ALBUMS -> if (state.mode == CollectionMode.ALBUM) vm.reload()
                        HomeSection.DUPLICATES -> Unit
                        else -> vm.reload()
                    }
                },
                onSettings = { showSettings = true },
                showMore = showMore,
                onMoreChange = { showMore = it }
            )

            if (selected.isNotEmpty()) {
                SelectionBar(
                    selectedCount = selected.size,
                    trashMode = state.mode == CollectionMode.TRASH,
                    canRename = selected.size == 1 && state.mode != CollectionMode.TRASH,
                    onClear = { selectedIds = emptySet() },
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
                HomeSection.PHOTOS, HomeSection.VIDEOS, HomeSection.TRASH -> {
                    MediaCollection(
                        state = state,
                        gridColumns = gridColumns,
                        selectedIds = selectedIds,
                        onToggleSelection = { id ->
                            selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
                        },
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
                            selectedIds = emptySet()
                            vm.switchTab(GalleryTab.PHOTOS)
                        }
                        MediaCollection(
                            state = state,
                            gridColumns = gridColumns,
                            selectedIds = selectedIds,
                            onToggleSelection = { id ->
                                selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
                            },
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
                            albums = albums,
                            onOpen = { album -> vm.openAlbum(album.relativePath) }
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
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
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
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("$selectedCount seçili", modifier = Modifier.weight(1f))
        IconButton(onClick = onShare) { Icon(Icons.Default.Share, "Paylaş") }
        if (!trashMode) {
            IconButton(onClick = onCopy) { Icon(Icons.Default.ContentCopy, "Kopyala") }
            IconButton(onClick = onMove) { Icon(Icons.Default.DriveFileMove, "Taşı") }
            if (canRename) IconButton(onClick = onRename) { Icon(Icons.Default.Edit, "Ad değiştir") }
        }
        IconButton(onClick = onTrashOrRestore) {
            Icon(if (trashMode) Icons.Default.Restore else Icons.Default.Delete, if (trashMode) "Geri al" else "Çöpe taşı")
        }
        if (trashMode) {
            IconButton(onClick = onDeleteForever) { Icon(Icons.Default.Delete, "Kalıcı sil") }
        }
        TextButton(onClick = onClear) { Text("İptal") }
    }
}

@Composable
private fun MediaCollection(
    state: GalleryUiState,
    gridColumns: Int,
    selectedIds: Set<Long>,
    onToggleSelection: (Long) -> Unit,
    onOpen: (Int) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit
) {
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

        else -> MediaGrid(
            items = state.items,
            loading = state.loading,
            hasMore = state.hasMore,
            columns = gridColumns,
            selectedIds = selectedIds,
            onToggleSelection = onToggleSelection,
            onOpen = onOpen,
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
    onOpen: (Int) -> Unit,
    onLoadMore: () -> Unit
) {
    val gridState = rememberLazyGridState()

    LaunchedEffect(gridState, items.size, hasMore) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .map { last -> items.size - last }
            .distinctUntilChanged()
            .collect { remaining ->
                if (hasMore && items.isNotEmpty() && remaining <= columns * 4) onLoadMore()
            }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        state = gridState,
        modifier = Modifier.fillMaxSize(),
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
                    .combinedClickable(
                        onClick = { onOpen(index) },
                        onLongClick = { onToggleSelection(item.id) }
                    )
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
        itemsIndexed(albums, key = { _, album -> album.relativePath }) { _, album ->
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
    onRename: (GalleryMedia) -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val pager = rememberPagerState(initialPage = initialIndex) { items.size }
    val rotations = remember { mutableStateMapOf<Long, Float>() }
    var controlsVisible by remember { mutableStateOf(true) }

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

    LaunchedEffect(pager, items.size) {
        snapshotFlow { pager.currentPage }
            .distinctUntilChanged()
            .collect { page -> if (items.size - page <= 4) onNeedMore() }
    }

    BackHandler(onBack = onBack)

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { controlsVisible = !controlsVisible })
            }
    ) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
            val item = items[page]
            val rotation = rotations[item.id] ?: 0f
            if (item.isVideo) VideoPage(item, rotation) else PhotoPage(item, rotation)
        }

        if (controlsVisible) {
            Row(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(top = 22.dp, start = 4.dp, end = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Geri", tint = Color.White) }
                Text(
                    items.getOrNull(pager.currentPage)?.name.orEmpty(),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            val current = items.getOrNull(pager.currentPage)
            if (current != null) {
                Row(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.60f))
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { onShare(current) }) { Icon(Icons.Default.Share, "Paylaş", tint = Color.White) }
                    IconButton(onClick = {
                        rotations[current.id] = ((rotations[current.id] ?: 0f) + 90f) % 360f
                    }) { Icon(Icons.Default.RotateRight, "Döndür", tint = Color.White) }
                    IconButton(onClick = { onRename(current) }) { Icon(Icons.Default.Edit, "Ad değiştir", tint = Color.White) }
                    IconButton(onClick = { onTrash(current) }) { Icon(Icons.Default.Delete, "Çöp", tint = Color.White) }
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
                            val newScale = (scale * zoom).coerceIn(1f, 8f)
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
                            onDoubleTap = {
                                if (scale > 1.1f) {
                                    scale = 1f
                                    offsetX = 0f
                                    offsetY = 0f
                                } else {
                                    scale = 2.5f
                                }
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
