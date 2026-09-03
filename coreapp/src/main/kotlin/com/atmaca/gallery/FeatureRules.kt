package com.atmaca.gallery

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

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

data class ViewerPanBounds(val maxX: Float, val maxY: Float)

enum class CropRatio(val ratio: Float) {
    FREE(0f),
    SQUARE(1f),
    FOUR_THREE(4f / 3f),
    SIXTEEN_NINE(16f / 9f)
}

fun clampViewerScale(scale: Float): Float = scale.coerceIn(1f, 4f)

/**
 * Gallery-style pinch response: follow the fingers nearly 1:1, but reject
 * pathological one-frame jumps that make the image feel springy or unstable.
 */
fun galleryZoomFactor(rawFactor: Float): Float = rawFactor.coerceIn(0.72f, 1.35f)

// Kept for callers from older builds; new viewer uses galleryZoomFactor.
fun dampedZoomFactor(rawFactor: Float): Float = galleryZoomFactor(rawFactor)

fun nextDoubleTapScale(scale: Float): Float = if (scale > 1.1f) 1f else 2.25f

/** Keeps the point under the pinch focus visually anchored while scaling. */
fun zoomOffsetAroundFocus(
    oldOffset: Float,
    focusFromCenter: Float,
    oldScale: Float,
    newScale: Float
): Float {
    if (oldScale <= 0f) return oldOffset
    val ratio = newScale / oldScale
    return oldOffset + focusFromCenter * (1f - ratio)
}

fun shouldEnablePager(scale: Float): Boolean = scale <= 1.001f

fun shouldEnablePager(scale: Float, rotation: Float): Boolean =
    scale <= 1.001f && (normalizeViewerRotation(rotation) < 0.5f || normalizeViewerRotation(rotation) > 359.5f)

fun shouldShowViewerControls(scale: Float, gestureActive: Boolean): Boolean =
    !gestureActive && scale <= 1.001f

fun shouldRenderViewerChrome(
    captureInProgress: Boolean,
    controlsVisible: Boolean,
    scale: Float,
    gestureActive: Boolean
): Boolean =
    !captureInProgress && controlsVisible && shouldShowViewerControls(scale, gestureActive)

fun normalizeViewerRotation(rotation: Float): Float = ((rotation % 360f) + 360f) % 360f

fun applyViewerRotationDelta(current: Float, delta: Float): Float =
    normalizeViewerRotation(current + delta)

fun viewerPanBounds(
    viewportWidth: Float,
    viewportHeight: Float,
    imageWidth: Float,
    imageHeight: Float,
    scale: Float,
    rotation: Float
): ViewerPanBounds {
    if (viewportWidth <= 0f || viewportHeight <= 0f || imageWidth <= 0f || imageHeight <= 0f) {
        return ViewerPanBounds(0f, 0f)
    }

    val fit = minOf(viewportWidth / imageWidth, viewportHeight / imageHeight)
    val fittedWidth = imageWidth * fit
    val fittedHeight = imageHeight * fit
    val safeScale = clampViewerScale(scale)
    val angle = normalizeViewerRotation(rotation) * PI.toFloat() / 180f
    val c = abs(cos(angle))
    val s = abs(sin(angle))
    val rotatedWidth = (fittedWidth * c + fittedHeight * s) * safeScale
    val rotatedHeight = (fittedWidth * s + fittedHeight * c) * safeScale

    return ViewerPanBounds(
        maxX = ((rotatedWidth - viewportWidth) / 2f).coerceAtLeast(0f),
        maxY = ((rotatedHeight - viewportHeight) / 2f).coerceAtLeast(0f)
    )
}

fun clampViewerOffset(offset: Float, maxOffset: Float): Float =
    offset.coerceIn(-maxOffset.coerceAtLeast(0f), maxOffset.coerceAtLeast(0f))

fun nextQuarterRotation(rotation: Float): Float = normalizeViewerRotation(rotation + 90f)

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
