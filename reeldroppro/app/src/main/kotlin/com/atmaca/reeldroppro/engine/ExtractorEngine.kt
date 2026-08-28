package com.atmaca.reeldroppro.engine

import android.content.Context
import com.atmaca.reeldroppro.model.ParsedInput
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
    @Volatile private var processId: String? = null

    @Synchronized
    fun init() {
        if (initialized) return
        YoutubeDL.getInstance().init(context.applicationContext)
        FFmpeg.getInstance().init(context.applicationContext)
        Aria2c.getInstance().init(context.applicationContext)
        initialized = true
    }

    suspend fun run(
        input: ParsedInput,
        jobId: Long,
        onProgress: (Float, Long) -> Unit
    ): Result<ResultData> = withContext(Dispatchers.IO) {
        runCatching {
            init()
            val base = requireNotNull(context.getExternalFilesDir(null))
            val tempDir = File(base, "reeldrop-pro/$jobId").apply {
                deleteRecursively()
                mkdirs()
            }
            val template = File(tempDir, "%(title).160B_[%(id)s].%(ext)s").absolutePath
            val request = ExtractorRequestFactory.build(input, template)
            request.addOption("--yes-playlist")
            val id = UUID.randomUUID().toString()
            processId = id
            try {
                val response = YoutubeDL.getInstance().execute(request, id) { progress, eta, _ ->
                    onProgress(progress, eta)
                }
                ResultData(response.out.orEmpty(), tempDir)
            } finally {
                processId = null
            }
        }
    }

    fun cancel() {
        processId?.let { id -> runCatching { YoutubeDL.getInstance().destroyProcessById(id) } }
    }
}
