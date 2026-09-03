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
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

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

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        uris.forEach { uri ->
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        selectedUris = uris.distinct()
        done = 0
        total = selectedUris.size
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
        when (tool) {
            AtmacaToolPage.PERSON_CROP -> picker.launch(arrayOf("image/*"))
            AtmacaToolPage.PACKAGER -> picker.launch(arrayOf("image/*", "video/*"))
            AtmacaToolPage.VIDEO_FRAMES -> picker.launch(arrayOf("video/*"))
        }
    }

    fun cancelActiveWork() {
        job?.cancel()
        running = false
        scanning = false
    }

    fun start() {
        if (selectedUris.isEmpty() || running || scanning) return
        running = true
        done = 0
        total = selectedUris.size
        job = scope.launch {
            val result = when (tool) {
                AtmacaToolPage.PERSON_CROP -> engine.smartPersonCrop(selectedUris, maxFacesPerPhoto = maxFaces) { d, t -> done = d; total = t }
                AtmacaToolPage.PACKAGER -> engine.packageMedia(selectedUris, batchSize = batchSize) { d, t -> done = d; total = t }
                AtmacaToolPage.VIDEO_FRAMES -> engine.extractVideoFrames(selectedUris, framesPerSecond = framesPerSecond) { d, t -> done = d; total = t }
            }
            running = false
            Toast.makeText(context, "${result.created} oluşturuldu • ${result.skipped} atlandı • ${result.failed} hata", Toast.LENGTH_LONG).show()
        }
    }

    Dialog(
        onDismissRequest = { if (!running && !scanning) onDismiss() },
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
                    OutlinedButton(onClick = { folderPicker.launch(null) }, enabled = !running && !scanning, modifier = Modifier.fillMaxWidth()) {
                        Text("Klasör seç ve alt klasörleri tara")
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
                        Text("İşleniyor: $done / $total")
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
private fun toolDescription(tool: AtmacaToolPage): String = when (tool) { AtmacaToolPage.PERSON_CROP -> "Orijinal fotoğraflara dokunmadan kişi/yüz bölgelerini yeni JPEG dosyaları olarak oluşturur."; AtmacaToolPage.PACKAGER -> "Seçtiğin fotoğraf ve videoları ATMACA Paketler altında ayrı paket klasörlerine kopyalar."; AtmacaToolPage.VIDEO_FRAMES -> "Her videodan belirlediğin kare hızında JPEG üretir; her video kendi isimli çıktı klasörüne gider." }
