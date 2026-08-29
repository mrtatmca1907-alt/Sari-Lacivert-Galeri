package com.sarilacivert.galeri.data

object BitmapSizingPolicy {
    fun safeThumbnailSize(requested: Int): Int = requested.coerceIn(96, 512)

    fun viewerTarget(viewportMax: Int): Int =
        (viewportMax * 2).coerceIn(1536, 4096)
}
