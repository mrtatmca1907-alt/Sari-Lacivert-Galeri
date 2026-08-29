package com.sarilacivert.galeri.files

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.webkit.MimeTypeMap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val Navy = Color(0xFF071A3D)
private val Navy2 = Color(0xFF0D2858)
private val Yellow = Color(0xFFFFD600)
private val SoftText = Color(0xFFB8C3D9)

data class FastFileEntry(
    val file: File,
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val modified: Long
)

@Composable
fun FastFileManagerApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var accessRefresh by remember { mutableIntStateOf(0) }
    var hasAccess by remember(accessRefresh) { mutableStateOf(hasFileAccess(context)) }

    val legacyPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        hasAccess = hasFileAccess(context)
        accessRefresh++
    }

    val allFilesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        hasAccess = hasFileAccess(context)
        accessRefresh++
    }

    LaunchedEffect(Unit) {
        if (!hasAccess && Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            legacyPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    if (!hasAccess) {
        StorageAccessScreen {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                allFilesLauncher.launch(intent)
            } else {
                legacyPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
        return
    }

    val root = remember { Environment.getExternalStorageDirectory().canonicalFile }
    var current by remember { mutableStateOf(root) }
    var entries by remember { mutableStateOf<List<FastFileEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var refresh by remember { mutableIntStateOf(0) }
    var pendingDelete by remember { mutableStateOf<File?>(null) }

    LaunchedEffect(current.path, refresh) {
        loading = true
        entries = withContext(Dispatchers.IO) { listDirectory(current) }
        loading = false
    }

    val goParent = {
        val parentPath = FileBrowserPolicy.parentPath(current.path, root.path)
        current = File(parentPath)
    }

    BackHandler(enabled = current.path != root.path) { goParent() }

    Column(Modifier.fillMaxSize().background(Navy)) {
        Row(
            modifier = Modifier.fillMaxWidth().background(Navy2).padding(horizontal = 6.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = goParent, enabled = current.path != root.path) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri", tint = if (current.path != root.path) Yellow else SoftText)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (current.path == root.path) "Dahili Depolama" else current.name,
                    color = Yellow,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = current.path.removePrefix(root.path).ifBlank { "/" },
                    color = SoftText,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = { refresh++ }) {
                Icon(Icons.Default.Refresh, "Yenile", tint = Yellow)
            }
        }

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Yellow)
            }
        } else if (entries.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Bu klasör boş", color = SoftText)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(entries, key = { it.file.path }) { entry ->
                    FileRow(
                        entry = entry,
                        onClick = {
                            if (entry.isDirectory) current = entry.file
                            else openFile(context, entry.file)
                        },
                        onDelete = { pendingDelete = entry.file }
                    )
                    HorizontalDivider(color = Navy2)
                }
            }
        }
    }

    pendingDelete?.let { file ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Silinsin mi?") },
            text = { Text(file.name) },
            confirmButton = {
                TextButton(onClick = {
                    val target = pendingDelete
                    pendingDelete = null
                    if (target != null) {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                if (target.isDirectory) target.deleteRecursively() else target.delete()
                            }
                            refresh++
                        }
                    }
                }) { Text("Sil") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Vazgeç") } }
        )
    }
}

@Composable
private fun StorageAccessScreen(onGrant: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Navy), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(Icons.Default.Folder, null, tint = Yellow, modifier = Modifier.size(64.dp))
            Text("ATMACA Dosyalar", color = Yellow, fontWeight = FontWeight.Black, style = MaterialTheme.typography.headlineSmall)
            Text("Dosya ve klasörleri hızlı göstermek için depolama erişimini aç.", color = SoftText)
            Button(onClick = onGrant) { Text("Depolama erişimini aç") }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileRow(entry: FastFileEntry, onClick: () -> Unit, onDelete: () -> Unit) {
    val icon = when {
        entry.isDirectory -> Icons.Default.Folder
        FileBrowserPolicy.isImage(entry.name) -> Icons.Default.Image
        FileBrowserPolicy.isVideo(entry.name) -> Icons.Default.PlayCircle
        else -> Icons.Default.Description
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onDelete)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = if (entry.isDirectory) Yellow else SoftText, modifier = Modifier.size(34.dp))
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(entry.name, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (entry.isDirectory) "Klasör" else FileBrowserPolicy.formatBytes(entry.size),
                color = SoftText,
                style = MaterialTheme.typography.labelSmall
            )
        }
        Icon(Icons.Default.MoreVert, "Basılı tut: sil", tint = SoftText, modifier = Modifier.size(20.dp))
    }
}

private fun listDirectory(directory: File): List<FastFileEntry> {
    val files = try { directory.listFiles()?.toList().orEmpty() } catch (_: SecurityException) { emptyList() }
    return files
        .asSequence()
        .filter { it.exists() && !it.isHidden }
        .map {
            FastFileEntry(
                file = it,
                name = it.name.ifBlank { it.path },
                isDirectory = it.isDirectory,
                size = if (it.isFile) it.length() else 0L,
                modified = it.lastModified()
            )
        }
        .sortedWith { a, b ->
            FileEntryPolicy.compare(
                FileEntryPolicy.Entry(a.name, a.isDirectory),
                FileEntryPolicy.Entry(b.name, b.isDirectory)
            )
        }
        .toList()
}

private fun hasFileAccess(context: android.content.Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }
}

private fun openFile(context: android.content.Context, file: File) {
    val extension = FileEntryPolicy.extensionOf(file.name)
    val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "*/*"
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mime)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        val fallback = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(fallback, file.name))
    }
}
