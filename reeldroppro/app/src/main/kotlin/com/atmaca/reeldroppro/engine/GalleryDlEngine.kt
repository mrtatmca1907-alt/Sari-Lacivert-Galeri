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
            val py = Python.getInstance()
            val module = py.getModule("gallery_bridge")
            val raw = module.callAttr(
                "run_download",
                slotId,
                input.platform.name,
                input.value,
                input.sourceKey,
                baseDir().absolutePath
            ).toString()
            val result = JSONObject(raw)
            if (result.optBoolean("cancelled", false)) throw SlotCancelledException()
            val status = result.optInt("status", 1)
            val log = result.optString("log", "")
            val error = result.optString("error", "")
            if (status != 0) throw RuntimeException(error.ifBlank { log.ifBlank { "gallery-dl çıkış kodu: $status" } })
            ExtractorEngine.ResultData(log, File(result.getString("source_dir")))
        }
    }

    fun cancel(slotId: Int) {
        runCatching {
            ensurePython()
            Python.getInstance().getModule("gallery_bridge").callAttr("cancel_slot", slotId)
        }
    }
}
