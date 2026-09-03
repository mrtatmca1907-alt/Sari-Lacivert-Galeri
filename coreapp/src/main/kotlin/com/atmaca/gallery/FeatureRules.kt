package com.atmaca.gallery

data class MediaMeta(
    val id: Long,
    val relativePath: String,
    val size: Long,
    val mimeType: String?
)

data class AlbumSummary(
    val relativePath: String,
    val count: Int
)

data class NormalizedCropRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

enum class CropRatio(val ratio: Float) {
    FREE(0f),
    SQUARE(1f),
    FOUR_THREE(4f / 3f),
    SIXTEEN_NINE(16f / 9f)
}

fun clampViewerScale(scale: Float): Float = scale.coerceIn(1f, 8f)

fun nextDoubleTapScale(scale: Float): Float = if (scale > 1.1f) 1f else 2.5f

fun nextQuarterRotation(rotation: Float): Float = (rotation + 90f) % 360f

fun normalizedCropRect(left: Float, top: Float, right: Float, bottom: Float): NormalizedCropRect {
    val l = minOf(left, right).coerceIn(0f, 1f)
    val r = maxOf(left, right).coerceIn(0f, 1f)
    val t = minOf(top, bottom).coerceIn(0f, 1f)
    val b = maxOf(top, bottom).coerceIn(0f, 1f)
    return NormalizedCropRect(l, t, r, b)
}

fun groupAlbums(items: List<MediaMeta>): List<AlbumSummary> =
    items
        .groupBy { normalizeRelativePath(it.relativePath) }
        .map { (path, media) -> AlbumSummary(path, media.size) }
        .sortedBy { it.relativePath.lowercase() }

fun duplicateCandidateGroups(items: List<MediaMeta>): List<List<MediaMeta>> =
    items
        .asSequence()
        .filter { it.size > 0L }
        .groupBy { it.size }
        .values
        .asSequence()
        .filter { it.size > 1 }
        .map { it.sortedBy(MediaMeta::id) }
        .sortedBy { it.first().size }
        .toList()

fun normalizeRelativePath(raw: String): String {
    val parts = raw.trim()
        .replace('\\', '/')
        .split('/')
        .map(String::trim)
        .filter(String::isNotEmpty)
    val normalized = if (parts.isEmpty()) "Pictures/ATMACA" else parts.joinToString("/")
    return "$normalized/"
}

fun albumDisplayName(relativePath: String): String =
    normalizeRelativePath(relativePath).trimEnd('/').substringAfterLast('/').ifBlank { "Depolama" }
