package com.atmaca.photo100

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

private const val PREFS = "photo100_vault_state"
private const val KEY_INITIAL_COLLECT = "initial_collect_done"
private const val BATCH_SIZE = 100

class MainActivity : ComponentActivity() {
    private var resumeTick by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
                Photo100VaultApp(resumeTick)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumeTick++
    }
}

private class HiddenVault(private val context: Context) {
    private val externalRoot = Environment.getExternalStorageDirectory()
    private val root = File(externalRoot, ".Foto100Kasa")
    private val pending = File(root, "bekleyen")
    private val current = File(root, "verilen")
    private val done = File(root, "biten")

    init { ensureFolders() }

    private fun ensureFolders() {
        root.mkdirs(); pending.mkdirs(); current.mkdirs(); done.mkdirs()
        runCatching { File(root, ".nomedia").let { if (!it.exists()) it.createNewFile() } }
    }

    fun pendingCount(): Int = photoFiles(pending).size
    fun doneCount(): Int = photoFiles(done).size
    fun totalCount(): Int = pendingCount() + currentBatch().size + doneCount()
    fun currentBatch(): List<File> = photoFiles(current).sortedBy { it.name }

    fun collectAllPhotos(): Int {
        ensureFolders()
        var moved = 0
        val stale = ArrayList<String>(200)
        externalRoot.walkTopDown()
            .onEnter { HiddenStoreRules.shouldEnter(it.absolutePath) }
            .filter { it.isFile && HiddenStoreRules.isPhoto(it.name) }
            .forEach { source ->
                if (source.absolutePath.startsWith(root.absolutePath)) return@forEach
                val old = source.absolutePath
                if (moveInto(source, pending)) {
                    moved++
                    stale += old
                    if (stale.size >= 200) {
                        MediaScannerConnection.scanFile(context, stale.toTypedArray(), null, null)
                        stale.clear()
                    }
                }
            }
        if (stale.isNotEmpty()) MediaScannerConnection.scanFile(context, stale.toTypedArray(), null, null)
        return moved
    }

    fun give100(): List<File> {
        currentBatch().let { if (it.isNotEmpty()) return it }
        photoFiles(pending).sortedBy { it.name }.take(BATCH_SIZE).forEach { moveInto(it, current) }
        return currentBatch()
    }

    fun finishAndGiveNext(): List<File> {
        currentBatch().forEach { moveInto(it, done) }
        return give100()
    }

    fun delete(files: Collection<File>): Int {
        var deleted = 0
        files.forEach { if (it.exists() && it.delete()) deleted++ }
        return deleted
    }

    private fun photoFiles(dir: File): List<File> =
        dir.listFiles()?.filter { it.isFile && HiddenStoreRules.isPhoto(it.name) }.orEmpty()

    private fun moveInto(source: File, destination: File): Boolean {
        destination.mkdirs()
        val target = uniqueTarget(destination, source.name)
        if (source.renameTo(target)) return true
        return runCatching {
            source.inputStream().use { input -> target.outputStream().use { output -> input.copyTo(output, 1024 * 1024) } }
            if (source.delete()) true else { target.delete(); false }
        }.getOrElse { target.delete(); false }
    }

    private fun uniqueTarget(dir: File, name: String): File {
        val clean = name.ifBlank { "foto" }
        var out = File(dir, clean)
        if (!out.exists()) return out
        val base = clean.substringBeforeLast('.', clean)
        val ext = clean.substringAfterLast('.', "")
        do {
            val s = UUID.randomUUID().toString().take(8)
            out = File(dir, if (ext.isBlank()) "${base}_$s" else "${base}_$s.$ext")
        } while (out.exists())
        return out
    }
}

