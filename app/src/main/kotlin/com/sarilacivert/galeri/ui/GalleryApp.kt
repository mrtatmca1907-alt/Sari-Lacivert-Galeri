package com.sarilacivert.galeri.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ManageSearch
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.ViewColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.sarilacivert.galeri.data.Album
import com.sarilacivert.galeri.data.AlbumSort
import com.sarilacivert.galeri.data.BitmapLoader
import com.sarilacivert.galeri.data.GalleryPreferences
import com.sarilacivert.galeri.data.MediaItem
import com.sarilacivert.galeri.data.MediaRepository
import com.sarilacivert.galeri.data.MediaSort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface Screen {
    data object Albums : Screen
    data class AlbumView(val album: Album) : Screen
    data object Search : Screen
    data object Favorites : Screen
    data object Duplicates : Screen
    data object Trash : Screen
    data object Settings : Screen
    data class Viewer(
        val items: List<MediaItem>,
        val startIndex: Int,
        val returnTo: Screen,
        val slideshow: Boolean = false,
        val fromTrash: Boolean = false
    ) : Screen
}

@Composable
fun GalleryApp() {
    val context = LocalContext.current
    val repo = remember { MediaRepository(context.applicationContext) }
    val prefs = remember { GalleryPreferences(context.applicationContext) }
    val loader = remember { BitmapLoader(context.applicationContext) }
    val scope = rememberCoroutineScope()

    var screen by remember { mutableStateOf<Screen>(Screen.Albums) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var hasPermission by remember { mutableStateOf(hasMediaPermission(context)) }

    val favorites by prefs.favorites.collectAsState(initial = emptySet())
    val gridColumns by prefs.gridColumns.collectAsState(initial = 3)
    val showImages by prefs.showImages.collectAsState(initial = true)
    val showVideos by prefs.showVideos.collectAsState(initial = true)
    val mediaSort by prefs.defaultMediaSort.collectAsState(initial = MediaSort.NEWEST)
    val albumSort by prefs.defaultAlbumSort.collectAsState(initial = AlbumSort.NEWEST)
    val slideshowSeconds by prefs.slideshowSeconds.collectAsState(initial = 3)
    val duplicateDistance by prefs.duplicateDistance.collectAsState(initial = 8)

    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        hasPermission = hasMediaPermission(context)
        if (hasPermission) refreshKey++
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(requiredMediaPermissions())
    }

    if (!hasPermission) {
        PermissionScreen(onRequest = { permissionLauncher.launch(requiredMediaPermissions()) })
        return
    }

    when (val current = screen) {
        Screen.Albums -> AlbumsScreen(
            repo = repo,
            loader = loader,
            showImages = showImages,
            showVideos = showVideos,
            sort = albumSort,
            refreshKey = refreshKey,
            onOpenAlbum = { screen = Screen.AlbumView(it) },
            onSearch = { screen = Screen.Search },
            onCamera = {
                runCatching {
                    context.startActivity(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }.onFailure { Toast.makeText(context, "Kamera açılamadı", Toast.LENGTH_SHORT).show() }
            },
            onRefresh = { refreshKey++ },
            onSortChange = { scope.launch { prefs.setDefaultAlbumSort(it) } },
            bottomScreen = Screen.Albums,
            onBottomNavigate = { screen = it }
        )

        is Screen.AlbumView -> AlbumScreen(
            album = current.album,
            repo = repo,
            loader = loader,
            favorites = favorites,
            columns = gridColumns,
            sort = mediaSort,
            showImages = showImages,
            showVideos = showVideos,
            refreshKey = refreshKey,
            onBack = { screen = Screen.Albums },
            onOpen = { items, index, slideshow ->
                screen = Screen.Viewer(items, index, current, slideshow = slideshow)
            },
            onColumns = { scope.launch { prefs.setGridColumns(it) } },
            onSort = { scope.launch { prefs.setDefaultMediaSort(it) } }
        )

        Screen.Search -> SearchScreen(
            repo = repo,
            loader = loader,
            favorites = favorites,
            columns = gridColumns,
            sort = mediaSort,
            showImages = showImages,
            showVideos = showVideos,
            onBack = { screen = Screen.Albums },
            onOpen = { items, index -> screen = Screen.Viewer(items, index, Screen.Search) }
        )

        Screen.Favorites -> FavoritesScreen(
            repo = repo,
            loader = loader,
            favorites = favorites,
            columns = gridColumns,
            sort = mediaSort,
            refreshKey = refreshKey,
            onOpen = { items, index -> screen = Screen.Viewer(items, index, Screen.Favorites) },
            bottomScreen = Screen.Favorites,
            onBottomNavigate = { screen = it }
        )

        Screen.Duplicates -> DuplicatesScreen(
            loader = loader,
            duplicateDistance = duplicateDistance,
            favorites = favorites,
            onOpen = { items, index -> screen = Screen.Viewer(items, index, Screen.Duplicates) },
            bottomScreen = Screen.Duplicates,
            onBottomNavigate = { screen = it }
        )

        Screen.Trash -> TrashScreen(
            repo = repo,
            loader = loader,
            columns = gridColumns,
            sort = mediaSort,
            refreshKey = refreshKey,
            onOpen = { items, index -> screen = Screen.Viewer(items, index, Screen.Trash, fromTrash = true) },
            bottomScreen = Screen.Trash,
            onBottomNavigate = { screen = it }
        )

        Screen.Settings -> SettingsScreen(
            columns = gridColumns,
            slideshowSeconds = slideshowSeconds,
            showImages = showImages,
            showVideos = showVideos,
            duplicateDistance = duplicateDistance,
            mediaSort = mediaSort,
            albumSort = albumSort,
            onColumns = { scope.launch { prefs.setGridColumns(it) } },
            onSlideshowSeconds = { scope.launch { prefs.setSlideshowSeconds(it) } },
            onShowImages = { scope.launch { prefs.setShowImages(it) } },
            onShowVideos = { scope.launch { prefs.setShowVideos(it) } },
            onDuplicateDistance = { scope.launch { prefs.setDuplicateDistance(it) } },
            onMediaSort = { scope.launch { prefs.setDefaultMediaSort(it) } },
            onAlbumSort = { scope.launch { prefs.setDefaultAlbumSort(it) } },
            bottomScreen = Screen.Settings,
            onBottomNavigate = { screen = it }
        )

        is Screen.Viewer -> ViewerScreen(
            initialItems = current.items,
            startIndex = current.startIndex,
            repo = repo,
            loader = loader,
            prefs = prefs,
            favorites = favorites,
            slideshowSeconds = slideshowSeconds,
            startSlideshow = current.slideshow,
            fromTrash = current.fromTrash,
            onBack = { screen = current.returnTo },
            onChanged = { refreshKey++ }
        )
    }
}

@Composable
private fun PermissionScreen(onRequest: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Galeri izni gerekiyor", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
            Spacer(Modifier.height(10.dp))
            Text(
                "Fotoğraf ve videoları göstermek için Android medya iznini ver. Uygulama sadece cihazındaki medyayı okur.",
                color = TextSecondary
            )
            Spacer(Modifier.height(18.dp))
            Button(onClick = onRequest) { Text("İzin ver") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlbumsScreen(
    repo: MediaRepository,
    loader: BitmapLoader,
    showImages: Boolean,
    showVideos: Boolean,
    sort: AlbumSort,
    refreshKey: Int,
    onOpenAlbum: (Album) -> Unit,
    onSearch: () -> Unit,
    onCamera: () -> Unit,
    onRefresh: () -> Unit,
    onSortChange: (AlbumSort) -> Unit,
    bottomScreen: Screen,
    onBottomNavigate: (Screen) -> Unit
) {
    var albums by remember { mutableStateOf<List<Album>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var sortMenu by remember { mutableStateOf(false) }

    LaunchedEffect(showImages, showVideos, sort, refreshKey) {
        loading = true
        albums = repo.loadAlbums(showImages, showVideos, sort)
        loading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Sarı Lacivert Galeri", fontWeight = FontWeight.Bold)
                        Text("${albums.size} albüm", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    }
                },
                actions = {
                    IconButton(onClick = onSearch) { Icon(Icons.Default.Search, "Ara") }
                    IconButton(onClick = onCamera) { Icon(Icons.Default.CameraAlt, "Kamera") }
                    IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, "Yenile") }
                    Box {
                        IconButton(onClick = { sortMenu = true }) { Icon(Icons.Default.Sort, "Sırala") }
                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                            AlbumSort.entries.forEach { choice ->
                                DropdownMenuItem(
                                    text = { Text(albumSortLabel(choice)) },
                                    onClick = { sortMenu = false; onSortChange(choice) }
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Navy800)
            )
        },
        bottomBar = { GalleryBottomBar(bottomScreen, onBottomNavigate) },
        containerColor = Navy900
    ) { padding ->
        when {
            loading -> Box(Modifier.padding(padding)) { LoadingState("Albümler taranıyor…") }
            albums.isEmpty() -> Box(Modifier.padding(padding)) { EmptyState("Medya bulunamadı", "Ayarlar > izinleri kontrol et veya yenile.") }
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 4.dp)
            ) {
                items(albums, key = { it.path }) { album ->
                    AlbumCard(album, loader) { onOpenAlbum(album) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlbumScreen(
    album: Album,
    repo: MediaRepository,
    loader: BitmapLoader,
    favorites: Set<String>,
    columns: Int,
    sort: MediaSort,
    showImages: Boolean,
    showVideos: Boolean,
    refreshKey: Int,
    onBack: () -> Unit,
    onOpen: (List<MediaItem>, Int, Boolean) -> Unit,
    onColumns: (Int) -> Unit,
    onSort: (MediaSort) -> Unit
) {
    var items by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var sortMenu by remember { mutableStateOf(false) }

    LaunchedEffect(album.path, showImages, showVideos, sort, refreshKey) {
        loading = true
        items = repo.loadAlbum(album.path, showImages, showVideos, sort)
        loading = false
    }
    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(album.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${items.size} öğe • ${album.path}", style = MaterialTheme.typography.labelSmall, color = TextSecondary, maxLines = 1)
                    }
                },
                navigationIcon = { TextButton(onClick = onBack) { Text("‹ Geri") } },
                actions = {
                    IconButton(onClick = { onColumns(if (columns >= 5) 3 else columns + 1) }) { Icon(Icons.Default.ViewColumn, "Sütun") }
                    if (items.any { !it.isVideo }) {
                        TextButton(onClick = { onOpen(items, items.indexOfFirst { !it.isVideo }.coerceAtLeast(0), true) }) { Text("Slayt") }
                    }
                    Box {
                        IconButton(onClick = { sortMenu = true }) { Icon(Icons.Default.Sort, "Sırala") }
                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                            MediaSort.entries.forEach { choice ->
                                DropdownMenuItem(text = { Text(mediaSortLabel(choice)) }, onClick = { sortMenu = false; onSort(choice) })
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Navy800)
            )
        },
        containerColor = Navy900
    ) { padding ->
        when {
            loading -> Box(Modifier.padding(padding)) { LoadingState() }
            items.isEmpty() -> Box(Modifier.padding(padding)) { EmptyState("Bu albüm boş") }
            else -> MediaGrid(
                items = items,
                loader = loader,
                favorites = favorites,
                columns = columns,
                modifier = Modifier.fillMaxSize().padding(padding),
                onOpen = { index -> onOpen(items, index, false) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchScreen(
    repo: MediaRepository,
    loader: BitmapLoader,
    favorites: Set<String>,
    columns: Int,
    sort: MediaSort,
    showImages: Boolean,
    showVideos: Boolean,
    onBack: () -> Unit,
    onOpen: (List<MediaItem>, Int) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var items by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    BackHandler(onBack = onBack)

    LaunchedEffect(query, sort, showImages, showVideos) {
        if (query.trim().length < 2) {
            items = emptyList()
            return@LaunchedEffect
        }
        loading = true
        items = repo.search(query, showImages, showVideos, sort)
        loading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Medya Ara") },
                navigationIcon = { TextButton(onClick = onBack) { Text("‹ Geri") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Navy800)
            )
        },
        containerColor = Navy900
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Dosya, albüm veya klasör adı") },
                leadingIcon = { Icon(Icons.Default.ManageSearch, null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(12.dp)
            )
            when {
                query.trim().length < 2 -> EmptyState("Aramak için en az 2 karakter yaz")
                loading -> LoadingState("Aranıyor…")
                items.isEmpty() -> EmptyState("Sonuç bulunamadı")
                else -> MediaGrid(items, loader, favorites, columns, Modifier.weight(1f).fillMaxWidth()) { onOpen(items, it) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FavoritesScreen(
    repo: MediaRepository,
    loader: BitmapLoader,
    favorites: Set<String>,
    columns: Int,
    sort: MediaSort,
    refreshKey: Int,
    onOpen: (List<MediaItem>, Int) -> Unit,
    bottomScreen: Screen,
    onBottomNavigate: (Screen) -> Unit
) {
    var items by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(favorites, sort, refreshKey) {
        loading = true
        items = repo.loadFavoriteItems(favorites, sort)
        loading = false
    }
    Scaffold(
        topBar = { TopAppBar(title = { Text("Favoriler") }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Navy800)) },
        bottomBar = { GalleryBottomBar(bottomScreen, onBottomNavigate) },
        containerColor = Navy900
    ) { padding ->
        when {
            loading -> Box(Modifier.padding(padding)) { LoadingState() }
            items.isEmpty() -> Box(Modifier.padding(padding)) { EmptyState("Favori yok", "Bir fotoğraf veya videoda ★ simgesine dokun.") }
            else -> MediaGrid(items, loader, favorites, columns, Modifier.fillMaxSize().padding(padding)) { onOpen(items, it) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrashScreen(
    repo: MediaRepository,
    loader: BitmapLoader,
    columns: Int,
    sort: MediaSort,
    refreshKey: Int,
    onOpen: (List<MediaItem>, Int) -> Unit,
    bottomScreen: Screen,
    onBottomNavigate: (Screen) -> Unit
) {
    var items by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(sort, refreshKey) {
        loading = true
        items = repo.loadTrash(sort)
        loading = false
    }
    Scaffold(
        topBar = { TopAppBar(title = { Text("Çöp Kutusu") }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Navy800)) },
        bottomBar = { GalleryBottomBar(bottomScreen, onBottomNavigate) },
        containerColor = Navy900
    ) { padding ->
        when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.R -> Box(Modifier.padding(padding)) { EmptyState("Sistem çöp kutusu Android 11+ gerektirir") }
            loading -> Box(Modifier.padding(padding)) { LoadingState() }
            items.isEmpty() -> Box(Modifier.padding(padding)) { EmptyState("Çöp kutusu boş") }
            else -> MediaGrid(items, loader, emptySet(), columns, Modifier.fillMaxSize().padding(padding)) { onOpen(items, it) }
        }
    }
}

@Composable
private fun MediaGrid(
    items: List<MediaItem>,
    loader: BitmapLoader,
    favorites: Set<String>,
    columns: Int,
    modifier: Modifier = Modifier,
    onOpen: (Int) -> Unit
) {
    LazyVerticalGrid(columns = GridCells.Fixed(columns.coerceIn(3, 5)), modifier = modifier) {
        items(items.size, key = { items[it].uri.toString() }) { index ->
            val item = items[index]
            MediaTile(item, loader, item.uri.toString() in favorites, onClick = { onOpen(index) })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    columns: Int,
    slideshowSeconds: Int,
    showImages: Boolean,
    showVideos: Boolean,
    duplicateDistance: Int,
    mediaSort: MediaSort,
    albumSort: AlbumSort,
    onColumns: (Int) -> Unit,
    onSlideshowSeconds: (Int) -> Unit,
    onShowImages: (Boolean) -> Unit,
    onShowVideos: (Boolean) -> Unit,
    onDuplicateDistance: (Int) -> Unit,
    onMediaSort: (MediaSort) -> Unit,
    onAlbumSort: (AlbumSort) -> Unit,
    bottomScreen: Screen,
    onBottomNavigate: (Screen) -> Unit
) {
    var mediaSortMenu by remember { mutableStateOf(false) }
    var albumSortMenu by remember { mutableStateOf(false) }
    Scaffold(
        topBar = { TopAppBar(title = { Text("Ayarlar") }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Navy800)) },
        bottomBar = { GalleryBottomBar(bottomScreen, onBottomNavigate) },
        containerColor = Navy900
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SettingRow("Fotoğrafları göster", "Albüm ve aramalarda fotoğrafları dahil et") { Switch(showImages, onCheckedChange = onShowImages) }
            SettingRow("Videoları göster", "Albüm ve aramalarda videoları dahil et") { Switch(showVideos, onCheckedChange = onShowVideos) }

            Text("Izgara: $columns sütun", color = TextPrimary, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (3..5).forEach { c -> Button(onClick = { onColumns(c) }, enabled = c != columns) { Text("$c") } }
            }

            Text("Slayt geçişi: ${slideshowSeconds} sn", color = TextPrimary, fontWeight = FontWeight.Bold)
            Slider(value = slideshowSeconds.toFloat(), onValueChange = { onSlideshowSeconds(it.toInt()) }, valueRange = 2f..10f, steps = 7)

            Text("Benzer fotoğraf hassasiyeti: $duplicateDistance", color = TextPrimary, fontWeight = FontWeight.Bold)
            Text("Düşük değer daha katı, yüksek değer daha geniş eşleşir.", color = TextSecondary)
            Slider(value = duplicateDistance.toFloat(), onValueChange = { onDuplicateDistance(it.toInt()) }, valueRange = 2f..16f, steps = 13)

            Box {
                Button(onClick = { mediaSortMenu = true }) { Text("Medya sıralama: ${mediaSortLabel(mediaSort)}") }
                DropdownMenu(mediaSortMenu, onDismissRequest = { mediaSortMenu = false }) {
                    MediaSort.entries.forEach { s -> DropdownMenuItem(text = { Text(mediaSortLabel(s)) }, onClick = { mediaSortMenu = false; onMediaSort(s) }) }
                }
            }
            Box {
                Button(onClick = { albumSortMenu = true }) { Text("Albüm sıralama: ${albumSortLabel(albumSort)}") }
                DropdownMenu(albumSortMenu, onDismissRequest = { albumSortMenu = false }) {
                    AlbumSort.entries.forEach { s -> DropdownMenuItem(text = { Text(albumSortLabel(s)) }, onClick = { albumSortMenu = false; onAlbumSort(s) }) }
                }
            }

            Text("Android 13 uyumlu • minSdk 26 • targetSdk 36 • compileSdk 36", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SettingRow(title: String, subtitle: String, control: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontWeight = FontWeight.Bold)
            Text(subtitle, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
        control()
    }
}

@Composable
private fun GalleryBottomBar(current: Screen, onNavigate: (Screen) -> Unit) {
    NavigationBar(containerColor = Navy800) {
        NavigationBarItem(
            selected = current == Screen.Albums,
            onClick = { onNavigate(Screen.Albums) },
            icon = { Icon(Icons.Default.Folder, null) },
            label = { Text("Albümler") }
        )
        NavigationBarItem(
            selected = current == Screen.Favorites,
            onClick = { onNavigate(Screen.Favorites) },
            icon = { Icon(Icons.Default.Favorite, null) },
            label = { Text("Favori") }
        )
        NavigationBarItem(
            selected = current == Screen.Duplicates,
            onClick = { onNavigate(Screen.Duplicates) },
            icon = { Icon(Icons.Default.GridView, null) },
            label = { Text("Benzer") }
        )
        NavigationBarItem(
            selected = current == Screen.Trash,
            onClick = { onNavigate(Screen.Trash) },
            icon = { Icon(Icons.Default.DeleteOutline, null) },
            label = { Text("Çöp") }
        )
        NavigationBarItem(
            selected = current == Screen.Settings,
            onClick = { onNavigate(Screen.Settings) },
            icon = { Icon(Icons.Default.Settings, null) },
            label = { Text("Ayarlar") }
        )
    }
}

private fun requiredMediaPermissions(): Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    buildList {
        add(Manifest.permission.READ_MEDIA_IMAGES)
        add(Manifest.permission.READ_MEDIA_VIDEO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
    }.toTypedArray()
} else {
    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

private fun hasMediaPermission(context: android.content.Context): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    val images = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
    val videos = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
    val selected = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED
    images || videos || selected
} else {
    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
}

fun mediaSortLabel(sort: MediaSort): String = when (sort) {
    MediaSort.NEWEST -> "En yeni"
    MediaSort.OLDEST -> "En eski"
    MediaSort.NAME_ASC -> "Ad A→Z"
    MediaSort.NAME_DESC -> "Ad Z→A"
    MediaSort.SIZE_DESC -> "En büyük"
}

fun albumSortLabel(sort: AlbumSort): String = when (sort) {
    AlbumSort.NEWEST -> "En yeni"
    AlbumSort.NAME -> "Ada göre"
    AlbumSort.COUNT -> "Öğe sayısı"
}
