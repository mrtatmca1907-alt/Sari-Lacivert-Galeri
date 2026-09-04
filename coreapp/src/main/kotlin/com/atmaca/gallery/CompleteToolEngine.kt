package com.atmaca.gallery

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Rect
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/** Result shared by the three isolated ATMACA media tools. */
data class ToolRunResult(
    val processed: Int,
    val created: Int,
    val skipped: Int,
    val failed: Int
)

class CompleteToolEngine(private val context: Context) {
    private val resolver = context.applicationContext.contentResolver

    suspend fun packageMedia(
        uris: List<Uri>,
        batchSize: Int = 50,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }
    ): ToolRunResult = withContext(Dispatchers.IO) {
        val safeBatch = batchSize.coerceIn(1, 1000)
        var created = 0
        var skipped = 0
        var failed = 0
        uris.forEachIndexed { index, uri ->
            coroutineContext.ensureActive()
            val batch = index / safeBatch + 1
            val meta = queryNameMime(uri)
            if (meta.first.isBlank()) {
                skipped++
            } else {
                val ok = copyUriToMediaStore(
                    source = uri,
                    displayName = meta.first,
                    mimeType = meta.second,
                    relativePath = packageBatchPath(batch)
                )
                if (ok) created++ else failed++
            }
            onProgress(index + 1, uris.size)
        }
        ToolRunResult(uris.size, created, skipped, failed)
    }

    suspend fun extractVideoFrames(
        videos: List<Uri>,
        framesPerSecond: Int = 1,
        moveSourceAfterSuccess: Boolean = false,
        onProgress: (doneFrames: Int, totalFrames: Int) -> Unit = { _, _ -> }
    ): ToolRunResult = withContext(Dispatchers.IO) {
        val interval = frameIntervalMs(framesPerSecond)
        data class FramePlan(val uri: Uri, val name: String, val count: Int)
        val plans = ArrayList<FramePlan>(videos.size)
        var skipped = 0
        var failed = 0

        videos.forEachIndexed { index, uri ->
            coroutineContext.ensureActive()
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                val name = queryNameMime(uri).first.ifBlank { "video_${index + 1}.mp4" }
                val count = frameCount(duration, interval)
                if (count <= 0) skipped++ else plans += FramePlan(uri, name, count)
            } catch (_: Throwable) {
                failed++
            } finally {
                runCatching { retriever.release() }
            }
        }

        val totalFrames = plans.sumOf { it.count }
        var doneFrames = 0
        var created = 0
        onProgress(0, totalFrames)

        plans.forEach { plan ->
            coroutineContext.ensureActive()
            val retriever = MediaMetadataRetriever()
            var videoCreated = 0
            var videoFailed = 0
            val outputPath = videoFrameOutputPath(plan.name)
            try {
                retriever.setDataSource(context, plan.uri)
                for (frameIndex in 0 until plan.count) {
                    coroutineContext.ensureActive()
                    val timeUs = frameIndex.toLong() * interval * 1000L
                    val frame = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
                    if (frame == null) {
                        failed++
                        videoFailed++
                    } else {
                        val ok = saveBitmapJpeg(frame, frameName(plan.name, frameIndex + 1), outputPath, 93)
                        frame.recycle()
                        if (ok) {
                            created++
                            videoCreated++
                        } else {
                            failed++
                            videoFailed++
                        }
                    }
                    doneFrames++
                    onProgress(doneFrames, totalFrames)
                }
            } catch (_: Throwable) {
                failed++
                videoFailed++
            } finally {
                runCatching { retriever.release() }
            }

            if (moveSourceAfterSuccess && shouldMoveVideoAfterFrames(videoCreated, videoFailed)) {
                if (!moveVideoToOutputFolder(plan.uri, outputPath)) failed++
            }
        }
        ToolRunResult(videos.size, created, skipped, failed)
    }

    /**
     * On-device smart crop using bundled ML Kit face detection. The detector works on
     * a bounded preview bitmap; detections are mapped back to source coordinates and
     * expanded to include head/shoulders/upper body. Every source gets its own
     * result folder; crops are written first and the original is moved last.
     */
    suspend fun smartPersonCrop(
        photos: List<Uri>,
        maxFacesPerPhoto: Int = 12,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }
    ): ToolRunResult = withContext(Dispatchers.Default) {
        val detector = createFaceDetector()
        var created = 0
        var skipped = 0
        var failed = 0
        try {
            photos.forEachIndexed { photoIndex, uri ->
                coroutineContext.ensureActive()
                val sourceName = queryNameMime(uri).first.ifBlank { "photo_${photoIndex + 1}.jpg" }
                val sourceBase = sanitizePathSegment(sourceName.substringBeforeLast('.', sourceName))
                val outputPath = "Pictures/ATMACA Kişi Kırpma/${sourceBase}_${photoIndex + 1}/"
                val failedBeforePhoto = failed
                val source = decodeBitmap(uri)
                if (source == null || source.width < 2 || source.height < 2) {
                    failed++
                    source?.recycle()
                    onProgress(photoIndex + 1, photos.size)
                    return@forEachIndexed
                }

                var detectionBitmap: Bitmap? = null
                try {
                    val prepared = prepareDetectionBitmap(source)
                    detectionBitmap = prepared.bitmap
                    val faces = detectFaces(detector, prepared.bitmap)
                        .sortedByDescending { it.boundingBox.width().toLong() * it.boundingBox.height().toLong() }
                        .take(maxFacesPerPhoto.coerceIn(1, 32))

                    if (faces.isEmpty()) {
                        skipped++
                    } else {
                        faces.forEachIndexed { faceIndex, face ->
                            coroutineContext.ensureActive()
                            val box = scaleRectToSource(face.boundingBox, prepared.scaleToSource)
                            val bounds = personCropBounds(
                                sourceWidth = source.width,
                                sourceHeight = source.height,
                                faceLeft = box.left,
                                faceTop = box.top,
                                faceRight = box.right,
                                faceBottom = box.bottom
                            )
                            if (bounds.width <= 0 || bounds.height <= 0) {
                                failed++
                                return@forEachIndexed
                            }
                            val crop = runCatching {
                                Bitmap.createBitmap(source, bounds.left, bounds.top, bounds.width, bounds.height)
                            }.getOrNull()
                            if (crop == null) {
                                failed++
                            } else {
                                val outputName = personCropName(
                                    sourceName.substringBeforeLast('.', sourceName) + ".jpg",
                                    faceIndex + 1
                                )
                                val ok = withContext(Dispatchers.IO) {
                                    saveBitmapJpeg(crop, outputName, outputPath, 94)
                                }
                                crop.recycle()
                                if (ok) created++ else failed++
                            }
                        }
                    }
                } catch (_: Throwable) {
                    failed++
                } finally {
                    if (detectionBitmap != null && detectionBitmap !== source && !detectionBitmap!!.isRecycled) {
                        detectionBitmap!!.recycle()
                    }
                    source.recycle()
                }
                // Orijinali ancak bu fotoğrafa ait bütün kırpmalar başarıyla
                // yazıldıktan sonra taşı. Böylece yarım işlemde kaynak kaybolmaz.
                if (failed == failedBeforePhoto) {
                    val moved = withContext(Dispatchers.IO) { moveImageToOutputFolder(uri, outputPath) }
                    if (moved) created++ else failed++
                }
                onProgress(photoIndex + 1, photos.size)
            }
        } finally {
            detector.close()
        }
        ToolRunResult(photos.size, created, skipped, failed)
    }

    private fun createFaceDetector(): FaceDetector {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setMinFaceSize(0.05f)
            .build()
        return FaceDetection.getClient(options)
    }

    private suspend fun detectFaces(detector: FaceDetector, bitmap: Bitmap): List<Face> =
        suspendCancellableCoroutine { continuation ->
            detector.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener { faces ->
                    if (continuation.isActive) continuation.resume(faces)
                }
                .addOnFailureListener { error ->
                    if (continuation.isActive) continuation.resumeWithException(error)
                }
        }

    private data class DetectionBitmap(val bitmap: Bitmap, val scaleToSource: Float)

    private fun prepareDetectionBitmap(source: Bitmap): DetectionBitmap {
        val maxEdge = 1600
        val edge = max(source.width, source.height)
        if (edge <= maxEdge) return DetectionBitmap(source, 1f)
        val ratio = edge.toFloat() / maxEdge.toFloat()
        val width = (source.width / ratio).roundToInt().coerceAtLeast(2)
        val height = (source.height / ratio).roundToInt().coerceAtLeast(2)
        val scaled = Bitmap.createScaledBitmap(source, width, height, true)
        return DetectionBitmap(scaled, source.width.toFloat() / scaled.width.toFloat())
    }

    private fun scaleRectToSource(rect: Rect, scale: Float): Rect = Rect(
        (rect.left * scale).roundToInt(),
        (rect.top * scale).roundToInt(),
        (rect.right * scale).roundToInt(),
        (rect.bottom * scale).roundToInt()
    )

    private fun decodeBitmap(uri: Uri): Bitmap? = runCatching {
        if (uri.scheme.equals("file", true)) {
            val path = uri.path ?: return@runCatching null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            val pixels = bounds.outWidth.toLong().coerceAtLeast(0L) * bounds.outHeight.toLong().coerceAtLeast(0L)
            val edge = max(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
            val sample = if (pixels > 24_000_000L) (edge / 5000).coerceAtLeast(1) else 1
            BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
        } else if (Build.VERSION.SDK_INT >= 28) {
            val source = ImageDecoder.createSource(resolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val pixels = info.size.width.toLong() * info.size.height.toLong()
                if (pixels > 24_000_000L) {
                    val edge = max(info.size.width, info.size.height)
                    val sample = (edge / 5000).coerceAtLeast(1)
                    decoder.setTargetSampleSize(sample)
                }
            }
        } else {
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        }
    }.getOrNull()

    private fun queryNameMime(uri: Uri): Pair<String, String?> {
        if (uri.scheme.equals("file", true)) {
            val file = uri.path?.let(::File)
            return (file?.name.orEmpty()) to null
        }
        val projection = arrayOf(MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.MIME_TYPE)
        return runCatching {
            resolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use "" to null
                val nameIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                val name = if (nameIndex >= 0) cursor.getString(nameIndex).orEmpty() else ""
                val mimeIndex = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
                name to if (mimeIndex >= 0) cursor.getString(mimeIndex) else null
            } ?: ("" to null)
        }.getOrDefault("" to null)
    }

    private fun copyUriToMediaStore(source: Uri, displayName: String, mimeType: String?, relativePath: String): Boolean {
        if (Build.VERSION.SDK_INT < 29) return false
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType ?: "application/octet-stream")
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val collection = MediaStore.Files.getContentUri("external")
        val output = resolver.insert(collection, values) ?: return false
        return try {
            val copied = resolver.openInputStream(source)?.use { input ->
                resolver.openOutputStream(output, "w")?.use { out -> input.copyTo(out); true } ?: false
            } ?: false
            if (copied) {
                values.clear(); values.put(MediaStore.MediaColumns.IS_PENDING, 0); resolver.update(output, values, null, null)
                true
            } else {
                resolver.delete(output, null, null); false
            }
        } catch (_: Throwable) {
            runCatching { resolver.delete(output, null, null) }
            false
        }
    }

    private fun saveBitmapJpeg(bitmap: Bitmap, displayName: String, relativePath: String, quality: Int): Boolean {
        if (Build.VERSION.SDK_INT < 29) return false
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, relativePath)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        return try {
            val ok = resolver.openOutputStream(uri, "w")?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(70, 100), out)
            } == true
            if (ok) {
                values.clear(); values.put(MediaStore.Images.Media.IS_PENDING, 0); resolver.update(uri, values, null, null)
                true
            } else {
                resolver.delete(uri, null, null); false
            }
        } catch (_: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            false
        }
    }

    private fun moveVideoToOutputFolder(source: Uri, outputPath: String): Boolean {
        if (source.scheme.equals("file", true)) return moveDirectFileToOutput(source, outputPath)
        if (Build.VERSION.SDK_INT < 29) return false
        val mediaUri = resolveVideoMediaStoreUri(source) ?: return false
        return runCatching {
            resolver.update(
                mediaUri,
                ContentValues().apply { put(MediaStore.MediaColumns.RELATIVE_PATH, outputPath) },
                null,
                null
            ) > 0
        }.getOrDefault(false)
    }

    private fun moveImageToOutputFolder(source: Uri, outputPath: String): Boolean {
        if (source.scheme.equals("file", true)) return moveDirectFileToOutput(source, outputPath)
        if (Build.VERSION.SDK_INT < 29) return false
        val mediaUri = resolveImageMediaStoreUri(source) ?: return false
        return runCatching {
            resolver.update(
                mediaUri,
                ContentValues().apply { put(MediaStore.MediaColumns.RELATIVE_PATH, outputPath) },
                null,
                null
            ) > 0
        }.getOrDefault(false)
    }

    private fun moveDirectFileToOutput(source: Uri, outputPath: String): Boolean = runCatching {
        val sourceFile = source.path?.let(::File) ?: return@runCatching false
        if (!sourceFile.isFile) return@runCatching false
        val root = Environment.getExternalStorageDirectory()
        val outputDir = File(root, outputPath.trimStart('/'))
        if (!outputDir.exists() && !outputDir.mkdirs()) return@runCatching false
        var target = File(outputDir, sourceFile.name)
        if (target.canonicalPath == sourceFile.canonicalPath) return@runCatching true
        if (target.exists()) {
            val base = sourceFile.nameWithoutExtension
            val ext = sourceFile.extension.takeIf { it.isNotBlank() }?.let { ".$it" }.orEmpty()
            var suffix = 2
            while (target.exists()) {
                target = File(outputDir, "${base}_orijinal_${suffix}${ext}")
                suffix++
            }
        }
        sourceFile.renameTo(target) || run {
            sourceFile.inputStream().use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            if (target.length() != sourceFile.length()) {
                target.delete()
                false
            } else {
                sourceFile.delete()
            }
        }
    }.getOrDefault(false)

    private fun resolveImageMediaStoreUri(source: Uri): Uri? {
        if (source.authority == MediaStore.AUTHORITY) return source
        if (source.authority == "com.android.providers.media.documents") {
            val docId = runCatching { DocumentsContract.getDocumentId(source) }.getOrNull() ?: return null
            val parts = docId.split(':', limit = 2)
            if (parts.size == 2 && parts[0].equals("image", true)) {
                val id = parts[1].toLongOrNull() ?: return null
                return ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
            }
        }
        if (source.authority == "com.android.externalstorage.documents") {
            val docId = runCatching { DocumentsContract.getDocumentId(source) }.getOrNull() ?: return null
            val relative = docId.substringAfter(':', "").trimStart('/')
            if (relative.isBlank()) return null
            val name = relative.substringAfterLast('/')
            val folder = relative.substringBeforeLast('/', "")
            val relativePath = if (folder.isBlank()) "" else "$folder/"
            val projection = arrayOf(MediaStore.Images.Media._ID)
            val selection = if (relativePath.isBlank()) {
                "${MediaStore.Images.Media.DISPLAY_NAME}=?"
            } else {
                "${MediaStore.Images.Media.DISPLAY_NAME}=? AND ${MediaStore.Images.Media.RELATIVE_PATH}=?"
            }
            val args = if (relativePath.isBlank()) arrayOf(name) else arrayOf(name, relativePath)
            return runCatching {
                resolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, selection, args, null)?.use { cursor ->
                    if (!cursor.moveToFirst()) return@use null
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                    ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                }
            }.getOrNull()
        }
        return null
    }

    private fun resolveVideoMediaStoreUri(source: Uri): Uri? {
        if (source.authority == MediaStore.AUTHORITY) return source

        if (source.authority == "com.android.providers.media.documents") {
            val docId = runCatching { DocumentsContract.getDocumentId(source) }.getOrNull() ?: return null
            val parts = docId.split(':', limit = 2)
            if (parts.size == 2 && parts[0].equals("video", true)) {
                val id = parts[1].toLongOrNull() ?: return null
                return ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
            }
        }

        if (source.authority == "com.android.externalstorage.documents") {
            val docId = runCatching { DocumentsContract.getDocumentId(source) }.getOrNull() ?: return null
            val relative = docId.substringAfter(':', "").trimStart('/')
            if (relative.isBlank()) return null
            val name = relative.substringAfterLast('/')
            val folder = relative.substringBeforeLast('/', "")
            val relativePath = if (folder.isBlank()) "" else "$folder/"
            val projection = arrayOf(MediaStore.Video.Media._ID)
            val selection = if (relativePath.isBlank()) {
                "${MediaStore.Video.Media.DISPLAY_NAME}=?"
            } else {
                "${MediaStore.Video.Media.DISPLAY_NAME}=? AND ${MediaStore.Video.Media.RELATIVE_PATH}=?"
            }
            val args = if (relativePath.isBlank()) arrayOf(name) else arrayOf(name, relativePath)
            return runCatching {
                resolver.query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, projection, selection, args, null)?.use { cursor ->
                    if (!cursor.moveToFirst()) return@use null
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID))
                    ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                }
            }.getOrNull()
        }
        return null
    }

    private fun sanitizePathSegment(raw: String): String = raw
        .replace(Regex("[\\/:*?\"<>|]"), "_")
        .trim()
        .ifBlank { "Video" }
}
