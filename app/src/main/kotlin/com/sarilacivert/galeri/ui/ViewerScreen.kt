package com.sarilacivert.galeri.ui

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.view.MotionEvent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoveToInbox
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.sarilacivert.galeri.data.BitmapLoader
import com.sarilacivert.galeri.data.GalleryPreferences
import com.sarilacivert.galeri.data.MediaItem
import com.sarilacivert.galeri.data.MediaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

@Composable
fun ViewerScreen(
    initialItems: List<MediaItem>,
    startIndex: Int,
    repo: MediaRepository,
    loader: BitmapLoader,
    prefs: GalleryPreferences,
    favorites: Set<String>,
    slideshowSeconds: Int,
    startSlideshow: Boolean,
    fromTrash: Boolean,
    onBack: () -> Unit,
    onChanged: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf(initialItems) }
    var index by remember { mutableIntStateOf(startIndex.coerceIn(0, (initialItems.size - 1).coerceAtLeast(0))) }
    var barsVisible by remember { mutableStateOf(true) }
    var slideshow by remember { mutableStateOf(startSlideshow) }
    var showInfo by remember { mutableStateOf(false) }
    var infoText by remember { mutableStateOf("") }
    var showRename by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }
    var pendingRename by remember { mutableStateOf<String?>(null) }
    var pendingMoveAfterCopy by remember { mutableStateOf(false) }
    var pendingFolderAction by remember { mutableStateOf<FolderAction?>(null) }

    if (items.isEmpty()) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val current = items[index.coerceIn(0, items.lastIndex)]
    var photoRotation by remember(current.uri) { mutableFloatStateOf(0f) }
    val isFavorite = current.uri.toString() in favorites

    fun closeCurrentAndBack() {
        onChanged()
        onBack()
    }

    val trashLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            Toast.makeText(context, if (fromTrash) "Geri yüklendi" else "Çöp kutusuna taşındı", Toast.LENGTH_SHORT).show()
            closeCurrentAndBack()
        }
    }
    val deleteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            Toast.makeText(context, "Kalıcı olarak silindi", Toast.LENGTH_SHORT).show()
            closeCurrentAndBack()
        }
    }
    val writeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val name = pendingRename
        if (result.resultCode == Activity.RESULT_OK && !name.isNullOrBlank()) {
            scope.launch {
                repo.rename(current, name).onSuccess {
                    Toast.makeText(context, "Yeniden adlandırıldı", Toast.LENGTH_SHORT).show()
                    pendingRename = null
                    onChanged()
                }.onFailure { Toast.makeText(context, it.message ?: "Ad değiştirilemedi", Toast.LENGTH_LONG).show() }
            }
        }
    }

    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { treeUri ->
        val action = pendingFolderAction
        pendingFolderAction = null
        if (treeUri != null && action != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            scope.launch {
                repo.copyToTree(current, treeUri).onSuccess {
                    if (action == FolderAction.COPY) {
                        Toast.makeText(context, "Kopyalandı", Toast.LENGTH_SHORT).show()
                    } else {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            repo.createTrashRequest(listOf(current), true)?.let { sender ->
                                pendingMoveAfterCopy = true
                                trashLauncher.launch(IntentSenderRequest.Builder(sender).build())
                            }
                        } else {
                            if (repo.deleteLegacy(current)) {
                                Toast.makeText(context, "Taşındı", Toast.LENGTH_SHORT).show()
                                closeCurrentAndBack()
                            }
                        }
                    }
                }.onFailure {
                    Toast.makeText(context, it.message ?: "Dosya işlemi başarısız", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    BackHandler(onBack = onBack)

    LaunchedEffect(slideshow, index, items.size, slideshowSeconds) {
        if (slideshow && !current.isVideo) {
            delay(slideshowSeconds * 1000L)
            val next = findNextImage(items, index)
            if (next >= 0) index = next else slideshow = false
        }
    }

    DisposableEffect(barsVisible) {
        val window = activity?.window
        val controller = if (window != null) WindowInsetsControllerCompat(window, window.decorView) else null
        if (barsVisible) {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        } else {
            controller?.hide(WindowInsetsCompat.Type.systemBars())
            controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        onDispose { }
    }

    Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black)) {
        if (current.isVideo) {
            VideoViewer(
                item = current,
                onTap = { barsVisible = !barsVisible },
                onPrevious = { if (index > 0) index-- },
                onNext = { if (index < items.lastIndex) index++ }
            )
        } else {
            ZoomableImage(
                loader = loader,
                uri = current.uri,
                rotationDegrees = photoRotation,
                onTap = { barsVisible = !barsVisible },
                onPrevious = { if (index > 0) index-- },
                onNext = { if (index < items.lastIndex) index++ }
            )
        }

        if (barsVisible) {
            Column(
                modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().background(Navy800.copy(alpha = 0.9f)).padding(top = 26.dp, start = 4.dp, end = 4.dp, bottom = 4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onBack) { Text("‹ Geri") }
                    Column(Modifier.weight(1f)) {
                        Text(current.name, color = TextPrimary, maxLines = 1)
                        Text("${index + 1}/${items.size} • ${current.albumName}", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
                    }
                    IconButton(onClick = {
                        scope.launch {
                            val now = prefs.toggleFavorite(current.uri.toString())
                            Toast.makeText(context, if (now) "Favorilere eklendi" else "Favoriden çıkarıldı", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Favori", tint = Yellow500)
                    }
                    IconButton(onClick = {
                        share(context, current)
                    }) { Icon(Icons.Default.Share, "Paylaş") }
                }
            }

            Row(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Navy800.copy(alpha = 0.92f)).padding(bottom = 18.dp, top = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!fromTrash) {
                    ViewerAction(Icons.Default.ContentCopy, "Kopyala") {
                        pendingFolderAction = FolderAction.COPY
                        folderLauncher.launch(null)
                    }
                    ViewerAction(Icons.Default.MoveToInbox, "Taşı") {
                        pendingFolderAction = FolderAction.MOVE
                        folderLauncher.launch(null)
                    }
                    ViewerAction(Icons.Default.Edit, "Ad") {
                        renameText = current.name
                        showRename = true
                    }
                    if (!current.isVideo) {
                        ViewerAction(if (slideshow) Icons.Default.Pause else Icons.Default.PlayArrow, "Slayt") { slideshow = !slideshow }
                    }
                    ViewerAction(Icons.Default.DeleteOutline, "Çöp") {
                        scope.launch {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                repo.createTrashRequest(listOf(current), true)?.let { trashLauncher.launch(IntentSenderRequest.Builder(it).build()) }
                            } else if (repo.deleteLegacy(current)) {
                                closeCurrentAndBack()
                            }
                        }
                    }
                } else {
                    ViewerAction(Icons.Default.RestoreFromTrash, "Geri Al") {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            repo.createTrashRequest(listOf(current), false)?.let { trashLauncher.launch(IntentSenderRequest.Builder(it).build()) }
                        }
                    }
                    ViewerAction(Icons.Default.DeleteForever, "Kalıcı Sil") {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            repo.createDeleteRequest(listOf(current))?.let { deleteLauncher.launch(IntentSenderRequest.Builder(it).build()) }
                        }
                    }
                }
                ViewerAction(Icons.Default.Info, "Bilgi") {
                    scope.launch {
                        infoText = repo.mediaInfo(current)
                        showInfo = true
                    }
                }
                ViewerAction(Icons.Default.RotateRight, if (current.isVideo) "Ekran" else "Döndür") {
                    if (current.isVideo) {
                        activity?.requestedOrientation = if (activity?.resources?.configuration?.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
                            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        } else ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    } else {
                        photoRotation = (photoRotation + 90f) % 360f
                    }
                }
            }
        }
    }

    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            title = { Text("Medya Bilgisi") },
            text = { Text(infoText) },
            confirmButton = { TextButton(onClick = { showInfo = false }) { Text("Tamam") } }
        )
    }

    if (showRename) {
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text("Yeniden adlandır") },
            text = {
                OutlinedTextField(value = renameText, onValueChange = { renameText = it }, singleLine = true)
            },
            confirmButton = {
                Button(onClick = {
                    showRename = false
                    val newName = renameText.trim()
                    if (newName.isBlank()) return@Button
                    scope.launch {
                        val direct = repo.rename(current, newName)
                        if (direct.isSuccess) {
                            Toast.makeText(context, "Yeniden adlandırıldı", Toast.LENGTH_SHORT).show()
                            onChanged()
                        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            pendingRename = newName
                            repo.createWriteRequest(listOf(current))?.let { writeLauncher.launch(IntentSenderRequest.Builder(it).build()) }
                        } else {
                            Toast.makeText(context, direct.exceptionOrNull()?.message ?: "Ad değiştirilemedi", Toast.LENGTH_LONG).show()
                        }
                    }
                }) { Text("Kaydet") }
            },
            dismissButton = { TextButton(onClick = { showRename = false }) { Text("Vazgeç") } }
        )
    }
}

