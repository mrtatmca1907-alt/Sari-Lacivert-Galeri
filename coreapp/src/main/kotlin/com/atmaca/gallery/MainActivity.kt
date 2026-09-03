package com.atmaca.gallery

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Size
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.snapshotFlow
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFFFFD600),
                    secondary = Color(0xFF1D3F91),
                    background = Color(0xFF071226),
                    surface = Color(0xFF0E1D38)
                )
            ) {
                Surface(Modifier.fillMaxSize()) {
                    AtmacaGalleryApp()
                }
            }
        }
    }
}

@Composable
private fun AtmacaGalleryApp(vm: GalleryViewModel = viewModel()) {
    val context = LocalContext.current
    val required = remember { requiredMediaPermissions(Build.VERSION.SDK_INT) }
    var permissionTick by remember { mutableStateOf(0) }
    val granted = remember(permissionTick) {
        required.all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        permissionTick++
    }

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
    val state by vm.state.collectAsState()
    var viewerIndex by remember { mutableStateOf<Int?>(null) }

    viewerIndex?.let { index ->
        if (state.items.isNotEmpty()) {
            MediaViewer(
                items = state.items,
                initialIndex = index.coerceIn(0, state.items.lastIndex),
                onBack = { viewerIndex = null },
                onNeedMore = vm::loadNextPage
            )
            return
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = state.tab == GalleryTab.PHOTOS,
                    onClick = { vm.switchTab(GalleryTab.PHOTOS) },
                    icon = { Text("▣") },
                    label = { Text("Fotoğraflar") }
                )
                NavigationBarItem(
                    selected = state.tab == GalleryTab.VIDEOS,
                    onClick = { vm.switchTab(GalleryTab.VIDEOS) },
                    icon = { Icon(Icons.Default.PlayArrow, null) },
                    label = { Text("Videolar") }
                )
            }
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (state.tab == GalleryTab.PHOTOS) "Fotoğraflar" else "Videolar",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = vm::reload) {
                    Icon(Icons.Default.Refresh, "Yenile")
                }
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
                        Button(onClick = vm::reload) { Text("Tekrar dene") }
                    }
                }

                else -> MediaGrid(
                    state = state,
                    onOpen = { viewerIndex = it },
                    onLoadMore = vm::loadNextPage
                )
            }
        }
    }
}

@Composable
private fun MediaGrid(
    state: GalleryUiState,
    onOpen: (Int) -> Unit,
    onLoadMore: () -> Unit
) {
    val gridState = rememberLazyGridState()

    LaunchedEffect(gridState, state.items.size, state.hasMore) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .map { last -> state.items.size - last }
            .distinctUntilChanged()
            .collect { remaining ->
                if (state.hasMore && state.items.isNotEmpty() && remaining <= 16) onLoadMore()
            }
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(96.dp),
        state = gridState,
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        itemsIndexed(
            items = state.items,
            key = { _, item -> "${item.isVideo}-${item.id}" }
        ) { index, item ->
            MediaTile(item = item, onClick = { onOpen(index) })
        }
        if (state.loading) {
            item(key = "loading") {
                Box(Modifier.size(96.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(28.dp))
                }
            }
        }
    }
}

@Composable
private fun MediaTile(item: GalleryMedia, onClick: () -> Unit) {
    Box(
        Modifier
            .size(120.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(Color(0xFF17233A))
            .clickable(onClick = onClick)
    ) {
        MediaBitmap(item, 360)?.let { bitmap ->
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
                modifier = Modifier.align(Alignment.Center).size(38.dp)
            )
        }
    }
}

@Composable
private fun MediaBitmap(item: GalleryMedia, edge: Int): Bitmap? {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, item.uri, edge) {
        value = withContext(Dispatchers.IO) {
            loadThumbnailCompat(context, item, edge)
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaViewer(
    items: List<GalleryMedia>,
    initialIndex: Int,
    onBack: () -> Unit,
    onNeedMore: () -> Unit
) {
    val pager = rememberPagerState(initialPage = initialIndex) { items.size }

    LaunchedEffect(pager.currentPage, items.size) {
        if (items.size - pager.currentPage <= 4) onNeedMore()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
            val item = items[page]
            if (item.isVideo) VideoPage(item) else PhotoPage(item)
        }
        IconButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopStart).padding(top = 28.dp, start = 8.dp)
        ) {
            Icon(Icons.Default.ArrowBack, "Geri", tint = Color.White)
        }
        Text(
            text = items.getOrNull(pager.currentPage)?.name.orEmpty(),
            color = Color.White,
            maxLines = 1,
            modifier = Modifier.align(Alignment.BottomCenter).padding(20.dp)
        )
    }
}

@Composable
private fun PhotoPage(item: GalleryMedia) {
    var scale by remember(item.id) { mutableFloatStateOf(1f) }
    var offsetX by remember(item.id) { mutableFloatStateOf(0f) }
    var offsetY by remember(item.id) { mutableFloatStateOf(0f) }
    val bitmap = MediaBitmap(item, 1800)

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (bitmap == null) {
            CircularProgressIndicator()
        } else {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = item.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(item.id) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val newScale = (scale * zoom).coerceIn(1f, 5f)
                            if (newScale == 1f) {
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
                                scale = 1f
                                offsetX = 0f
                                offsetY = 0f
                            }
                        )
                    }
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offsetX,
                        translationY = offsetY
                    )
            )
        }
    }
}

@Composable
private fun VideoPage(item: GalleryMedia) {
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
        modifier = Modifier.fillMaxSize()
    )
}
