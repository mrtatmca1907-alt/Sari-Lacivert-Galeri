package com.atmaca.reeldroppro.engine

import android.content.Context
import com.atmaca.reeldroppro.model.ParsedInput
import com.atmaca.reeldroppro.model.Platform
import com.yausername.aria2c.Aria2c
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class ExtractorEngine(private val context: Context) {
    data class ResultData(val stdout: String, val tempDir: File)

    @Volatile private var initialized = false
    private val processes = SlotProcessRegistry()
    private val gallery = GalleryDlEngine(context)

    @Synchronized
    fun initYtDlp() {
        if (initialized) return
        YoutubeDL.getInstance().init(context.applicationContext)
        FFmpeg.getInstance().init(context.applicationContext)
        Aria2c.getInstance().init(context.applicationContext)
        initialized = true
    }

    fun workingDir(input: ParsedInput, jobId: Long): File = when (ExtractorBackendPolicy.backendFor(input.platform)) {
        ExtractorBackend.GALLERY_DL -> gallery.sourceDir(input)
        ExtractorBackend.YT_DLP -> File(requireNotNull(context.getExternalFilesDir(null)), "reeldrop-pro/facebook/$jobId")
    }

    suspend fun run(
        input: ParsedInput,
        jobId: Long,
        slotId: Int,
        onProgress: (Float, Long) -> Unit
    ): Result<ResultData> = when (ExtractorBackendPolicy.backendFor(input.platform)) {
        ExtractorBackend.GALLERY_DL -> gallery.run(input, slotId)
        ExtractorBackend.YT_DLP -> runYtDlp(input, jobId, slotId, onProgress)
    }

    private suspend fun runYtDlp(
        input: ParsedInput,
        jobId: Long,
        slotId: Int,
        onProgress: (Float, Long) -> Unit
    ): Result<ResultData> = withContext(Dispatchers.IO) {
        runCatching {
            require(input.platform == Platform.FACEBOOK)
            initYtDlp()
            val tempDir = workingDir(input, jobId).apply {
                deleteRecursively()
                mkdirs()
            }
            val template = File(tempDir, "%(title).160B_[%(id)s].%(ext)s").absolutePath
            val request = ExtractorRequestFactory.build(input, template).apply {
                addOption("--yes-playlist")
            }
            val processId = "slot-$slotId-${UUID.randomUUID()}"
            processes.set(slotId, processId)
            try {
                val response = YoutubeDL.getInstance().execute(request, processId) { progress, eta, _ ->
                    onProgress(progress, eta)
                }
                ResultData(response.out.orEmpty(), tempDir)
            } finally {
                processes.clear(slotId)
            }
        }
    }

    fun cancel(slotId: Int) {
        gallery.cancel(slotId)
        processes.get(slotId)?.let { processId ->
            runCatching { YoutubeDL.getInstance().destroyProcessById(processId) }
        }
        processes.clear(slotId)
    }

    fun cancelAll() {
        EngineSlotPolicy.slotIds.forEach(::cancel)
    }
}
