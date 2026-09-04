package com.atmaca.gallery

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.os.Build
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.ceil
import kotlin.math.sqrt

private object ViewerBitmapCache : LruCache<String, Bitmap>(96 * 1024) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
}

private fun viewerBitmapKey(item: GalleryMedia, viewportWidth: Int, viewportHeight: Int): String =
    "${item.uri}|${item.dateModified}|${item.size}|${viewportWidth.coerceAtLeast(1)}x${viewportHeight.coerceAtLeast(1)}"

suspend fun prefetchViewerBitmap(
    context: Context,
    item: GalleryMedia,
    viewportWidth: Int,
    viewportHeight: Int
) {
    if (item.isVideo) return
    loadHighResolutionBitmap(context, item, viewportWidth = viewportWidth, viewportHeight = viewportHeight)
}

suspend fun loadHighResolutionBitmap(
    context: Context,
    item: GalleryMedia,
    maxPixels: Long = 12_000_000L,
    viewportWidth: Int = 0,
    viewportHeight: Int = 0
): Bitmap? = withContext(Dispatchers.IO) {
    val key = viewerBitmapKey(item, viewportWidth, viewportHeight)
    ViewerBitmapCache.get(key)?.let { cached ->
        if (!cached.isRecycled) return@withContext cached
        ViewerBitmapCache.remove(key)
    }

    val decoded = runCatching {
        if (Build.VERSION.SDK_INT >= 28) {
            val source = ImageDecoder.createSource(context.contentResolver, item.uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = false
                val pixels = info.size.width.toLong() * info.size.height.toLong()
                val pixelSample = if (pixels > maxPixels) {
                    ceil(sqrt(pixels.toDouble() / maxPixels.toDouble())).toInt().coerceAtLeast(1)
                } else 1
                val viewportSample = calculateViewerDecodeSample(
                    info.size.width,
                    info.size.height,
                    viewportWidth,
                    viewportHeight
                )
                val sample = maxOf(pixelSample, viewportSample)
                if (sample > 1) decoder.setTargetSampleSize(sample)
            }
        } else {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(item.uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            val pixels = bounds.outWidth.toLong().coerceAtLeast(1) * bounds.outHeight.toLong().coerceAtLeast(1)
            var pixelSample = 1
            while (pixels / (pixelSample.toLong() * pixelSample.toLong()) > maxPixels) pixelSample *= 2
            val viewportSample = calculateViewerDecodeSample(
                bounds.outWidth,
                bounds.outHeight,
                viewportWidth,
                viewportHeight
            )
            val options = BitmapFactory.Options().apply {
                inSampleSize = maxOf(pixelSample, viewportSample)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            resolver.openInputStream(item.uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        }
    }.getOrNull()

    if (decoded != null && !decoded.isRecycled) ViewerBitmapCache.put(key, decoded)
    decoded
}
