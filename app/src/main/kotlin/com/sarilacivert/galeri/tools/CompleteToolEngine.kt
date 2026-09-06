package com.sarilacivert.galeri.tools

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max
import kotlin.math.min


data class ToolOptions(
    val maxFacesPerPhoto: Int = 10,
    val framesPerSecond: Int = 1,
    val batchSize: Int = 100
)

data class ToolProgress(
    val scanned: Int = 0,
    val processed: Int = 0,
    val outputs: Int = 0,
    val failed: Int = 0,
    val currentName: String = ""
)

data class ToolResult(
    val processed: Int,
    val outputs: Int,
    val failed: Int
)

class CompleteToolEngine(private val context: Context) {
    private val resolver = context.contentResolver
    private val inputResolver = ToolInputResolver(context)

    suspend fun run(
        mode: ToolMode,
        input: ToolInputState,
        options: ToolOptions,
        onProgress: (ToolProgress) -> Unit
    ): ToolResult = withContext(Dispatchers.IO) {
        val started = ToolInputPolicy.start(input)
        var scanned = 0
        var processed = 0
        var outputs = 0
        var failed = 0
        val stamp = System.currentTimeMillis()

        inputResolver.stream(started, mode).collect { uri ->
            scanned++
            val name = displayName(uri).ifBlank { "medya_$scanned" }
            onProgress(ToolProgress(scanned, processed, outputs, failed, name))
            val produced = runCatching {
                when (mode) {
                    ToolMode.PERSON_CROP -> smartPersonCrop(uri, name, options.maxFacesPerPhoto)
                    ToolMode.VIDEO_FRAMES -> extractVideoFrames(uri, name, options.framesPerSecond)
                    ToolMode.PACKAGER -> packageMedia(uri, name, options.batchSize, processed, stamp)
                }
            }.getOrElse {
                failed++
                0
            }
            processed++
            outputs += produced
            onProgress(ToolProgress(scanned, processed, outputs, failed, name))
        }
        ToolResult(processed, outputs, failed)
    }

    private suspend fun smartPersonCrop(uri: Uri, sourceName: String, maxFaces: Int): Int {
        val bitmap = resolver.openInputStream(uri)?.use(BitmapFactory::decodeStream) ?: return 0
        try {
            val options = FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .build()
            val detector = FaceDetection.getClient(options)
            return try {
                val faces = detector.process(InputImage.fromBitmap(bitmap, 0)).awaitTask()
                var written = 0
                val base = safeBaseName(sourceName)
                for ((index, face) in faces.take(maxFaces.coerceIn(1, 50)).withIndex()) {
                    val crop = cropFace(bitmap, face) ?: continue
                    try {
                        val outName = "${base}_kisi_${index + 1}.jpg"
                        if (saveJpeg(crop, outName, "Pictures/ATMACA Kişi Kırpma/")) written++
                    } finally {
                        if (crop !== bitmap && !crop.isRecycled) crop.recycle()
                    }
                }
                written
            } finally {
                detector.close()
            }
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    private fun cropFace(bitmap: Bitmap, face: Face): Bitmap? {
        val box = face.boundingBox
        val marginX = (box.width() * 0.35f).toInt()
        val marginY = (box.height() * 0.55f).toInt()
        val left = max(0, box.left - marginX)
        val top = max(0, box.top - marginY)
        val right = min(bitmap.width, box.right + marginX)
        val bottom = min(bitmap.height, box.bottom + marginY)
        if (right <= left || bottom <= top) return null
        return Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
    }

    private fun extractVideoFrames(uri: Uri, sourceName: String, fpsRaw: Int): Int {
        val fps = fpsRaw.coerceIn(1, 5)
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            if (durationMs <= 0L) return 0
            val base = safeBaseName(sourceName)
            val folder = "Pictures/ATMACA Video Kareleri/$base/"
            val stepUs = 1_000_000L / fps
            val durationUs = durationMs * 1_000L
            var timeUs = 0L
            var frameNo = 1
            var written = 0
            while (timeUs < durationUs) {
                val frame = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
                if (frame != null) {
                    try {
                        if (saveJpeg(frame, "${base}_${frameNo.toString().padStart(6, '0')}.jpg", folder)) written++
                    } finally {
                        if (!frame.isRecycled) frame.recycle()
                    }
                }
                frameNo++
                timeUs += stepUs
            }
            if (written > 0) moveVideoToOutput(uri, sourceName, folder)
            written
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun packageMedia(uri: Uri, sourceName: String, batchSizeRaw: Int, processedBefore: Int, stamp: Long): Int {
        val batchSize = batchSizeRaw.coerceIn(1, 5000)
        val packageNo = processedBefore / batchSize + 1
        val folder = "Pictures/ATMACA Paketler/Paket_${stamp}_$packageNo/"
        val mime = resolver.getType(uri).orEmpty().ifBlank { guessMime(sourceName) }
        return if (copyUriToMediaStore(uri, sourceName, mime, folder) != null) 1 else 0
    }

    private fun moveVideoToOutput(source: Uri, sourceName: String, folder: String) {
        val mime = resolver.getType(source).orEmpty().ifBlank { "video/mp4" }
        val target = copyUriToMediaStore(source, sourceName, mime, folder) ?: return
        val deleted = runCatching { resolver.delete(source, null, null) > 0 }.getOrDefault(false)
        if (!deleted) {
            // SAF sağlayıcısı silmeye izin vermiyorsa kaynak korunur; oluşturulan çıktı geçerli kalır.
        }
        finalizePending(target)
    }

    private fun saveJpeg(bitmap: Bitmap, name: String, folder: String): Boolean {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, folder)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }
        val target = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        return try {
            val ok = resolver.openOutputStream(target, "w")?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 94, out)
            } ?: false
            if (ok) finalizePending(target) else resolver.delete(target, null, null)
            ok
        } catch (t: Throwable) {
            runCatching { resolver.delete(target, null, null) }
            false
        }
    }

    private fun copyUriToMediaStore(source: Uri, name: String, mime: String, folder: String): Uri? {
        val collection = if (mime.startsWith("video/")) {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, folder)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }
        val target = resolver.insert(collection, values) ?: return null
        return try {
            val copied = resolver.openInputStream(source)?.use { input ->
                resolver.openOutputStream(target, "w")?.use { output ->
                    input.copyTo(output, 1024 * 1024)
                    true
                }
            } ?: false
            if (!copied) {
                resolver.delete(target, null, null)
                null
            } else {
                finalizePending(target)
                target
            }
        } catch (t: Throwable) {
            runCatching { resolver.delete(target, null, null) }
            null
        }
    }

    private fun finalizePending(uri: Uri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        }
    }

    private fun displayName(uri: Uri): String {
        return runCatching {
            resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0).orEmpty() else ""
            }.orEmpty()
        }.getOrDefault("")
    }

    private fun safeBaseName(name: String): String = name.substringBeforeLast('.', name)
        .replace(Regex("[^\\p{L}\\p{N}._ -]"), "_")
        .trim()
        .ifBlank { "medya" }

    private fun guessMime(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return when (ext) {
            "mp4", "m4v" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            "mov" -> "video/quicktime"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "heic", "heif" -> "image/heic"
            else -> "image/jpeg"
        }
    }
}

private suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { if (cont.isActive) cont.resume(it) }
    addOnFailureListener { if (cont.isActive) cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}
