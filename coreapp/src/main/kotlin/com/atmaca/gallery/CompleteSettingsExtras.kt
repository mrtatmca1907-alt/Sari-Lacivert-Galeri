package com.atmaca.gallery

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

enum class AtmacaToolPage { PERSON_CROP, PACKAGER, VIDEO_FRAMES }

@Composable
fun CompleteSettingsExtras(
    onOpenTrash: () -> Unit,
    onOpenDuplicates: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("gallery", android.content.Context.MODE_PRIVATE) }
    var slideshowSeconds by remember { mutableIntStateOf(clampSlideshowSeconds(prefs.getInt("slideshow_seconds", 4))) }
    var slideshowLoop by remember { mutableStateOf(prefs.getBoolean("slideshow_loop", true)) }
    var slideshowRandom by remember { mutableStateOf(prefs.getBoolean("slideshow_random", false)) }
    var activeTool by remember { mutableStateOf<AtmacaToolPage?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        SettingsSubheader("Galeri araçları")
        OutlinedButton(onClick = onOpenTrash, modifier = Modifier.fillMaxWidth()) { Text("Geri Dönüşüm Kutusu") }
        OutlinedButton(onClick = onOpenDuplicates, modifier = Modifier.fillMaxWidth()) { Text("Yinelenenler") }

        SettingsSubheader("Slayt gösterisi")
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Geçiş süresi", modifier = Modifier.weight(1f))
            OutlinedButton(onClick = {
                slideshowSeconds = clampSlideshowSeconds(slideshowSeconds - 1)
                prefs.edit().putInt("slideshow_seconds", slideshowSeconds).apply()
            }) { Text("−") }
            Spacer(Modifier.width(8.dp))
            Text("$slideshowSeconds sn")
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = {
                slideshowSeconds = clampSlideshowSeconds(slideshowSeconds + 1)
                prefs.edit().putInt("slideshow_seconds", slideshowSeconds).apply()
            }) { Text("+") }
        }
        SettingSwitchRow("Döngü", slideshowLoop) {
            slideshowLoop = it
            prefs.edit().putBoolean("slideshow_loop", it).apply()
        }
        SettingSwitchRow("Rastgele sıra", slideshowRandom) {
            slideshowRandom = it
            prefs.edit().putBoolean("slideshow_random", it).apply()
        }

        SettingsSubheader("ATMACA araçları")
        ToolLaunchButton("Akıllı Kişi Kırpma", "Fotoğraflardaki kişi/yüz bölgelerini ayrı JPEG olarak üretir.") { activeTool = AtmacaToolPage.PERSON_CROP }
        ToolLaunchButton("Görsel Paketleyici", "Seçilen medya dosyalarını belirlediğin grup boyutuyla klasörlere ayırır.") { activeTool = AtmacaToolPage.PACKAGER }
        ToolLaunchButton("Video Kareleri", "Videolardan seçtiğin hızda JPEG kareleri ayrı video klasörlerine çıkarır.") { activeTool = AtmacaToolPage.VIDEO_FRAMES }
    }

    activeTool?.let { tool -> AtmacaToolDialog(tool = tool, onDismiss = { activeTool = null }) }
}

