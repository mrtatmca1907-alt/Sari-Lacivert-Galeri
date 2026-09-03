package com.atmaca.gallery

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun CompleteSettingsExtras(
    onOpenTrash: () -> Unit,
    onOpenDuplicates: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("gallery", android.content.Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    val engine = remember { CompleteToolEngine(context) }
    var running by remember { mutableStateOf<String?>(null) }
    var progressDone by remember { mutableIntStateOf(0) }
    var progressTotal by remember { mutableIntStateOf(0) }
    var slideshowSeconds by remember { mutableIntStateOf(clampSlideshowSeconds(prefs.getInt("slideshow_seconds", 4))) }
    var slideshowLoop by remember { mutableStateOf(prefs.getBoolean("slideshow_loop", true)) }
    var slideshowRandom by remember { mutableStateOf(prefs.getBoolean("slideshow_random", false)) }

    fun report(label: String, result: ToolRunResult) {
        running = null
        Toast.makeText(
            context,
            "$label: ${result.created} oluşturuldu, ${result.skipped} atlandı, ${result.failed} hata",
            Toast.LENGTH_LONG
        ).show()
    }

    val personCropPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        running = "Akıllı Kişi Kırpma"
        progressDone = 0; progressTotal = uris.size
        scope.launch {
            val result = engine.smartPersonCrop(uris) { done, total -> progressDone = done; progressTotal = total }
            report("Kişi kırpma", result)
        }
    }

    val packagerPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        running = "Görsel Paketleyici"
        progressDone = 0; progressTotal = uris.size
        scope.launch {
            val result = engine.packageMedia(uris, batchSize = 50) { done, total -> progressDone = done; progressTotal = total }
            report("Paketleyici", result)
        }
    }

    val videoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        running = "Video Kareleri"
        progressDone = 0; progressTotal = uris.size
        scope.launch {
            val result = engine.extractVideoFrames(uris, framesPerSecond = 1) { done, total -> progressDone = done; progressTotal = total }
            report("Video kareleri", result)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Dosya araçları", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onOpenTrash, modifier = Modifier.weight(1f)) { Text("Geri Dönüşüm Kutusu") }
            OutlinedButton(onClick = onOpenDuplicates, modifier = Modifier.weight(1f)) { Text("Yinelenenler") }
        }

        Text("Slayt gösterisi", style = MaterialTheme.typography.titleSmall)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = {
                slideshowSeconds = clampSlideshowSeconds(slideshowSeconds - 1)
                prefs.edit().putInt("slideshow_seconds", slideshowSeconds).apply()
            }) { Text("−") }
            Text("$slideshowSeconds sn")
            OutlinedButton(onClick = {
                slideshowSeconds = clampSlideshowSeconds(slideshowSeconds + 1)
                prefs.edit().putInt("slideshow_seconds", slideshowSeconds).apply()
            }) { Text("+") }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Döngü", modifier = Modifier.weight(1f))
            Switch(checked = slideshowLoop, onCheckedChange = {
                slideshowLoop = it; prefs.edit().putBoolean("slideshow_loop", it).apply()
            })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Rastgele sıra", modifier = Modifier.weight(1f))
            Switch(checked = slideshowRandom, onCheckedChange = {
                slideshowRandom = it; prefs.edit().putBoolean("slideshow_random", it).apply()
            })
        }

        Text("ATMACA Araçları", style = MaterialTheme.typography.titleSmall)
        Button(
            enabled = running == null,
            onClick = { personCropPicker.launch(arrayOf("image/*")) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Akıllı Kişi Kırpma") }
        Text("Fotoğraftaki yüz/kişi bölgelerini cihazda bulur, yeni kırpılmış JPEG'ler oluşturur; orijinale dokunmaz.", style = MaterialTheme.typography.bodySmall)

        Button(
            enabled = running == null,
            onClick = { packagerPicker.launch(arrayOf("image/*", "video/*")) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Görsel Paketleyici") }
        Text("Seçilen medya dosyalarını 50'lik paket klasörlerine kopyalar.", style = MaterialTheme.typography.bodySmall)

        Button(
            enabled = running == null,
            onClick = { videoPicker.launch(arrayOf("video/*")) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Video Kareleri") }
        Text("Her videodan varsayılan saniyede 1 JPEG çıkarır; her video kendi isimli klasörüne gider.", style = MaterialTheme.typography.bodySmall)

        if (running != null) {
            Text("$running çalışıyor: $progressDone / $progressTotal")
            if (progressTotal > 0) {
                LinearProgressIndicator(
                    progress = { progressDone.toFloat() / progressTotal.toFloat() },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
            }
        }
    }
}
