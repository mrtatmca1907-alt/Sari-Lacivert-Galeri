package com.atmaca.photo100

import android.Manifest
import android.app.Activity
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.IntentSender
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Size
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val BATCH_SIZE = 100
private const val PREFS = "photo100_state"
private const val KEY_CURSOR = "cursor"
private const val KEY_IDS = "batch_ids"
private const val KEY_GROUP = "group"

data class PhotoItem(val id: Long, val uri: Uri, val name: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
                Photo100App()
            }
        }
    }
}

private class ProgressStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    val cursor: Long get() = prefs.getLong(KEY_CURSOR, Long.MAX_VALUE)
    val group: Int get() = prefs.getInt(KEY_GROUP, 1)

    fun currentIds(): List<Long> = prefs.getString(KEY_IDS, "")
        .orEmpty()
        .split(',')
        .mapNotNull { it.toLongOrNull() }

    fun saveBatch(ids: List<Long>) {
        prefs.edit().putString(KEY_IDS, ids.joinToString(",")).apply()
    }

    fun nextBatch() {
        val ids = currentIds()
        if (ids.isEmpty()) return
        prefs.edit()
            .putLong(KEY_CURSOR, BatchProgress.nextCursor(ids, cursor))
            .putString(KEY_IDS, "")
            .putInt(KEY_GROUP, group + 1)
            .apply()
    }
}

private class PhotoRepo(private val resolver: ContentResolver) {
    private val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

    suspend fun loadNext(cursor: Long): List<PhotoItem> = withContext(Dispatchers.IO) {
        val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME)
        val selection = if (cursor == Long.MAX_VALUE) null else "${MediaStore.Images.Media._ID} < ?"
        val args = if (selection == null) null else arrayOf(cursor.toString())
        val out = ArrayList<PhotoItem>(BATCH_SIZE)

        resolver.query(
            collection,
            projection,
            selection,
            args,
            "${MediaStore.Images.Media._ID} DESC"
        )?.use { c ->
            val idIx = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameIx = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            while (c.moveToNext() && out.size < BATCH_SIZE) {
                val id = c.getLong(idIx)
                out += PhotoItem(id, ContentUris.withAppendedId(collection, id), c.getString(nameIx).orEmpty())
            }
        }
        out
    }

    suspend fun loadSaved(ids: List<Long>): List<PhotoItem> = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext emptyList()
        val placeholders = ids.joinToString(",") { "?" }
        val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME)
        val found = ArrayList<PhotoItem>()
        resolver.query(
            collection,
            projection,
            "${MediaStore.Images.Media._ID} IN ($placeholders)",
            ids.map(Long::toString).toTypedArray(),
            null
        )?.use { c ->
            val idIx = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameIx = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            while (c.moveToNext()) {
                val id = c.getLong(idIx)
                found += PhotoItem(id, ContentUris.withAppendedId(collection, id), c.getString(nameIx).orEmpty())
            }
        }
        val order = ids.withIndex().associate { it.value to it.index }
        found.sortedBy { order[it.id] ?: Int.MAX_VALUE }
    }
}

