package com.sarilacivert.galeri.ui

import android.Manifest
import android.app.Activity
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.IntentSender
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
private const val PREFS_NAME = "photo_100_state"
private const val KEY_CURSOR = "cursor"
private const val KEY_BATCH_IDS = "batch_ids"
private const val KEY_BATCH_NO = "batch_no"

data class CleanerPhoto(
    val id: Long,
    val uri: Uri,
    val name: String
)

private class PhotoBatchStore(private val context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val cursor: Long
        get() = prefs.getLong(KEY_CURSOR, Long.MAX_VALUE)

    val batchNo: Int
        get() = prefs.getInt(KEY_BATCH_NO, 1)

    fun savedBatchIds(): List<Long> = prefs.getString(KEY_BATCH_IDS, "")
        .orEmpty()
        .split(',')
        .mapNotNull { it.toLongOrNull() }

    fun saveCurrentBatch(ids: List<Long>) {
        prefs.edit().putString(KEY_BATCH_IDS, ids.joinToString(",")).apply()
    }

    fun advance(ids: List<Long>) {
        if (ids.isEmpty()) return
        val nextCursor = ids.minOrNull() ?: return
        prefs.edit()
            .putLong(KEY_CURSOR, nextCursor)
            .putString(KEY_BATCH_IDS, "")
            .putInt(KEY_BATCH_NO, batchNo + 1)
            .apply()
    }
}

private class PhotoRepository(private val resolver: ContentResolver) {
    private val collection: Uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

    suspend fun loadSaved(ids: List<Long>): List<CleanerPhoto> = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext emptyList()
        val placeholders = ids.joinToString(",") { "?" }
        val selection = "${MediaStore.Images.Media._ID} IN ($placeholders)"
        query(selection, ids.map(Long::toString).toTypedArray())
            .sortedByDescending { ids.indexOf(it.id).let { index -> if (index < 0) Int.MIN_VALUE else -index } }
    }

    suspend fun loadNext(cursor: Long): List<CleanerPhoto> = withContext(Dispatchers.IO) {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME
        )
        val selection = if (cursor == Long.MAX_VALUE) null else "${MediaStore.Images.Media._ID} < ?"
        val args = if (selection == null) null else arrayOf(cursor.toString())
        val result = ArrayList<CleanerPhoto>(BATCH_SIZE)

        resolver.query(
            collection,
            projection,
            selection,
            args,
            "${MediaStore.Images.Media._ID} DESC"
        )?.use { c ->
            val idIndex = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameIndex = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            while (c.moveToNext() && result.size < BATCH_SIZE) {
                val id = c.getLong(idIndex)
                result += CleanerPhoto(
                    id = id,
                    uri = ContentUris.withAppendedId(collection, id),
                    name = c.getString(nameIndex).orEmpty()
                )
            }
        }
        result
    }

    private fun query(selection: String, args: Array<String>): List<CleanerPhoto> {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME
        )
        val result = ArrayList<CleanerPhoto>()
        resolver.query(collection, projection, selection, args, null)?.use { c ->
            val idIndex = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameIndex = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            while (c.moveToNext()) {
                val id = c.getLong(idIndex)
                result += CleanerPhoto(
                    id = id,
                    uri = ContentUris.withAppendedId(collection, id),
                    name = c.getString(nameIndex).orEmpty()
                )
            }
        }
        return result
    }
}

