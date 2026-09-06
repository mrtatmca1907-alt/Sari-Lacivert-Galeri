package com.sarilacivert.galeri.tools

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun AtmacaToolsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val inputResolver = remember { ToolInputResolver(context.applicationContext) }
    val engine = remember { CompleteToolEngine(context.applicationContext) }

    var mode by remember { mutableStateOf(ToolMode.PERSON_CROP) }
    var input by remember { mutableStateOf(ToolInputState()) }
    var running by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(ToolProgress()) }
    var resultText by remember { mutableStateOf("") }
    var maxFaces by remember { mutableIntStateOf(10) }
    var fps by remember { mutableIntStateOf(1) }
    var batchSize by remember { mutableIntStateOf(100) }

    val filesLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            uris.forEach { uri ->
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                }
            }
            input = ToolInputPolicy.filesSelected(uris.map(Uri::toString))
            progress = ToolProgress()
            resultText = "${uris.size} dosya seçildi. Tarama yapılmadı."
        }
    }

    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            inputResolver.persistTreePermission(uri)
            input = ToolInputPolicy.folderSelected(uri.toString())
            progress = ToolProgress()
            resultText = "Klasör seçildi. Tarama yapılmadı; Başlat'a basınca işlenecek."
        }
    }

    AlertDialog(
        onDismissRequest = { if (!running) onDismiss() },
        title = { Text("ATMACA Araçları") },
        text = {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Görev", style = MaterialTheme.typography.titleSmall)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ModeButton("Kişi", mode == ToolMode.PERSON_CROP, enabled = !running) { mode = ToolMode.PERSON_CROP; input = ToolInputState(); resultText = "" }
                    ModeButton("Kare", mode == ToolMode.VIDEO_FRAMES, enabled = !running) { mode = ToolMode.VIDEO_FRAMES; input = ToolInputState(); resultText = "" }
                    ModeButton("Paket", mode == ToolMode.PACKAGER, enabled = !running) { mode = ToolMode.PACKAGER; input = ToolInputState(); resultText = "" }
                }

                Text(toolDescription(mode), style = MaterialTheme.typography.bodySmall)

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { filesLauncher.launch(mimeTypes(mode)) },
                        enabled = !running,
                        modifier = Modifier.weight(1f)
                    ) { Text("Dosya seç") }
                    OutlinedButton(
                        onClick = { folderLauncher.launch(null) },
                        enabled = !running,
                        modifier = Modifier.weight(1f)
                    ) { Text("Klasör seç") }
                }

                val selectionText = when {
                    input.directUris.isNotEmpty() -> "Seçilen dosya: ${input.directUris.size}"
                    input.treeUri != null -> "Klasör hazır — ön tarama yok"
                    else -> "Henüz kaynak seçilmedi"
                }
                Text(selectionText, style = MaterialTheme.typography.bodyMedium)

                when (mode) {
                    ToolMode.PERSON_CROP -> {
                        Text("Fotoğraf başına en fazla kişi: $maxFaces")
                        Slider(
                            value = maxFaces.toFloat(),
                            onValueChange = { maxFaces = it.toInt() },
                            valueRange = 1f..30f,
                            enabled = !running
                        )
                    }
                    ToolMode.VIDEO_FRAMES -> {
                        Text("Saniyedeki kare: $fps")
                        Slider(
                            value = fps.toFloat(),
                            onValueChange = { fps = it.toInt() },
                            valueRange = 1f..5f,
                            steps = 3,
                            enabled = !running
                        )
                    }
                    ToolMode.PACKAGER -> {
                        Text("Paket başına dosya: $batchSize")
                        Slider(
                            value = batchSize.toFloat(),
                            onValueChange = { batchSize = (it.toInt() / 10 * 10).coerceAtLeast(10) },
                            valueRange = 10f..1000f,
                            enabled = !running
                        )
                    }
                }

                if (running) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("Taranan ${progress.scanned} • İşlenen ${progress.processed} • Çıktı ${progress.outputs} • Hata ${progress.failed}")
                    if (progress.currentName.isNotBlank()) Text(progress.currentName, maxLines = 1, style = MaterialTheme.typography.bodySmall)
                }
                if (resultText.isNotBlank()) Text(resultText, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(
                enabled = !running && (input.directUris.isNotEmpty() || input.treeUri != null),
                onClick = {
                    running = true
                    resultText = ""
                    scope.launch {
                        val result = runCatching {
                            engine.run(
                                mode = mode,
                                input = input,
                                options = ToolOptions(maxFaces, fps, batchSize),
                                onProgress = { progress = it }
                            )
                        }
                        running = false
                        resultText = result.fold(
                            onSuccess = { "Bitti: ${it.processed} işlendi, ${it.outputs} çıktı, ${it.failed} hata." },
                            onFailure = { "Hata: ${it.message ?: "işlem tamamlanamadı"}" }
                        )
                    }
                }
            ) { Text(if (running) "Çalışıyor" else "Başlat") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !running) { Text("Kapat") }
        }
    )
}

@Composable
private fun ModeButton(text: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    if (selected) Button(onClick = onClick, enabled = enabled) { Text(text) }
    else OutlinedButton(onClick = onClick, enabled = enabled) { Text(text) }
}

private fun mimeTypes(mode: ToolMode): Array<String> = when (mode) {
    ToolMode.PERSON_CROP -> arrayOf("image/*")
    ToolMode.VIDEO_FRAMES -> arrayOf("video/*")
    ToolMode.PACKAGER -> arrayOf("image/*", "video/*")
}

private fun toolDescription(mode: ToolMode): String = when (mode) {
    ToolMode.PERSON_CROP -> "Fotoğraflardaki kişileri algılar ve ayrı JPG kırpımları üretir."
    ToolMode.VIDEO_FRAMES -> "Videolardan seçilen hızda JPEG kareleri çıkarır; başarılı videoyu kendi kare klasörüne taşımayı dener."
    ToolMode.PACKAGER -> "Fotoğraf ve videoları ATMACA Paketler altında toplu paket klasörlerine kopyalar."
}
