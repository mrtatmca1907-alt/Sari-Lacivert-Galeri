package com.sarilacivert.galeri.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FindReplace
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.sarilacivert.galeri.data.BitmapLoader
import com.sarilacivert.galeri.data.DuplicateGroup
import com.sarilacivert.galeri.data.DuplicateKind
import com.sarilacivert.galeri.data.DuplicateResultStore
import com.sarilacivert.galeri.data.MediaItem
import com.sarilacivert.galeri.worker.DuplicateScanWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuplicatesScreen(
    loader: BitmapLoader,
    duplicateDistance: Int,
    favorites: Set<String>,
    onOpen: (List<MediaItem>, Int) -> Unit,
    bottomScreen: Screen,
    onBottomNavigate: (Screen) -> Unit
) {
    val context = LocalContext.current
    val workManager = remember { WorkManager.getInstance(context.applicationContext) }
    var groups by remember { mutableStateOf<List<DuplicateGroup>>(emptyList()) }
    var progress by remember { mutableIntStateOf(0) }
    var stage by remember { mutableStateOf("Hazır") }
    var running by remember { mutableStateOf(false) }
    var refreshToken by remember { mutableIntStateOf(0) }

    suspend fun refreshResults() {
        groups = DuplicateResultStore.load(context.applicationContext)
    }

    LaunchedEffect(refreshToken) {
        refreshResults()
        while (true) {
            val infos = withContext(Dispatchers.IO) {
                runCatching { workManager.getWorkInfosForUniqueWork(DuplicateScanWorker.UNIQUE_WORK).get() }.getOrDefault(emptyList())
            }
            val info = infos.firstOrNull()
            running = info?.state == WorkInfo.State.RUNNING || info?.state == WorkInfo.State.ENQUEUED
            if (info != null) {
                progress = info.progress.getInt(DuplicateScanWorker.KEY_PROGRESS, progress)
                stage = info.progress.getString(DuplicateScanWorker.KEY_STAGE) ?: when (info.state) {
                    WorkInfo.State.SUCCEEDED -> "Tamamlandı"
                    WorkInfo.State.FAILED -> "Tarama hata verdi"
                    WorkInfo.State.CANCELLED -> "Tarama iptal edildi"
                    else -> stage
                }
                if (info.state == WorkInfo.State.SUCCEEDED) refreshResults()
            }
            delay(if (running) 1000 else 3000)
        }
    }

    fun startScan() {
        val request = OneTimeWorkRequest.Builder(DuplicateScanWorker::class.java).build()
        workManager.enqueueUniqueWork(DuplicateScanWorker.UNIQUE_WORK, ExistingWorkPolicy.REPLACE, request)
        progress = 0
        stage = "Tarama başlatılıyor"
        running = true
        refreshToken++
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Çift ve Benzer")
                        Text("Silme otomatik yapılmaz", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    }
                },
                actions = {
                    Button(onClick = ::startScan, enabled = !running) {
                        Icon(if (groups.isEmpty()) Icons.Default.FindReplace else Icons.Default.Refresh, null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (groups.isEmpty()) "Tara" else "Yeniden tara")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Navy800)
            )
        },
        bottomBar = { GalleryBottomBarForDuplicates(bottomScreen, onBottomNavigate) },
        containerColor = Navy900
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (running || progress in 1..99) {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Text(stage, color = TextPrimary)
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
                    Text("%$progress", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
                }
            }

            if (groups.isEmpty() && !running) {
                EmptyState(
                    "Henüz sonuç yok",
                    "Tarama aynı dosyaları SHA-256 ile, benzer fotoğrafları görüntü karması ile karşılaştırır. Hassasiyet: $duplicateDistance"
                )
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(groups) { group ->
                        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                            Text(
                                if (group.kind == DuplicateKind.EXACT) "Birebir aynı • ${group.items.size} dosya" else "Benzer fotoğraflar • ${group.items.size} dosya",
                                color = if (group.kind == DuplicateKind.EXACT) Yellow500 else TextPrimary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                            LazyRow(Modifier.fillMaxWidth()) {
                                items(group.items) { media ->
                                    MediaTile(
                                        item = media,
                                        loader = loader,
                                        favorite = media.uri.toString() in favorites,
                                        modifier = Modifier.width(126.dp),
                                        onClick = { onOpen(group.items, group.items.indexOf(media)) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GalleryBottomBarForDuplicates(current: Screen, onNavigate: (Screen) -> Unit) {
    androidx.compose.material3.NavigationBar(containerColor = Navy800) {
        val entries = listOf(
            Triple(Screen.Albums, androidx.compose.material.icons.Icons.Default.Folder, "Albümler"),
            Triple(Screen.Favorites, androidx.compose.material.icons.Icons.Default.Favorite, "Favori"),
            Triple(Screen.Duplicates, androidx.compose.material.icons.Icons.Default.GridView, "Benzer"),
            Triple(Screen.Trash, androidx.compose.material.icons.Icons.Default.DeleteOutline, "Çöp"),
            Triple(Screen.Settings, androidx.compose.material.icons.Icons.Default.Settings, "Ayarlar")
        )
        entries.forEach { (screen, icon, label) ->
            androidx.compose.material3.NavigationBarItem(
                selected = current == screen,
                onClick = { onNavigate(screen) },
                icon = { Icon(icon, null) },
                label = { Text(label) }
            )
        }
    }
}
