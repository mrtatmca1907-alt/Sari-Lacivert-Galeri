package com.atmaca.gallery

import android.net.Uri
import android.os.Environment
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.coroutineContext

@Composable
fun DirectFolderPicker(
    tool: AtmacaToolPage,
    onDismiss: () -> Unit,
    onSelected: (List<Uri>) -> Unit
) {
    val root = remember { Environment.getExternalStorageDirectory().canonicalFile }
    var current by remember { mutableStateOf(root) }
    var folders by remember { mutableStateOf<List<File>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var selecting by remember { mutableStateOf(false) }
    var scanned by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(current) {
        loading = true
        folders = withContext(Dispatchers.IO) {
            current.listFiles()
                ?.asSequence()
                ?.filter { it.isDirectory && it.canRead() && !it.name.startsWith(".") }
                ?.sortedBy { it.name.lowercase() }
                ?.toList()
                .orEmpty()
        }
        loading = false
    }

    Dialog(
        onDismissRequest = { if (!selecting) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(Modifier.fillMaxWidth().fillMaxHeight(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Ana bellek", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(
                        onClick = {
                            val parent = current.parentFile
                            if (parent != null && current != root && parent.path.startsWith(root.path)) current = parent
                        },
                        enabled = current != root && !selecting
                    ) { Text("Yukarı") }
                    TextButton(onClick = onDismiss, enabled = !selecting) { Text("Kapat") }
                }
                Text(
                    current.path.removePrefix(root.path).ifBlank { "/" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                Button(
                    onClick = {
                        selecting = true
                        scanned = 0
                        scope.launch {
                            val result = collectDirectToolUris(current, tool) { scanned = it }
                            selecting = false
                            onSelected(result)
                        }
                    },
                    enabled = !selecting,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (selecting) "Taranıyor: $scanned" else "Bu klasörü seç (alt klasörler dahil)") }

                if (loading || selecting) {
                    Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.Center) {
                        CircularProgressIndicator()
                    }
                }
                LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                    items(folders, key = { it.absolutePath }) { folder ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable(enabled = !selecting) { current = folder }
                                .padding(horizontal = 8.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("📁", modifier = Modifier.padding(end = 10.dp))
                            Text(folder.name, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
    }
}

suspend fun collectDirectToolUris(
    folder: File,
    tool: AtmacaToolPage,
    onScanned: (Int) -> Unit = {}
): List<Uri> = withContext(Dispatchers.IO) {
    val accepted = ArrayList<Uri>()
    var scanned = 0
    folder.walkTopDown()
        .onEnter { it.canRead() && !it.name.startsWith(".") }
        .filter { it.isFile }
        .forEach { file ->
            coroutineContext.ensureActive()
            scanned++
            if (toolAcceptsDocument(tool, null, file.name)) accepted += Uri.fromFile(file)
            if (scanned % 100 == 0) onScanned(scanned)
        }
    onScanned(scanned)
    accepted
}