private enum class FolderAction { COPY, MOVE }

@Composable
private fun ViewerAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick) { Icon(icon, label, tint = TextPrimary) }
        Text(label, color = TextSecondary, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun VideoViewer(
    item: MediaItem,
    onTap: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    val context = LocalContext.current
    val player = remember(item.uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(androidx.media3.common.MediaItem.fromUri(item.uri))
            prepare()
        }
    }
    val positionPrefs = remember { context.getSharedPreferences("video_positions_v2", android.content.Context.MODE_PRIVATE) }

    LaunchedEffect(player, item.uri) {
        val saved = positionPrefs.getLong(item.uri.toString(), 0L)
        if (saved > 1000L) player.seekTo(saved)
        player.playWhenReady = true
    }

    DisposableEffect(player, item.uri) {
        onDispose {
            positionPrefs.edit().putLong(item.uri.toString(), player.currentPosition).apply()
            player.release()
        }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                useController = true
                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                setOnClickListener { onTap() }

                var downX = 0f
                var downY = 0f
                var downTime = 0L
                setOnTouchListener { _, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            downX = event.x
                            downY = event.y
                            downTime = event.eventTime
                            false
                        }
                        MotionEvent.ACTION_UP -> {
                            val dx = event.x - downX
                            val dy = event.y - downY
                            val elapsed = event.eventTime - downTime
                            val horizontalSwipe = abs(dx) > 170f && abs(dx) > abs(dy) * 1.25f && elapsed < 1400L
                            if (horizontalSwipe) {
                                if (dx > 0f) onPrevious() else onNext()
                                true
                            } else {
                                false
                            }
                        }
                        else -> false
                    }
                }
            }
        },
        update = { it.player = player },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
