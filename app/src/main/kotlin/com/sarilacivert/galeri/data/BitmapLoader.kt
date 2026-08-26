package com.sarilacivert.galeri.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.LruCache
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlin.math.max

class BitmapLoader(private val context: Context) {
    private val resolver = context.contentResolver

    // Binlerce dosyalı klasörlerde hızlı kaydırırken onlarca decode aynı anda başlarsa
    // telefon RAM/IO tarafında boğuluyor. Görünür hücreler için 4 paralel küçük resim,
    // tam ekran için en fazla 1 ağır decode yeterli.
    private val thumbGate = Semaphore(4)
    private val fullGate = Semaphore(1)

    private val thumbCache = object : LruCache<String, Bitmap>(cacheSizeKb()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    suspend fun thumbnail(uri: Uri, sizePx: Int): Bitmap? = withContext(Dispatchers.IO) {
        val safeSize = sizePx.coerceIn(96, 384)
        val key = "${uri}_$safeSize"
        thumbCache.get(key)?.let { return@withContext it }

        thumbGate.withPermit {
            // Sırada beklerken başka hücre aynı resmi yüklemiş olabilir.
            thumbCache.get(key)?.let { return@withPermit it }

            val bitmap = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    resolver.loadThumbnail(uri, Size(safeSize, safeSize), null)
                } else {
                    decodeSampled(uri, safeSize, safeSize, lowMemory = true)
                }
            }.getOrNull()

            if (bitmap != null) thumbCache.put(key, bitmap)
            bitmap
        }
    }

    suspend fun full(uri: Uri, maxDimension: Int = 3072): Bitmap? = withContext(Dispatchers.IO) {
        fullGate.withPermit {
            runCatching { decodeSampled(uri, maxDimension, maxDimension, lowMemory = false) }.getOrNull()
        }
    }

    fun clearMemory() {
        thumbCache.evictAll()
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
        private fun cacheSizeKb(): Int {
            val maxKb = (Runtime.getRuntime().maxMemory() / 1024L).toInt()
            // Eski sürüm 24 MB'a kadar küçük resim cache'i tutuyordu. Büyük galeride
            // bu, tam ekran foto + Compose ile birleşince GC kasmasına dönüşebiliyordu.
            return (maxKb / 24).coerceIn(4 * 1024, 12 * 1024)
        }
    }
}
