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