@Composable
private fun Photo100App() {
    val context = LocalContext.current
    val activity = context as Activity
    val store = remember { ProgressStore(context) }
    val repo = remember { PhotoRepo(context.contentResolver) }
    val photos = remember { mutableStateListOf<PhotoItem>() }
    val selected = remember { mutableStateListOf<Long>() }

    var permitted by remember { mutableStateOf(hasImagePermission(context)) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var refresh by remember { mutableIntStateOf(0) }
    var groupNo by remember { mutableIntStateOf(store.group) }
    var hasSavedBatch by remember { mutableStateOf(store.currentIds().isNotEmpty()) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permitted = granted
        if (!granted) message = "Fotoğrafları göstermek için izin gerekli."
    }

    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            selected.clear()
            refresh++
            message = "Silindi. Hazırsan DEVAM'a bas."
        } else {
            message = "Silme iptal edildi."
        }
    }

    LaunchedEffect(permitted, refresh) {
        if (!permitted) return@LaunchedEffect
        loading = true
        try {
            val saved = store.currentIds()
            hasSavedBatch = saved.isNotEmpty()
            val loaded = if (saved.isNotEmpty()) {
                repo.loadSaved(saved)
            } else {
                repo.loadNext(store.cursor).also { batch ->
                    if (batch.isNotEmpty()) {
                        store.saveBatch(batch.map { it.id })
                        hasSavedBatch = true
                    }
                }
            }
            photos.clear()
            photos.addAll(loaded)
            groupNo = store.group
            if (loaded.isEmpty() && !hasSavedBatch) message = "Gösterilecek fotoğraf kalmadı."
        } finally {
            loading = false
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("100'lü Foto Ayıklama", style = MaterialTheme.typography.headlineSmall)
            Text("Grup $groupNo • Gösterilen ${photos.size}/100 • Seçili ${selected.size}")

            if (!permitted) {
                Button(onClick = { permissionLauncher.launch(requiredPermission()) }) {
                    Text("Fotoğraf izni ver")
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = {
                        if (photos.isNotEmpty() && selected.size == photos.size) selected.clear()
                        else {
                            selected.clear()
                            selected.addAll(photos.map { it.id })
                        }
                    }) {
                        Text(if (photos.isNotEmpty() && selected.size == photos.size) "Seçimi kaldır" else "Hepsini seç")
                    }

                    Button(
                        enabled = selected.isNotEmpty(),
                        onClick = {
                            val uris = photos.filter { it.id in selected }.map { it.uri }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                runCatching {
                                    val sender: IntentSender = MediaStore.createDeleteRequest(
                                        context.contentResolver,
                                        uris
                                    ).intentSender
                                    deleteLauncher.launch(IntentSenderRequest.Builder(sender).build())
                                }.onFailure { message = "Silme başlatılamadı: ${it.message.orEmpty()}" }
                            } else {
                                uris.forEach { context.contentResolver.delete(it, null, null) }
                                selected.clear()
                                refresh++
                                message = "Silindi. Hazırsan DEVAM'a bas."
                            }
                        }
                    ) {
                        Text("Seçilenleri sil")
                    }
                }

                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = hasSavedBatch && !loading,
                    onClick = {
                        store.nextBatch()
                        photos.clear()
                        selected.clear()
                        hasSavedBatch = false
                        groupNo = store.group
                        message = "Sonraki 100 yükleniyor…"
                        refresh++
                    }
                ) {
                    Text("DEVAM — SONRAKİ 100")
                }
            }

            if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodySmall)

            Box(modifier = Modifier.fillMaxSize()) {
                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        items(photos, key = { it.id }) { photo ->
                            PhotoCell(
                                photo = photo,
                                selected = photo.id in selected,
                                onClick = {
                                    if (photo.id in selected) selected.remove(photo.id)
                                    else selected.add(photo.id)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoCell(photo: PhotoItem, selected: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    var bitmap by remember(photo.id) { mutableStateOf<android.graphics.Bitmap?>(null) }

    LaunchedEffect(photo.id) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    context.contentResolver.loadThumbnail(photo.uri, Size(220, 220), null)
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Thumbnails.getThumbnail(
                        context.contentResolver,
                        photo.id,
                        MediaStore.Images.Thumbnails.MINI_KIND,
                        null
                    )
                }
            }.getOrNull()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(5.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(
                if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(5.dp))
                else Modifier
            )
            .clickable(onClick = onClick)
    ) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = photo.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        if (selected) {
            Text(
                "✓",
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(20.dp))
                    .padding(horizontal = 7.dp, vertical = 2.dp)
            )
        }
    }
}

private fun requiredPermission(): String =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_IMAGES
    else Manifest.permission.READ_EXTERNAL_STORAGE

private fun hasImagePermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, requiredPermission()) == PackageManager.PERMISSION_GRANTED
