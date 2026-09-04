package com.atmaca.gallery

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch

@Composable
fun InternalToolAlbumPicker(
    tool: AtmacaToolPage,
    repository: MediaStoreRepository,
    onDismiss: () -> Unit,
    onSelected: (List<Uri>) -> Unit
) {
    val scope = rememberCoroutineScope()
    var albums by remember { mutableStateOf<List<GalleryAlbum>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var opening by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableStateOf(0) }

    LaunchedEffect(refreshKey) {
        loading = true
        albums = runCatching { repository.loadAlbumsOemSafe() }.getOrDefault(emptyList())
        loading = false
    }

    Dialog(
        onDismissRequest = { if (opening == null) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxWidth().fillMaxHeight(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Galeriden klasör seç", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = { refreshKey++ }, enabled = opening == null && !loading) { Text("Yenile") }
                    TextButton(onClick = onDismiss, enabled = opening == null) { Text("Kapat") }
                }
                if (loading) {
                    Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center) {
                        CircularProgressIndicator()
                    }
                } else if (albums.isEmpty()) {
                    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Albüm bulunamadı. Medya iznini kontrol edip Yenile'ye dokun.")
                        Button(onClick = { refreshKey++ }, modifier = Modifier.padding(top = 12.dp)) { Text("Yenile") }
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                        items(albums, key = { "${it.relativePath}-${it.bucketId}-${it.name}" }) { album ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = opening == null) {
                                        opening = album.name
                                        scope.launch {
                                            val uris = runCatching {
                                                repository.loadAllInAlbumOemSafe(album)
                                                    .filter { item -> toolAcceptsDocument(tool, item.mimeType, item.name) }
                                                    .map { it.uri }
                                            }.getOrDefault(emptyList())
                                            opening = null
                                            onSelected(uris)
                                        }
                                    }
                                    .padding(horizontal = 8.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(album.name, style = MaterialTheme.typography.bodyLarge)
                                    Text("${album.count} öğe", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (opening == album.name) CircularProgressIndicator()
                            }
                        }
                    }
                }
            }
        }
    }
}
