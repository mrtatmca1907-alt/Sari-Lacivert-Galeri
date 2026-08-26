package com.sarilacivert.galeri.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.LruCache
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

class BitmapLoader(private val context: Context) {
    private val resolver = context.contentResolver
    private val thumbCache = object : LruCache<String, Bitmap>(cacheSizeKb()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    suspend fun thumbnail(uri: Uri, sizePx: Int): Bitmap? = withContext(Dispatchers.IO) {
        val safeSize = sizePx.coerceIn(96, 512)
        val key = "${uri}_$safeSize"
        thumbCache.get(key)?.let { return@withContext it }
        val bitmap = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                resolver.loadThumbnail(uri, Size(safeSize, safeSize), null)
            } else {
                decodeSampled(uri, safeSize, safeSize)
            }
        }.getOrNull()
        if (bitmap != null) thumbCache.put(key, bitmap)
        bitmap
    }

    suspend fun full(uri: Uri, maxDimension: Int = 2560): Bitmap? = withContext(Dispatchers.IO) {
        runCatching { decodeSampled(uri, maxDimension, maxDimension) }.getOrNull()
    }

    fun clearMemory() {
        thumbCache.evictAll()
    }

    private fun decodeSampled(uri: Uri, reqWidth: Int, reqHeight: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (bounds.outWidth / sample > reqWidth * 2 || bounds.outHeight / sample > reqHeight * 2) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = max(1, sample)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }

    companion object {
        private fun cacheSizeKb(): Int {
            val maxKb = (Runtime.getRuntime().maxMemory() / 1024L).toInt()
            return (maxKb / 16).coerceIn(6 * 1024, 24 * 1024)
        }
    }
}
