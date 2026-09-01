package com.atmaca.filemanager.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ThumbnailUtils
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File

class ThumbnailLoader(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    maxConcurrent: Int = 3
) {
    private val semaphore = Semaphore(maxConcurrent)
    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    suspend fun load(file: File, sizePx: Int = 180): Bitmap? {
        val key = "${file.absolutePath}:${file.lastModified()}:$sizePx"
        cache.get(key)?.let { return it }
        return semaphore.withPermit {
            cache.get(key)?.let { return@withPermit it }
            val bitmap = withContext(ioDispatcher) {
                when {
                    isImage(file) -> decodeSampled(file, sizePx)
                    isVideo(file) -> videoThumbnail(file, sizePx)
                    else -> null
                }
            }
            bitmap?.let { cache.put(key, it) }
            bitmap
        }
    }

    private fun decodeSampled(file: File, sizePx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > sizePx * 2 || bounds.outHeight / sample > sizePx * 2) sample *= 2
        return BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565
        })
    }

    @Suppress("DEPRECATION")
    private fun videoThumbnail(file: File, sizePx: Int): Bitmap? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching { ThumbnailUtils.createVideoThumbnail(file, Size(sizePx, sizePx), null) }.getOrNull()
        } else {
            ThumbnailUtils.createVideoThumbnail(file.absolutePath, MediaStore.Video.Thumbnails.MINI_KIND)
        }

    private fun isImage(file: File): Boolean = file.extension.lowercase() in setOf(
        "jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif", "avif"
    )

    private fun isVideo(file: File): Boolean = file.extension.lowercase() in setOf(
        "mp4", "mkv", "avi", "mov", "webm", "3gp", "m4v", "ts"
    )
}
