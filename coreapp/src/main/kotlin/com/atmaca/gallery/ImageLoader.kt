package com.atmaca.gallery

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.ceil
import kotlin.math.sqrt

suspend fun loadHighResolutionBitmap(
    context: Context,
    item: GalleryMedia,
    maxPixels: Long = 24_000_000L
): Bitmap? = withContext(Dispatchers.IO) {
    runCatching {
        if (Build.VERSION.SDK_INT >= 28) {
            val source = ImageDecoder.createSource(context.contentResolver, item.uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = false
                val pixels = info.size.width.toLong() * info.size.height.toLong()
                if (pixels > maxPixels) {
                    val ratio = sqrt(pixels.toDouble() / maxPixels.toDouble())
                    val sample = ceil(ratio).toInt().coerceAtLeast(1)
                    decoder.setTargetSampleSize(sample)
                }
            }
        } else {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(item.uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            val pixels = bounds.outWidth.toLong().coerceAtLeast(1) * bounds.outHeight.toLong().coerceAtLeast(1)
            var sample = 1
            while (pixels / (sample.toLong() * sample.toLong()) > maxPixels) sample *= 2
            val options = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            resolver.openInputStream(item.uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        }
    }.getOrNull()
}
