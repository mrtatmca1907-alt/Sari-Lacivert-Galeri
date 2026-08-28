package com.sarilacivert.galeri.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.sarilacivert.galeri.data.Album
import com.sarilacivert.galeri.data.AlbumSort
import com.sarilacivert.galeri.data.BitmapLoader
import com.sarilacivert.galeri.data.MediaItem
import com.sarilacivert.galeri.data.MediaRepository
import com.sarilacivert.galeri.data.MediaSort

private sealed interface AtmacaScreen {
    data object Albums : AtmacaScreen
    data class AlbumView(val album: Album) : AtmacaScreen
    data class Viewer(val item: MediaItem, val album: Album) : AtmacaScreen
}

@Composable
fun AtmacaModernApp() {
    val context = LocalContext.current
    val repo = remember { MediaRepository(context.applicationContext) }
    val loader = remember { BitmapLoader(context.applicationContext) }
    var screen by remember { mutableStateOf<AtmacaScreen>(AtmacaScreen.Albums) }
    var refresh by remember { mutableIntStateOf(0) }
    var granted by remember { mutableStateOf(hasAtmacaMediaPermission(context)) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        granted = hasAtmacaMediaPermission(context)
        if (granted) refresh++
    }

    LaunchedEffect(Unit) {
        if (!granted) permissionLauncher.launch(atmacaMediaPermissions())
    }

    if (!granted) {
        Box(Modifier.fillMaxSize().background(Navy900), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("ATMACA", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = Yellow500)
                Text("Fotoğraf ve videoları göstermek için medya izni gerekiyor.", color = TextSecondary)
                Button(onClick = { permissionLauncher.launch(atmacaMediaPermissions()) }) { Text("İzin ver") }
            }
        }
        return
    }

    when (val current = screen) {
        AtmacaScreen.Albums -> AtmacaAlbums(
            repo = repo,
            loader = loader,
            refresh = refresh,
            onRefresh = { refresh++ },
            onOpen = { screen = AtmacaScreen.AlbumView(it) }
        )
        is AtmacaScreen.AlbumView -> AtmacaAlbum(
            album = current.album,
            repo = repo,
            loader = loader,
            refresh = refresh,
            onBack = { screen = AtmacaScreen.Albums },
            onOpen = { screen = AtmacaScreen.Viewer(it, current.album) }
        )
        is AtmacaScreen.Viewer -> AtmacaViewer(
            item = current.item,
            loader = loader,
            onBack = { screen = AtmacaScreen.AlbumView(current.album) }
        )
    }
}

@Composable
private fun AtmacaAlbums(
    repo: MediaRepository,
    loader: BitmapLoader,
    refresh: Int,
    onRefresh: () -> Unit,
    onOpen: (Album) -> Unit
) {
    var albums by remember { mutableStateOf<List<Album>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(refresh) {
        loading = true
        albums = repo.loadAlbums(showImages = true, showVideos = true, sort = AlbumSort.NEWEST)
        loading = false
    }

    Column(Modifier.fillMaxSize().background(Navy900)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("ATMACA", color = Yellow500, fontWeight = FontWeight.Black, style = MaterialTheme.typography.headlineSmall)
                Text("${albums.size} klasör", color = TextSecondary, style = MaterialTheme.typography.labelMedium)
            }
            IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, contentDescription = "Yenile", tint = Yellow500) }
        }

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Yellow500) }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(albums, key = { it.path }) { album ->
                    AlbumTile(album, loader, onOpen)
                }
            }
        }
    }
}

@Composable
private fun AlbumTile(album: Album, loader: BitmapLoader, onOpen: (Album) -> Unit) {
    var bitmap by remember(album.coverUri) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(album.coverUri) { bitmap = loader.thumbnail(album.coverUri, 360) }

    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onOpen(album) },
        color = Navy800,
        shape = RoundedCornerShape(16.dp)
    ) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(1.18f).background(Navy700)) {
                bitmap?.let {
                    Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
                if (bitmap == null) Icon(Icons.Default.Folder, null, Modifier.align(Alignment.Center).size(42.dp), tint = Yellow500)
            }
            Column(Modifier.padding(10.dp)) {
                Text(album.name, color = TextPrimary, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${album.count} öğe", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun AtmacaAlbum(
    album: Album,
    repo: MediaRepository,
    loader: BitmapLoader,
    refresh: Int,
    onBack: () -> Unit,
    onOpen: (MediaItem) -> Unit
) {
    var media by remember(album.path) { mutableStateOf<List<MediaItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(album.path, refresh) {
        loading = true
        media = repo.loadAlbum(album.path, showImages = true, showVideos = true, sort = MediaSort.NEWEST)
        loading = false
    }
    BackHandler(onBack = onBack)

    Column(Modifier.fillMaxSize().background(Navy900)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Geri", tint = Yellow500) }
            Column(Modifier.weight(1f)) {
                Text(album.name, color = TextPrimary, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${media.size} öğe", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
            }
        }

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Yellow500) }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(media, key = { it.uri.toString() }) { item ->
                    MediaTile(item, loader) { onOpen(item) }
                }
            }
        }
    }
}

@Composable
private fun MediaTile(item: MediaItem, loader: BitmapLoader, onOpen: () -> Unit) {
    var bitmap by remember(item.uri) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(item.uri) { bitmap = loader.thumbnail(item.uri, 384) }

    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .background(Navy800)
            .clickable(onClick = onOpen)
    ) {
        bitmap?.let {
            Image(it.asImageBitmap(), item.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        if (item.isVideo) {
            Box(
                Modifier.align(Alignment.BottomEnd).padding(6.dp).clip(RoundedCornerShape(20.dp)).background(Navy900.copy(alpha = 0.82f)).padding(5.dp)
            ) {
                Icon(Icons.Default.PlayArrow, "Video", tint = Yellow500, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun AtmacaViewer(item: MediaItem, loader: BitmapLoader, onBack: () -> Unit) {
    var bitmap by remember(item.uri) { mutableStateOf<android.graphics.Bitmap?>(null) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    val transformState = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 5f)
        if (scale == 1f) {
            offsetX = 0f
            offsetY = 0f
        } else {
            offsetX += pan.x
            offsetY += pan.y
        }
    }

    LaunchedEffect(item.uri) { bitmap = loader.full(item.uri, 4096) }
    BackHandler(onBack = onBack)

    Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black)) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = item.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offsetX, translationY = offsetY)
                    .transformable(transformState)
            )
        } ?: CircularProgressIndicator(Modifier.align(Alignment.Center), color = Yellow500)

        IconButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopStart).padding(10.dp).background(Navy900.copy(alpha = 0.72f), RoundedCornerShape(24.dp))
        ) {
            Icon(Icons.Default.ArrowBack, "Geri", tint = Yellow500)
        }
    }
}

private fun hasAtmacaMediaPermission(context: android.content.Context): Boolean {
    return atmacaMediaPermissions().all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
}

private fun atmacaMediaPermissions(): Array<String> {
    return if (Build.VERSION.SDK_INT >= 33) {
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
}
