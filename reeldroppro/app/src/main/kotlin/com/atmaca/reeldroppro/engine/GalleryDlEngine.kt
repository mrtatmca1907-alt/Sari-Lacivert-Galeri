package com.atmaca.reeldroppro.engine

import android.content.Context
import com.atmaca.reeldroppro.model.ParsedInput
import com.atmaca.reeldroppro.model.Platform
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

class SlotCancelledException : RuntimeException("İndirme kullanıcı tarafından durduruldu")

class GalleryDlEngine(private val context: Context) {
    private val appContext = context.applicationContext

    @Synchronized
    private fun ensurePython() {
        if (!Python.isStarted()) Python.start(AndroidPlatform(appContext))
    }

    fun baseDir(): File = File(requireNotNull(appContext.getExternalFilesDir(null)), "reeldrop-pro/gallery")
    private fun cookieFile(): File = File(appContext.filesDir, "reeldrop-pro/auth/instagram-cookies.txt")

    fun sourceDir(input: ParsedInput): File {
        val clean = input.sourceKey.removePrefix("#").trim().lowercase()
        return if (input.platform == Platform.INSTAGRAM_HASHTAG) {
            File(baseDir(), "instagram/tag/$clean")
        } else {
            File(baseDir(), "instagram/$clean")
        }
    }

    suspend fun run(input: ParsedInput, slotId: Int): Result<ExtractorEngine.ResultData> = withContext(Dispatchers.IO) {
        runCatching {
            require(input.platform == Platform.INSTAGRAM_PROFILE || input.platform == Platform.INSTAGRAM_HASHTAG)
            ensurePython()
            val module = Python.getInstance().getModule("gallery_bridge")
            val cookies = cookieFile().takeIf { it.isFile }?.absolutePath.orEmpty()
            val raw = module.callAttr(
                "run_download",
                slotId,
                input.platform.name,
                input.value,
                input.sourceKey,
                baseDir().absolutePath,
                cookies
            ).toString()
            val result = JSONObject(raw)
            if (result.optBoolean("cancelled", false)) throw SlotCancelledException()

            val status = result.optInt("status", 1)
            val partial = result.optBoolean("partial_success", false)
            val log = result.optString("log", "")
            val error = result.optString("error", "")
            val errorCount = result.optInt("error_count", 0)
            if (status != 0 && !partial) {
                throw RuntimeException(error.ifBlank { log.ifBlank { "gallery-dl çıkış kodu: $status" } })
            }
            ExtractorEngine.ResultData(
                stdout = log,
                tempDir = File(result.getString("source_dir")),
                itemErrors = errorCount,
                partialError = error.takeIf { partial && it.isNotBlank() }
            )
        }
    }

    fun cancel(slotId: Int) {
        runCatching {
            ensurePython()
            Python.getInstance().getModule("gallery_bridge").callAttr("cancel_slot", slotId)
        }
    }
}
