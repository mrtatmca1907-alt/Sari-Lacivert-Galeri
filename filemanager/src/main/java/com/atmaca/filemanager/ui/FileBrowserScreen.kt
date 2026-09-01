package com.atmaca.filemanager.ui

import android.graphics.Bitmap
import android.widget.VideoView
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.atmaca.filemanager.core.FileEntry
import java.io.File

private enum class TargetAction { COPY, MOVE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileBrowserScreen(viewModel: FileManagerViewModel) {
    val state by viewModel.state.collectAsState()
    val thumbnailLoader = remember { ThumbnailLoader() }
    var targetAction by remember { mutableStateOf<TargetAction?>(null) }
    var previewFile by remember { mutableStateOf<File?>(null) }

    MaterialTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("ATMACA Dosya Yöneticisi")
                            Text(state.currentDirectory.absolutePath, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = viewModel::goUp) {
                            Icon(Icons.Default.ArrowUpward, contentDescription = "Üst klasör")
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.loadDirectory(state.currentDirectory) }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Yenile")
                        }
                    }
                )
            },
            bottomBar = {
                if (state.selected.isNotEmpty()) {
                    Surface(tonalElevation = 4.dp) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${state.selected.size} seçili")
                            IconButton(onClick = { targetAction = TargetAction.COPY }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Kopyala")
                            }
                            IconButton(onClick = { targetAction = TargetAction.MOVE }) {
                                Icon(Icons.Default.DriveFileMove, contentDescription = "Taşı")
                            }
                            IconButton(onClick = viewModel::deleteSelected) {
                                Icon(Icons.Default.Delete, contentDescription = "Sil")
                            }
                        }
                    }
                }
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(state.entries, key = { it.file.absolutePath }) { entry ->
                        FileRow(
                            entry = entry,
                            selected = entry.file.absolutePath in state.selected,
                            thumbnailLoader = thumbnailLoader,
                            onClick = {
                                if (state.selected.isNotEmpty()) viewModel.toggleSelection(entry.file.absolutePath)
                                else if (entry.isDirectory) viewModel.open(entry)
                                else if (isPreviewable(entry.file)) previewFile = entry.file
                            },
                            onLongClick = { viewModel.toggleSelection(entry.file.absolutePath) }
                        )
                    }
                }
                if (state.isLoading) CircularProgressIndicator(Modifier.align(Alignment.Center))
                if (state.activeOperationCount > 0) {
                    Text(
                        "Dosya işlemi çalışıyor… (${state.activeOperationCount})",
                        modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
                    )
                }
            }
        }

        state.message?.let { message ->
            LaunchedEffect(message) { /* state survives rotation; message intentionally remains visible in UI state */ }
        }

        targetAction?.let { action ->
            TargetFolderDialog(
                initialPath = state.currentDirectory.absolutePath,
                action = action,
                onDismiss = { targetAction = null },
                onConfirm = { path ->
                    val target = File(path)
                    if (action == TargetAction.COPY) viewModel.copySelected(target) else viewModel.moveSelected(target)
                    targetAction = null
                }
            )
        }

        previewFile?.let { file ->
            MediaPreviewDialog(file = file, thumbnailLoader = thumbnailLoader, onDismiss = { previewFile = null })
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileRow(
    entry: FileEntry,
    selected: Boolean,
    thumbnailLoader: ThumbnailLoader,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val thumb by produceState<Bitmap?>(initialValue = null, entry.file.absolutePath, entry.modifiedAt) {
        value = if (entry.isDirectory) null else thumbnailLoader.load(entry.file)
    }
    Surface(tonalElevation = if (selected) 4.dp else 0.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when {
                entry.isDirectory -> Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(52.dp))
                thumb != null -> Image(
                    bitmap = thumb!!.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(52.dp),
                    contentScale = ContentScale.Crop
                )
                else -> Icon(Icons.Default.InsertDriveFile, contentDescription = null, modifier = Modifier.size(52.dp))
            }
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(entry.file.name, maxLines = 1)
                Text(if (entry.isDirectory) "Klasör" else formatBytes(entry.sizeBytes), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun TargetFolderDialog(
    initialPath: String,
    action: TargetAction,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var path by remember(initialPath) { mutableStateOf(initialPath) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (action == TargetAction.MOVE) "Hedef klasöre taşı" else "Hedef klasöre kopyala") },
        text = {
            Column {
                Text("Klasör yolunu seç. Ek bir ‘Dizine ekle’ adımı yok.")
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = path, onValueChange = { path = it }, singleLine = true, label = { Text("Hedef klasör") })
            }
        },
        confirmButton = { Button(onClick = { onConfirm(path) }, enabled = path.isNotBlank()) { Text("Başlat") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } }
    )
}

@Composable
private fun MediaPreviewDialog(file: File, thumbnailLoader: ThumbnailLoader, onDismiss: () -> Unit) {
    val extension = file.extension.lowercase()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(file.name) },
        text = {
            if (extension in setOf("mp4", "mkv", "avi", "mov", "webm", "3gp", "m4v", "ts")) {
                AndroidView(
                    factory = { context -> VideoView(context).apply { setVideoPath(file.absolutePath); setOnPreparedListener { it.isLooping = false; start() } } },
                    modifier = Modifier.fillMaxWidth().height(360.dp)
                )
            } else {
                val bitmap by produceState<Bitmap?>(initialValue = null, file.absolutePath) { value = thumbnailLoader.load(file, 1400) }
                if (bitmap == null) CircularProgressIndicator()
                else Image(bitmap = bitmap!!.asImageBitmap(), contentDescription = file.name, modifier = Modifier.fillMaxWidth(), contentScale = ContentScale.Fit)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Kapat") } }
    )
}

private fun isPreviewable(file: File): Boolean = file.extension.lowercase() in setOf(
    "jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif", "avif",
    "mp4", "mkv", "avi", "mov", "webm", "3gp", "m4v", "ts"
)

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    bytes < 1024L * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
    else -> String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024))
}
