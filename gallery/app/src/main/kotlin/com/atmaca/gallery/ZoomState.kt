package com.atmaca.gallery

class ZoomState(
    private val minScale: Float = 1f,
    private val maxScale: Float = 5f,
    private val doubleTapScale: Float = 2.5f
) {
    var scale: Float = minScale
        private set

    fun onScale(factor: Float): Float {
        scale = (scale * factor).coerceIn(minScale, maxScale)
        return scale
    }

    fun onDoubleTap(): Float {
        scale = if (scale > minScale + 0.01f) minScale else doubleTapScale.coerceAtMost(maxScale)
        return scale
    }

    fun reset(): Float {
        scale = minScale
        return scale
    }
}
