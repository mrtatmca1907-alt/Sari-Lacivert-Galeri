package com.atmaca.toplayici1907

import android.Manifest
import android.app.Activity
import android.content.IntentSender
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private lateinit var repository: CollectorRepository

    private var ui by mutableStateOf(CollectorUiState())
    private var running = false

    private var deleteChunks: List<List<PhotoRecord>> = emptyList()
    private var deleteIndex = 0

    private var writeChunks: List<List<PhotoRecord>> = emptyList()
    private var writeIndex = 0
    private var targetNames: Map<Long, String> = emptyMap()

    private val readPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startCollector() else fail("Fotoğraf izni verilmedi")
    }

    private val deleteLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val current = deleteChunks.getOrNull(deleteIndex).orEmpty()
            ui = ui.copy(
                duplicates = ui.duplicates + current.size,
                currentName = current.lastOrNull()?.name.orEmpty()
            )
            deleteIndex++
            launchNextDeleteChunk()
        } else {
            fail("Kopya silme onayı iptal edildi")
        }
    }

    private val writeLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            moveCurrentWriteChunk()
        } else {
            fail("1907 klasörüne taşıma onayı iptal edildi")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = CollectorRepository(this)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("ATMACA 1907 Fotoğraf Toplayıcı", style = MaterialTheme.typography.headlineSmall)
                        Text("Tüm erişilebilir fotoğrafları tarar, birebir kopyaları SHA-256 ile doğrular ve kalanları Pictures/1907 klasöründe toplar.")

                        if (ui.running) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }

                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("Durum: ${ui.phase}")
                                if (ui.currentName.isNotBlank()) Text("Dosya: ${ui.currentName}")
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Tarandı: ${ui.scanned}")
                                    Text("Kopya: ${ui.duplicates}")
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Taşındı: ${ui.moved}")
                                    Text("Hata: ${ui.failed}")
                                }
                                if (ui.remaining >= 0) Text("Kalan: ${ui.remaining}")
                                if (ui.message.isNotBlank()) Text(ui.message)
                            }
                        }

                        Spacer(Modifier.height(4.dp))

                        Button(
                            onClick = { ensurePermissionAndStart() },
                            enabled = !ui.running,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (ui.phase == "Tamamlandı") "Tekrar Tara" else "Tara ve Toparla")
                        }
                    }
                }
            }
        }
    }

    private fun ensurePermissionAndStart() {
        val permission = if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            startCollector()
        } else {
            readPermissionLauncher.launch(permission)
        }
    }

    private fun startCollector() {
        if (running) return
        running = true
        ui = CollectorUiState(running = true, phase = "Taranıyor")

        lifecycleScope.launch {
            try {
                val photos = withContext(Dispatchers.IO) { repository.loadPhotos() }
                ui = ui.copy(scanned = photos.size, remaining = photos.size, phase = "Kopyalar doğrulanıyor")

                val plan = withContext(Dispatchers.IO) {
                    ConsolidationPlanner.plan(photos) { repository.sha256(it) }
                }

                val safeDuplicates = withContext(Dispatchers.IO) {
                    plan.duplicateGroups.flatMap { group ->
                        if (repository.exists(group.survivor)) group.duplicates else emptyList()
                    }
                }

                if (safeDuplicates.isNotEmpty()) {
                    ui = ui.copy(
                        phase = "Kopyalar temizleniyor",
                        message = "${safeDuplicates.size} kesin kopya bulundu"
                    )
                    deleteChunks = safeDuplicates.chunked(1000)
                    deleteIndex = 0
                    launchNextDeleteChunk()
                } else {
                    prepareMovePhase()
                }
            } catch (t: Throwable) {
                fail(t.message ?: "Tarama sırasında hata oluştu")
            }
        }
    }

    private fun launchNextDeleteChunk() {
        val chunk = deleteChunks.getOrNull(deleteIndex)
        if (chunk == null) {
            prepareMovePhase()
            return
        }

        if (Build.VERSION.SDK_INT < 30) {
            fail("Bu sürüm Android 11 ve üzeri için güvenli toplu silme kullanıyor")
            return
        }

        val uris = chunk.map { Uri.parse(it.uri) }
        val pendingIntent = MediaStore.createDeleteRequest(contentResolver, uris)
        val request = IntentSenderRequest.Builder(pendingIntent.intentSender).build()
        deleteLauncher.launch(request)
    }

    private fun prepareMovePhase() {
        lifecycleScope.launch {
            try {
                val photos = withContext(Dispatchers.IO) { repository.loadPhotos() }
                ui = ui.copy(scanned = photos.size, phase = "Kopyalar doğrulanıyor")

                val plan = withContext(Dispatchers.IO) {
                    ConsolidationPlanner.plan(photos) { repository.sha256(it) }
                }

                if (plan.duplicates.isNotEmpty()) {
                    fail("Bazı kopyalar temizlenemedi; taşıma başlatılmadı")
                    return@launch
                }

                targetNames = CollectorPolicy.targetNames(plan.survivors)
                val outside = plan.survivors.filterNot(CollectorPolicy::isTarget)
                ui = ui.copy(
                    phase = "1907'ye taşınıyor",
                    remaining = outside.size,
                    message = if (outside.isEmpty()) "Tüm fotoğraflar zaten 1907 içinde" else "${outside.size} fotoğraf taşınacak"
                )

                if (outside.isEmpty()) {
                    finishVerification()
                    return@launch
                }

                writeChunks = outside.chunked(1000)
                writeIndex = 0
                launchNextWriteChunk()
            } catch (t: Throwable) {
                fail(t.message ?: "Taşıma hazırlığında hata oluştu")
            }
        }
    }

    private fun launchNextWriteChunk() {
        val chunk = writeChunks.getOrNull(writeIndex)
        if (chunk == null) {
            finishVerification()
            return
        }

        if (Build.VERSION.SDK_INT < 30) {
            moveCurrentWriteChunk()
            return
        }

        val uris = chunk.map { Uri.parse(it.uri) }
        val pendingIntent = MediaStore.createWriteRequest(contentResolver, uris)
        val request = IntentSenderRequest.Builder(pendingIntent.intentSender).build()
        writeLauncher.launch(request)
    }

    private fun moveCurrentWriteChunk() {
        val chunk = writeChunks.getOrNull(writeIndex) ?: run {
            finishVerification()
            return
        }

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                var moved = 0
                var failed = 0
                var last = ""
                chunk.forEach { photo ->
                    last = photo.name
                    val targetName = targetNames[photo.id] ?: photo.name
                    val ok = runCatching { repository.moveTo1907(photo, targetName) }.getOrDefault(false)
                    if (ok) moved++ else failed++
                }
                Triple(moved, failed, last)
            }

            ui = ui.copy(
                moved = ui.moved + result.first,
                failed = ui.failed + result.second,
                remaining = (ui.remaining - chunk.size).coerceAtLeast(0),
                currentName = result.third
            )
            writeIndex++
            launchNextWriteChunk()
        }
    }

    private fun finishVerification() {
        lifecycleScope.launch {
            try {
                ui = ui.copy(phase = "Son kontrol")
                val photos = withContext(Dispatchers.IO) { repository.loadPhotos() }
                val plan = withContext(Dispatchers.IO) {
                    ConsolidationPlanner.plan(photos) { repository.sha256(it) }
                }
                val outside = plan.survivors.count { !CollectorPolicy.isTarget(it) }
                running = false

                if (plan.duplicates.isEmpty() && outside == 0) {
                    ui = ui.copy(
                        running = false,
                        phase = "Tamamlandı",
                        scanned = photos.size,
                        remaining = 0,
                        currentName = "",
                        message = "Pictures/1907 içinde ${plan.survivors.size} benzersiz fotoğraf kaldı."
                    )
                } else {
                    ui = ui.copy(
                        running = false,
                        phase = "Eksik kaldı",
                        scanned = photos.size,
                        remaining = outside,
                        message = "Kalan kesin kopya: ${plan.duplicates.size} • 1907 dışında: $outside"
                    )
                }
            } catch (t: Throwable) {
                fail(t.message ?: "Son kontrolde hata oluştu")
            }
        }
    }

    private fun fail(message: String) {
        running = false
        ui = ui.copy(running = false, phase = "Hata", message = message)
    }
}

data class CollectorUiState(
    val running: Boolean = false,
    val phase: String = "Hazır",
    val scanned: Int = 0,
    val duplicates: Int = 0,
    val moved: Int = 0,
    val remaining: Int = -1,
    val failed: Int = 0,
    val currentName: String = "",
    val message: String = ""
)