@Composable
fun PhotoCleanerApp() {
    val context = LocalContext.current
    val activity = context as Activity
    val store = remember { PhotoBatchStore(context) }
    val repository = remember { PhotoRepository(context.contentResolver) }
    val photos = remember { mutableStateListOf<CleanerPhoto>() }
    val selected = remember { mutableStateListOf<Long>() }

    var hasPermission by remember { mutableStateOf(hasImagePermission(context)) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var reloadToken by remember { mutableStateOf(0) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (!granted) message = "Fotoğrafları okuyabilmek için izin gerekli."
    }

    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            selected.clear()
            reloadToken++
            message = "Silme tamamlandı. Hazırsan Devam'a bas."
        } else {
            message = "Silme iptal edildi."
        }
    }

    LaunchedEffect(hasPermission, reloadToken) {
        if (!hasPermission) return@LaunchedEffect
        loading = true
        try {
            val savedIds = store.savedBatchIds()
            val loaded = if (savedIds.isNotEmpty()) {
                repository.loadSaved(savedIds)
            } else {
                repository.loadNext(store.cursor).also { batch ->
                    if (batch.isNotEmpty()) store.saveCurrentBatch(batch.map { it.id })
                }
            }
            photos.clear()
            photos.addAll(loaded)
            if (loaded.isEmpty()) message = "Gösterilecek fotoğraf kalmadı."
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
            Text(
                text = "100'lü Foto Ayıklama",
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = "Grup ${store.batchNo} • Bu grupta ${photos.size} fotoğraf • Seçili ${selected.size}",
                style = MaterialTheme.typography.bodyMedium
            )

            if (!hasPermission) {
                Button(onClick = {
                    permissionLauncher.launch(requiredImagePermission())
                }) {
                    Text("Fotoğraf izni ver")
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        enabled = photos.isNotEmpty(),
                        onClick = {
                            if (selected.size == photos.size) selected.clear()
                            else {
                                selected.clear()
                                selected.addAll(photos.map { it.id })
                            }
                        }
                    ) {
                        Text(if (selected.size == photos.size && photos.isNotEmpty()) "Seçimi kaldır" else "100'ünü seç")
                    }

                    Button(
                        modifier = Modifier.weight(1f),
                        enabled = selected.isNotEmpty(),
                        onClick = {
                            val targets = photos.filter { it.id in selected }.map { it.uri }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                try {
                                    val sender: IntentSender = MediaStore.createDeleteRequest(
                                        context.contentResolver,
                                        targets
                                    ).intentSender
                                    deleteLauncher.launch(IntentSenderRequest.Builder(sender).build())
                                } catch (t: Throwable) {
                                    message = "Silme başlatılamadı: ${t.message.orEmpty()}"
                                }
                            } else {
                                targets.forEach { uri -> context.contentResolver.delete(uri, null, null) }
                                selected.clear()
                                reloadToken++
                                message = "Silme tamamlandı. Hazırsan Devam'a bas."
                            }
                        }
                    ) {
                        Text("Seçilenleri sil")
                    }
                }

                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = photos.isNotEmpty() && !loading,
                    onClick = {
                        val shownIds = store.savedBatchIds()
                        if (shownIds.isNotEmpty()) store.advance(shownIds)
                        photos.clear()
                        selected.clear()
                        reloadToken++
                        message = "Sonraki 100 fotoğraf yükleniyor…"
                    }
                ) {
                    Text("DEVAM — SONRAKİ 100")
                }
            }

            if (message.isNotBlank()) {
                Text(message, style = MaterialTheme.typography.bodySmall)
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
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
private fun PhotoCell(photo: CleanerPhoto, selected: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    var bitmap by remember(photo.id) { mutableStateOf<android.graphics.Bitmap?>(null) }

    LaunchedEffect(photo.id) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    context.contentResolver.loadThumbnail(photo.uri, Size(240, 240), null)
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
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(
                if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                else Modifier
            )
            .clickable(onClick = onClick)
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = photo.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().size(92.dp)
            )
        } else {
            Box(
                modifier = Modifier.fillMaxWidth().size(92.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("…")
            }
        }

        if (selected) {
            Text(
                text = "✓",
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(20.dp))
                    .padding(horizontal = 7.dp, vertical = 2.dp)
            )
        }
    }
}

private fun requiredImagePermission(): String = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    Manifest.permission.READ_MEDIA_IMAGES
} else {
    Manifest.permission.READ_EXTERNAL_STORAGE
}

private fun hasImagePermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, requiredImagePermission()) == PackageManager.PERMISSION_GRANTED