private fun ZoomableImage(
    loader: BitmapLoader,
    uri: Uri,
    rotationDegrees: Float,
    onTap: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    val bitmap by produceState<Bitmap?>(null, uri) { value = loader.full(uri) }
    var scale by remember(uri) { mutableFloatStateOf(1f) }
    var gestureRotation by remember(uri) { mutableFloatStateOf(0f) }
    var offset by remember(uri) { mutableStateOf(Offset.Zero) }
    var dragX by remember(uri) { mutableFloatStateOf(0f) }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (bitmap == null) {
            LoadingState("Fotoğraf açılıyor…")
        } else {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y,
                        rotationZ = rotationDegrees + gestureRotation
                    )
                    .pointerInput(uri) {
                        detectTapGestures(
                            onTap = { onTap() },
                            onDoubleTap = {
                                if (scale > 1.05f) {
                                    scale = 1f
                                    offset = Offset.Zero
                                } else scale = 2.5f
                            }
                        )
                    }
                    .pointerInput(uri) {
                        detectTransformGestures { _, pan, zoom, rotate ->
                            val newScale = (scale * zoom).coerceIn(1f, 6f)
                            scale = newScale
                            gestureRotation += rotate
                            offset = if (newScale <= 1.01f) Offset.Zero else offset + pan
                        }
                    }
                    .pointerInput(uri, scale) {
                        detectHorizontalDragGestures(
                            onDragStart = { dragX = 0f },
                            onHorizontalDrag = { _, delta -> if (scale <= 1.02f) dragX += delta },
                            onDragEnd = {
                                if (scale <= 1.02f && abs(dragX) > 140f) {
                                    if (dragX > 0) onPrevious() else onNext()
                                }
                                dragX = 0f
                            }
                        )
                    }
            )
        }
    }
}

private fun share(context: android.content.Context, item: MediaItem) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = item.mimeType.ifBlank { if (item.isVideo) "video/*" else "image/*" }
        putExtra(Intent.EXTRA_STREAM, item.uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Paylaş"))
}

private fun findNextImage(items: List<MediaItem>, current: Int): Int {
    if (items.isEmpty()) return -1
    for (step in 1..items.size) {
        val i = (current + step) % items.size
        if (!items[i].isVideo) return i
    }
    return -1
}
