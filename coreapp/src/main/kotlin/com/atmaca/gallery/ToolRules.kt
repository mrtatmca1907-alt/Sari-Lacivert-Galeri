package com.atmaca.gallery

import kotlin.math.ceil

fun packageBatchSizes(total: Int, batchSize: Int): List<Int> {
    if (total <= 0) return emptyList()
    val safeBatch = batchSize.coerceAtLeast(1)
    val result = ArrayList<Int>((total + safeBatch - 1) / safeBatch)
    var remaining = total
    while (remaining > 0) {
        val size = minOf(safeBatch, remaining)
        result += size
        remaining -= size
    }
    return result
}

fun personCropName(sourceName: String, index: Int): String {
    val dot = sourceName.lastIndexOf('.')
    val base = if (dot > 0) sourceName.substring(0, dot) else sourceName
    val extension = if (dot > 0 && dot < sourceName.lastIndex) sourceName.substring(dot + 1) else "jpg"
    return "${base}_person_${index.coerceAtLeast(1)}.$extension"
}

fun frameCount(durationMs: Long, intervalMs: Long): Int {
    if (durationMs <= 0L || intervalMs <= 0L) return 0
    return ceil(durationMs.toDouble() / intervalMs.toDouble()).toInt()
}

fun frameName(videoName: String, sequence: Int): String {
    val dot = videoName.lastIndexOf('.')
    val base = if (dot > 0) videoName.substring(0, dot) else videoName
    return "$base ${sequence.coerceAtLeast(1)}.jpg"
}
