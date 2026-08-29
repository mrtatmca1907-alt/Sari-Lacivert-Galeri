package com.sarilacivert.galeri.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.LruCache
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlin.math.max

class BitmapLoader(private val context: Context) {
    private val resolver = context.contentResolver

    // Görünür grid hücreleri için sınırlı paralellik; tam ekran decode tek seferde bir tane.
    // Böylece hızlı kaydırmada RAM/IO fırtınası oluşmuyor.
    private val thumbGate = Semaphore(4)
    private val fullGate = Semaphore(1)

    suspend fun thumbnail(uri: Uri, sizePx: Int): Bitmap? = withContext(Dispatchers.IO) {
        val safeSize = BitmapSizingPolicy.safeThumbnailSize(sizePx)
        val key = "${uri}_$safeSize"
        thumbCache.get(key)?.let { return@withContext it }

        thumbGate.withPermit {
            coroutineContext.ensureActive()
            thumbCache.get(key)?.let { return@withPermit it }

            val bitmap = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    resolver.loadThumbnail(uri, Size(safeSize, safeSize), null)
                } else {
                    decodeSampled(uri, safeSize, safeSize, lowMemory = true)
                }
            }.getOrNull()

            coroutineContext.ensureActive()
            if (bitmap != null) thumbCache.put(key, bitmap)
            bitmap
        }
    }

    suspend fun full(uri: Uri, maxDimension: Int = defaultViewerTarget()): Bitmap? = withContext(Dispatchers.IO) {
        fullGate.withPermit {
            coroutineContext.ensureActive()
            runCatching {
                decodeSampled(uri, maxDimension.coerceIn(1536, 4096), maxDimension.coerceIn(1536, 4096), lowMemory = false)
            }.getOrNull()
        }
    }

    fun clearMemory() {
        clearGlobalMemory()
    }

    private fun defaultViewerTarget(): Int {
        val metrics = context.resources.displayMetrics
        return BitmapSizingPolicy.viewerTarget(max(metrics.widthPixels, metrics.heightPixels))
    }

    private fun decodeSampled(uri: Uri, reqWidth: Int, reqHeight: Int, lowMemory: Boolean): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (bounds.outWidth / sample > reqWidth * 2 || bounds.outHeight / sample > reqHeight * 2) {
            sample *= 2
        }

        val options = BitmapFactory.Options().apply {
            inSampleSize = max(1, sample)
            inPreferredConfig = if (lowMemory) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888
        }
        return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }

    companion object {
        // Tek process cache'i kullanmak, ekran yeniden yaratıldığında aynı küçük resimlerin
        // tekrar tekrar decode edilmesini azaltır. Üst sınır özellikle düşük RAM cihazlar için muhafazakâr.
        private val thumbCache = object : LruCache<String, Bitmap>(cacheSizeKb()) {
            override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
        }

        fun clearGlobalMemory() {
            thumbCache.evictAll()
        }

        private fun cacheSizeKb(): Int {
            val maxKb = (Runtime.getRuntime().maxMemory() / 1024L).toInt()
            return (maxKb / 24).coerceIn(4 * 1024, 12 * 1024)
        }
    }
}
