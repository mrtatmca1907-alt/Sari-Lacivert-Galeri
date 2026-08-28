package com.facebookdrop

import android.content.Context
import android.os.Environment
import com.yausername.aria2c.Aria2c
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class YtDlpEngine(private val context: Context) {
    @Volatile private var initialized = false
    @Volatile private var activeProcessId: String? = null

    @Synchronized fun init() {
        if (initialized) return
        YoutubeDL.getInstance().init(context.applicationContext)
        FFmpeg.getInstance().init(context.applicationContext)
        Aria2c.getInstance().init(context.applicationContext)
        initialized = true
    }

    suspend fun download(url: String, onProgress: (Float, Long) -> Unit): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            init()
            val root = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "FacebookDrop")
            root.mkdirs()
            val request = YoutubeDLRequest(url)
            request.addOption("--continue")
            request.addOption("--no-overwrites")
            request.addOption("--yes-playlist")
            request.addOption("--no-mtime")
            request.addOption("--restrict-filenames")
            request.addOption("--downloader", "libaria2c.so")
            request.addOption("-f", "bestvideo*+bestaudio/best")
            request.addOption("--merge-output-format", "mp4")
            request.addOption("-o", File(root, "%(uploader)s/%(title).160B_[%(id)s].%(ext)s").absolutePath)
            val processId = UUID.randomUUID().toString()
            activeProcessId = processId
            try {
                YoutubeDL.getInstance().execute(request, processId) { progress, eta, _ ->
                    onProgress(progress, eta)
                }.out
            } finally {
                activeProcessId = null
            }
        }
    }

    fun cancel() { activeProcessId?.let { runCatching { YoutubeDL.getInstance().destroyProcessById(it) } } }
}