@Composable
private fun Photo100VaultApp(resumeTick: Int) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    val vault = remember { HiddenVault(context) }
    val photos = remember { mutableStateListOf<File>() }
    val selected = remember { mutableStateListOf<String>() }

    var accessGranted by remember { mutableStateOf(hasAllFilesAccess()) }
    var collecting by remember { mutableStateOf(false) }
    var batchLoading by remember { mutableStateOf(false) }
    var collectedOnce by remember { mutableStateOf(prefs.getBoolean(KEY_INITIAL_COLLECT, false)) }
    var collectRequested by remember { mutableStateOf(false) }
    var nextRequested by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var pendingCount by remember { mutableIntStateOf(0) }
    var doneCount by remember { mutableIntStateOf(0) }
    var totalCount by remember { mutableIntStateOf(0) }

    suspend fun refreshCounts() {
        val c = withContext(Dispatchers.IO) { Triple(vault.pendingCount(), vault.doneCount(), vault.totalCount()) }
        pendingCount = c.first; doneCount = c.second; totalCount = c.third
    }

    LaunchedEffect(resumeTick) {
        accessGranted = hasAllFilesAccess()
        if (!accessGranted) return@LaunchedEffect
        photos.clear(); photos.addAll(withContext(Dispatchers.IO) { vault.currentBatch() })
        refreshCounts()
        if (!collectedOnce && !collecting) collectRequested = true
    }

    LaunchedEffect(collectRequested) {
        if (!collectRequested || !accessGranted || collecting) return@LaunchedEffect
        collecting = true
        collectRequested = false
        message = "Telefondaki fotoğraflar gizli kasaya taşınıyor…"
        val moved = withContext(Dispatchers.IO) { vault.collectAllPhotos() }
        prefs.edit().putBoolean(KEY_INITIAL_COLLECT, true).apply()
        collectedOnce = true
        collecting = false
        refreshCounts()
        message = "$moved fotoğraf kasaya alındı. VER'e bas."
    }

    LaunchedEffect(batchLoading) {
        if (!batchLoading || !accessGranted) return@LaunchedEffect
        val batch = withContext(Dispatchers.IO) { vault.give100() }
        photos.clear(); photos.addAll(batch); selected.clear()
        refreshCounts(); batchLoading = false
        message = if (batch.isEmpty()) "Bekleyen fotoğraf kalmadı." else "${batch.size} fotoğraf verildi."
    }

    LaunchedEffect(nextRequested) {
        if (!nextRequested || !accessGranted) return@LaunchedEffect
        nextRequested = false
        batchLoading = true
        val batch = withContext(Dispatchers.IO) { vault.finishAndGiveNext() }
        photos.clear(); photos.addAll(batch); selected.clear()
        refreshCounts(); batchLoading = false
        message = if (batch.isEmpty()) "Tüm fotoğraflar bitti." else "${batch.size} yeni fotoğraf verildi."
    }

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Foto100 Gizli Kasa", style = MaterialTheme.typography.headlineSmall)

            if (!accessGranted) {
                Text("Önce dosya erişimini aç.")
                Button(Modifier.fillMaxWidth(), onClick = { openAllFilesSettings(context) }) { Text("DOSYA ERİŞİMİ VER") }
            }

            Text("Kasada: $totalCount • Bekleyen: $pendingCount • Biten: $doneCount • Verilen: ${photos.size}")

            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = accessGranted && !collecting && !batchLoading && photos.isEmpty() && pendingCount > 0,
                onClick = { batchLoading = true }
            ) {
                Text("VER — 100 FOTOĞRAF")
            }

            if (collecting) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(); Text("Fotoğraflar kasaya taşınıyor… VER birazdan aktif olacak.")
                }
            }

            if (photos.isNotEmpty()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        if (selected.size == photos.size) selected.clear() else {
                            selected.clear(); selected.addAll(photos.map { it.absolutePath })
                        }
                    }) { Text(if (selected.size == photos.size) "Seçimi kaldır" else "Hepsini seç") }

                    Button(enabled = selected.isNotEmpty(), onClick = {
                        val chosen = photos.filter { it.absolutePath in selected }
                        val removed = vault.delete(chosen)
                        photos.removeAll(chosen.toSet()); selected.clear()
                        message = "$removed fotoğraf silindi."
                    }) { Text("Seçilenleri sil") }
                }

                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !batchLoading && !collecting,
                    onClick = { nextRequested = true }
                ) { Text("VER — SONRAKİ 100") }
            }

            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                enabled = accessGranted && !collecting && !batchLoading,
                onClick = { collectRequested = true }
            ) { Text("YENİ FOTOĞRAFLARI TOPLA") }

            if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodySmall)

            if (photos.isNotEmpty()) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4), modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(3.dp), verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    items(photos, key = { it.absolutePath }) { file ->
                        PhotoCell(file, file.absolutePath in selected) {
                            if (file.absolutePath in selected) selected.remove(file.absolutePath) else selected.add(file.absolutePath)
                        }
                    }
                }
            } else if (accessGranted && !collecting) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(if (pendingCount > 0) "Hazır. Yukarıdaki VER tuşuna bas." else "Bekleyen fotoğraf yok.")
                }
            }
        }
    }
}

@Composable
private fun PhotoCell(file: File, selected: Boolean, onClick: () -> Unit) {
    var bitmap by remember(file.absolutePath) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(file.absolutePath) { bitmap = withContext(Dispatchers.IO) { decodeThumbnail(file) } }
    Box(
        Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(5.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(5.dp)) else Modifier)
            .clickable(onClick = onClick)
    ) {
        bitmap?.let { Image(it.asImageBitmap(), file.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        if (selected) Text("✓", color = Color.White, modifier = Modifier.align(Alignment.TopEnd).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(20.dp)).padding(horizontal = 7.dp, vertical = 2.dp))
    }
}

private fun decodeThumbnail(file: File): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / sample > 320 || bounds.outHeight / sample > 320) sample *= 2
    return runCatching { BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample }) }.getOrNull()
}

private fun hasAllFilesAccess(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()

private fun openAllFilesSettings(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))
    runCatching { context.startActivity(intent) }.onFailure { context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
}