@Composable
private fun AtmacaToolDialog(tool: AtmacaToolPage, onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val engine = remember { CompleteToolEngine(context) }
    val repository = remember { MediaStoreRepository(context) }
    val scope = rememberCoroutineScope()
    var selectedUris by remember(tool) { mutableStateOf<List<Uri>>(emptyList()) }
    var running by remember(tool) { mutableStateOf(false) }
    var scanning by remember(tool) { mutableStateOf(false) }
    var scannedCount by remember(tool) { mutableIntStateOf(0) }
    var done by remember(tool) { mutableIntStateOf(0) }
    var total by remember(tool) { mutableIntStateOf(0) }
    var job by remember(tool) { mutableStateOf<Job?>(null) }
    var batchSize by remember(tool) { mutableIntStateOf(50) }
    var framesPerSecond by remember(tool) { mutableIntStateOf(1) }
    var maxFaces by remember(tool) { mutableIntStateOf(12) }
    var showInternalAlbumPicker by remember(tool) { mutableStateOf(false) }
    var backgroundWorkId by remember(tool) { mutableStateOf<UUID?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        uris.forEach { uri ->
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        job = scope.launch {
            val filtered = filterToolUris(context, uris, tool)
            selectedUris = filtered
            done = 0
            total = filtered.size
            if (uris.isNotEmpty() && filtered.isEmpty()) {
                Toast.makeText(context, "Bu araç için uygun dosya bulunamadı", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { treeUri: Uri? ->
        if (treeUri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        scanning = true
        scannedCount = 0
        selectedUris = emptyList()
        job = scope.launch {
            val found = runCatching {
                collectToolUrisFromTree(context, treeUri, tool) { scannedCount = it }
            }.getOrElse { emptyList() }
            if (scanning) {
                selectedUris = found
                total = found.size
                scanning = false
                Toast.makeText(context, "Klasörden ${found.size} uygun dosya bulundu", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun launchPicker() {
        picker.launch(toolPickerMimeTypes(tool).toTypedArray())
    }

    fun cancelActiveWork() {
        job?.cancel()
        backgroundWorkId?.let { WorkManager.getInstance(context.applicationContext).cancelWorkById(it) }
        backgroundWorkId = null
        running = false
        scanning = false
    }

    fun start() {
        if (selectedUris.isEmpty() || running || scanning) return
        if (tool == AtmacaToolPage.VIDEO_FRAMES) {
            val workId = enqueueVideoFrameWork(context, selectedUris, framesPerSecond)
            if (workId != null) {
                backgroundWorkId = workId
                running = true
                done = 0
                total = 0
                Toast.makeText(context, "Video Kareleri arka planda başlatıldı", Toast.LENGTH_LONG).show()
                job = scope.launch {
                    val manager = WorkManager.getInstance(context.applicationContext)
                    while (true) {
                        val info = withContext(Dispatchers.IO) { runCatching { manager.getWorkInfoById(workId).get() }.getOrNull() }
                        if (info != null) {
                            done = info.progress.getInt("done", done)
                            total = info.progress.getInt("total", total)
                            when (info.state) {
                                WorkInfo.State.SUCCEEDED -> {
                                    running = false
                                    backgroundWorkId = null
                                    val created = info.outputData.getInt("created", done)
                                    val failed = info.outputData.getInt("failed", 0)
                                    Toast.makeText(context, "$created kare oluşturuldu${if (failed > 0) " • $failed hata" else ""}", Toast.LENGTH_LONG).show()
                                    break
                                }
                                WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> {
                                    running = false
                                    backgroundWorkId = null
                                    Toast.makeText(context, "Video Kareleri işi tamamlanamadı", Toast.LENGTH_LONG).show()
                                    break
                                }
                                else -> Unit
                            }
                        }
                        delay(500)
                    }
                }
            } else {
                Toast.makeText(context, "Arka plan işi başlatılamadı", Toast.LENGTH_LONG).show()
            }
            return
        }
        running = true
        done = 0
        total = selectedUris.size
        job = scope.launch {
            val result = when (tool) {
                AtmacaToolPage.PERSON_CROP -> engine.smartPersonCrop(selectedUris, maxFacesPerPhoto = maxFaces) { d, t -> done = d; total = t }
                AtmacaToolPage.PACKAGER -> engine.packageMedia(selectedUris, batchSize = batchSize) { d, t -> done = d; total = t }
                AtmacaToolPage.VIDEO_FRAMES -> error("Video Kareleri WorkManager üzerinden çalışır")
            }
            running = false
            Toast.makeText(context, "${result.created} oluşturuldu • ${result.skipped} atlandı • ${result.failed} hata", Toast.LENGTH_LONG).show()
        }
    }

    if (showInternalAlbumPicker) {
        InternalToolAlbumPicker(
            tool = tool,
            repository = repository,
            onDismiss = { showInternalAlbumPicker = false },
            onSelected = { uris ->
                selectedUris = uris.distinct()
                done = 0
                total = selectedUris.size
                showInternalAlbumPicker = false
                Toast.makeText(context, "Klasörden ${selectedUris.size} uygun dosya seçildi", Toast.LENGTH_SHORT).show()
            }
        )
        if (!shouldRenderOuterToolDialog(showInternalAlbumPicker)) return
    }

    Dialog(
        onDismissRequest = { if (!running && !scanning && !showInternalAlbumPicker) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxWidth().fillMaxHeight(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(toolTitle(tool), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = { if (running || scanning) cancelActiveWork() else onDismiss() }) {
                        Text(if (running || scanning) "İptal" else "Kapat")
                    }
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())
                ) {
                    Text(toolDescription(tool), color = MaterialTheme.colorScheme.onSurfaceVariant)

                    OutlinedButton(onClick = ::launchPicker, enabled = !running && !scanning, modifier = Modifier.fillMaxWidth()) {
                        Text(if (selectedUris.isEmpty()) "Dosya seç" else "Dosya seçimini değiştir (${selectedUris.size})")
                    }
                    OutlinedButton(
                        onClick = {
                            if (toolUsesInternalAlbumPicker(tool)) showInternalAlbumPicker = true
                            else folderPicker.launch(null)
                        },
                        enabled = !running && !scanning,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (toolUsesInternalAlbumPicker(tool)) "Galeriden klasör seç" else "Klasör seç ve alt klasörleri tara")
                    }

                    if (scanning) {
                        Text("Klasör taranıyor… $scannedCount dosya kontrol edildi")
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    } else if (selectedUris.isNotEmpty()) {
                        Text("Hazır: ${selectedUris.size} dosya", color = MaterialTheme.colorScheme.primary)
                    }

                    when (tool) {
                        AtmacaToolPage.PERSON_CROP -> IntOptionRow("Fotoğraf başına en fazla kişi", maxFaces, 1, 24) { maxFaces = it }
                        AtmacaToolPage.PACKAGER -> IntOptionRow("Paket başına dosya", batchSize, 5, 200, 5) { batchSize = it }
                        AtmacaToolPage.VIDEO_FRAMES -> IntOptionRow("Saniyedeki kare", framesPerSecond, 1, 4) { framesPerSecond = it }
                    }

                    if (running) {
                        Text(if (tool == AtmacaToolPage.VIDEO_FRAMES) videoFrameProgressText(done, total) else "İşleniyor: $done / $total")
                        if (total > 0) LinearProgressIndicator(progress = { done.toFloat() / total.toFloat() }, modifier = Modifier.fillMaxWidth())
                        else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }

                    Button(onClick = ::start, enabled = selectedUris.isNotEmpty() && !running && !scanning, modifier = Modifier.fillMaxWidth()) { Text("Başlat") }
                }
            }
        }
    }
}

@Composable private fun SettingsSubheader(text: String) { Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp)) }
@Composable private fun SettingSwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) { Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) { Text(label, modifier = Modifier.weight(1f)); Switch(checked = checked, onCheckedChange = onCheckedChange) } }
@Composable private fun ToolLaunchButton(title: String, subtitle: String, onClick: () -> Unit) { Column(Modifier.fillMaxWidth()) { Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(title) }; Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp, vertical = 3.dp)) } }
@Composable private fun IntOptionRow(label: String, value: Int, min: Int, max: Int, step: Int = 1, onChange: (Int) -> Unit) { Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) { Text(label, modifier = Modifier.weight(1f)); OutlinedButton(onClick = { onChange((value - step).coerceAtLeast(min)) }, enabled = value > min) { Text("−") }; Spacer(Modifier.width(8.dp)); Text(value.toString()); Spacer(Modifier.width(8.dp)); OutlinedButton(onClick = { onChange((value + step).coerceAtMost(max)) }, enabled = value < max) { Text("+") } } }

private fun toolTitle(tool: AtmacaToolPage): String = when (tool) { AtmacaToolPage.PERSON_CROP -> "Akıllı Kişi Kırpma"; AtmacaToolPage.PACKAGER -> "Görsel Paketleyici"; AtmacaToolPage.VIDEO_FRAMES -> "Video Kareleri" }
private fun toolDescription(tool: AtmacaToolPage): String = when (tool) { AtmacaToolPage.PERSON_CROP -> "Orijinal fotoğraflara dokunmadan kişi/yüz bölgelerini yeni JPEG dosyaları olarak oluşturur."; AtmacaToolPage.PACKAGER -> "Seçtiğin fotoğraf ve videoları ATMACA Paketler altında ayrı paket klasörlerine kopyalar."; AtmacaToolPage.VIDEO_FRAMES -> "Arka planda kareleri üretir; işlenen video kopyalanmadan kendi kare klasörüne taşınır." }
