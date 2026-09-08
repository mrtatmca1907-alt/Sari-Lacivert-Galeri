package com.sarilacivert.galeri.ui

internal object GestureMath {
    fun shortestAngleDelta(fromDegrees: Float, toDegrees: Float): Float {
        var delta = (toDegrees - fromDegrees) % 360f
        if (delta > 180f) delta -= 360f
        if (delta < -180f) delta += 360f
        return delta
    }

    fun normalizeRotation(degrees: Float): Float {
        val normalized = degrees % 360f
        return if (normalized < 0f) normalized + 360f else normalized
    }
}
