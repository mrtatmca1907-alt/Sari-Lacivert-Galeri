package com.atmaca.gallery

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.PointF
import android.media.FaceDetector
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.coroutines.coroutineContext
import kotlin.math.max
import kotlin.math.min
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
        onProgress: (doneVideos: Int, totalVideos: Int) -> Unit = { _, _ -> }
    ): ToolRunResult = withContext(Dispatchers.IO) {
        val interval = frameIntervalMs(framesPerSecond)
        var created = 0
        var skipped = 0
        var failed = 0
        videos.forEachIndexed { videoIndex, uri ->
            coroutineContext.ensureActive()
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                val videoName = queryNameMime(uri).first.ifBlank { "video_${videoIndex + 1}.mp4" }
                val base = videoName.substringBeforeLast('.', videoName)
                val count = frameCount(duration, interval)
                if (count == 0) {
                    skipped++
                } else {
                    for (frameIndex in 0 until count) {
                        coroutineContext.ensureActive()
                        val timeUs = frameIndex.toLong() * interval * 1000L
                        val frame = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
                        if (frame == null) {
                            failed++
                            continue
                        }
                        val path = "Pictures/ATMACA Video Kareleri/${sanitizePathSegment(base)}/"
                        val ok = saveBitmapJpeg(frame, frameName(videoName, frameIndex + 1), path, 93)
                        frame.recycle()
                        if (ok) created++ else failed++
                    }
                }
            } catch (_: Throwable) {
                failed++
            } finally {
                runCatching { retriever.release() }
            }
            onProgress(videoIndex + 1, videos.size)
        }
        ToolRunResult(videos.size, created, skipped, failed)
    }

    /**
     * Independent on-device smart crop. Android's local FaceDetector is used as the
     * detector; source photos are never modified and every detected region is written
     * as a new JPEG under Pictures/ATMACA Kişi Kırpma/.
     */
    suspend fun smartPersonCrop(
        photos: List<Uri>,
        maxFacesPerPhoto: Int = 12,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }
    ): ToolRunResult = withContext(Dispatchers.Default) {
        var created = 0
        var skipped = 0
        var failed = 0
        photos.forEachIndexed { photoIndex, uri ->
            coroutineContext.ensureActive()
            val source = decodeBitmap(uri)
            if (source == null || source.width < 2 || source.height < 2) {
                failed++
                source?.recycle()
                onProgress(photoIndex + 1, photos.size)
                return@forEachIndexed
            }
            try {
                val detection = prepareFaceBitmap(source)
                val faces = arrayOfNulls<FaceDetector.Face>(maxFacesPerPhoto.coerceIn(1, 32))
                val detector = FaceDetector(detection.bitmap.width, detection.bitmap.height, faces.size)
                val found = runCatching { detector.findFaces(detection.bitmap, faces) }.getOrDefault(0)
                if (found <= 0) {
                    skipped++
                } else {
                    val sourceName = queryNameMime(uri).first.ifBlank { "photo_${photoIndex + 1}.jpg" }
                    repeat(found) { faceIndex ->
                        coroutineContext.ensureActive()
                        val face = faces[faceIndex] ?: return@repeat
                        val midpoint = PointF()
                        face.getMidPoint(midpoint)
                        val eyes = face.eyesDistance().coerceAtLeast(8f)
                        val scale = detection.scaleToSource
                        val cx = midpoint.x * scale
                        val cy = midpoint.y * scale
                        val e = eyes * scale
                        val left = (cx - e * 2.6f).roundToInt().coerceIn(0, source.width - 1)
                        val right = (cx + e * 2.6f).roundToInt().coerceIn(left + 1, source.width)
                        val top = (cy - e * 2.7f).roundToInt().coerceIn(0, source.height - 1)
                        val bottom = (cy + e * 3.7f).roundToInt().coerceIn(top + 1, source.height)
                        val crop = runCatching { Bitmap.createBitmap(source, left, top, right - left, bottom - top) }.getOrNull()
                        if (crop == null) {
                            failed++
                        } else {
                            val outputName = personCropName(sourceName.substringBeforeLast('.', sourceName) + ".jpg", faceIndex + 1)
                            val ok = withContext(Dispatchers.IO) {
                                saveBitmapJpeg(crop, outputName, "Pictures/ATMACA Kişi Kırpma/", 94)
                            }
                            crop.recycle()
                            if (ok) created++ else failed++
                        }
                    }
                }
                if (detection.bitmap !== source) detection.bitmap.recycle()
            } catch (_: Throwable) {
                failed++
            } finally {
                source.recycle()
            }
            onProgress(photoIndex + 1, photos.size)
        }
        ToolRunResult(photos.size, created, skipped, failed)
    }

    private data class DetectionBitmap(val bitmap: Bitmap, val scaleToSource: Float)

    private fun prepareFaceBitmap(source: Bitmap): DetectionBitmap {
        val maxEdge = 1600
        val ratio = max(source.width, source.height).toFloat() / maxEdge.toFloat()
        val scaled = if (ratio > 1f) {
            val w = (source.width / ratio).roundToInt().coerceAtLeast(2)
            val h = (source.height / ratio).roundToInt().coerceAtLeast(2)
            Bitmap.createScaledBitmap(source, w, h, true)
        } else source
        val evenWidth = if (scaled.width % 2 == 0) scaled.width else scaled.width - 1
        val detectorSized = if (evenWidth != scaled.width) {
            Bitmap.createBitmap(scaled, 0, 0, evenWidth.coerceAtLeast(2), scaled.height)
        } else scaled
        if (scaled !== source && detectorSized !== scaled) scaled.recycle()
        val rgb565 = if (detectorSized.config != Bitmap.Config.RGB_565) {
            detectorSized.copy(Bitmap.Config.RGB_565, false).also {
                if (detectorSized !== source) detectorSized.recycle()
            }
        } else detectorSized
        return DetectionBitmap(rgb565, source.width.toFloat() / rgb565.width.toFloat())
    }

    private fun decodeBitmap(uri: Uri): Bitmap? = runCatching {
        if (Build.VERSION.SDK_INT >= 28) {
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
        val projection = arrayOf(MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.MIME_TYPE)
        return runCatching {
            resolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use "" to null
                val name = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)) ?: ""
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
            val ok = resolver.openOutputStream(uri, "w")?.use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(70, 100), out) } == true
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

    private fun sanitizePathSegment(raw: String): String = raw
        .replace(Regex("[\\/:*?\"<>|]"), "_")
        .trim()
        .ifBlank { "Video" }
}
