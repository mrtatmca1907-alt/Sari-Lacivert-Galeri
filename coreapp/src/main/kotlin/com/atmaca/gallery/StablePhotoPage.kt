package com.atmaca.gallery

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.abs
import kotlin.math.hypot

@Composable
fun StablePhotoPage(
    item: GalleryMedia,
    rotation: Float,
    onRotationChanged: (Float) -> Unit,
    onScaleChanged: (Float) -> Unit,
    onGestureActive: (Boolean) -> Unit,
    onFitTap: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    var scale by remember(item.id) { mutableFloatStateOf(1f) }
    var localRotation by remember(item.id) { mutableFloatStateOf(rotation) }
    var offsetX by remember(item.id) { mutableFloatStateOf(0f) }
    var offsetY by remember(item.id) { mutableFloatStateOf(0f) }
    var viewportWidth by remember(item.id) { mutableIntStateOf(0) }
    var viewportHeight by remember(item.id) { mutableIntStateOf(0) }
    val lastTapTime = remember(item.id) { longArrayOf(0L) }
    val lastTapPosition = remember(item.id) { floatArrayOf(0f, 0f) }

    LaunchedEffect(rotation, item.id) {
        if (abs(normalizeViewerRotation(rotation) - normalizeViewerRotation(localRotation)) > 0.5f) localRotation = rotation
    }

    val bitmap by produceState<Bitmap?>(
        initialValue = null,
        item.uri,
        viewportWidth,
        viewportHeight
    ) {
        if (viewportWidth > 0 && viewportHeight > 0) {
            value = loadHighResolutionBitmap(
                context = context,
                item = item,
                viewportWidth = viewportWidth,
                viewportHeight = viewportHeight
            )
        }
    }

    fun clampOffsets(source: Bitmap?, targetScale: Float = scale, targetRotation: Float = localRotation) {
        val image = source ?: return
        val bounds = viewerPanBounds(viewportWidth.toFloat(), viewportHeight.toFloat(), image.width.toFloat(), image.height.toFloat(), targetScale, targetRotation)
        offsetX = clampViewerOffset(offsetX, bounds.maxX)
        offsetY = clampViewerOffset(offsetY, bounds.maxY)
    }

    fun resetZoomOnly() {
        scale = 1f
        offsetX = 0f
        offsetY = 0f
        onScaleChanged(1f)
    }

    Box(
        Modifier.fillMaxSize().onSizeChanged { viewportWidth = it.width; viewportHeight = it.height; clampOffsets(bitmap) },
        contentAlignment = Alignment.Center
    ) {
        val source = bitmap
        if (source == null) CircularProgressIndicator()
        else {
            val fitted = viewerImageRenderSize(
                viewportWidth.toFloat(),
                viewportHeight.toFloat(),
                source.width.toFloat(),
                source.height.toFloat()
            )
            val renderWidth = with(density) { fitted.width.toDp() }
            val renderHeight = with(density) { fitted.height.toDp() }
            Image(
                bitmap = source.asImageBitmap(),
                contentDescription = item.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .requiredWidth(renderWidth)
                    .requiredHeight(renderHeight)
                    .pointerInput(item.id) {
                        awaitEachGesture {
                            val firstDown = awaitFirstDown(requireUnconsumed = false)
                            val downX = firstDown.position.x
                            val downY = firstDown.position.y
                            var transformed = false
                            var moved = false
                            var lastEventTime = firstDown.uptimeMillis
                            do {
                                val event = awaitPointerEvent()
                                val pressed = event.changes.count { it.pressed }
                                val pan = event.calculatePan()
                                val zoomRaw = if (pressed >= 2) event.calculateZoom() else 1f
                                val rotationDelta = if (pressed >= 2) event.calculateRotation() else 0f
                                val owns = shouldPhotoConsumeGesture(pressed, scale, localRotation)
                                event.changes.firstOrNull()?.let { change ->
                                    lastEventTime = change.uptimeMillis
                                    if (hypot(change.position.x - downX, change.position.y - downY) > 12f) moved = true
                                }
                                val transformFrame = owns && (
                                    pressed >= 2 || abs(pan.x) > 0.01f || abs(pan.y) > 0.01f
                                )

                                if (transformFrame) {
                                    if (!transformed) { transformed = true; onGestureActive(true) }
                                    if (pressed >= 2) {
                                        val oldScale = scale
                                        val nextScale = clampViewerScale(oldScale * galleryZoomFactor(zoomRaw))
                                        if (abs(nextScale - oldScale) > 0.0005f) {
                                            val centroid = event.calculateCentroid(useCurrent = true)
                                            offsetX = zoomOffsetAroundFocus(offsetX, centroid.x - fitted.width / 2f, oldScale, nextScale)
                                            offsetY = zoomOffsetAroundFocus(offsetY, centroid.y - fitted.height / 2f, oldScale, nextScale)
                                            scale = nextScale
                                            moved = true
                                        }
                                        if (abs(rotationDelta) > 0.01f) {
                                            localRotation = applyViewerRotationDelta(localRotation, rotationDelta)
                                            moved = true
                                        }
                                    }
                                    if (scale > 1.001f || abs(localRotation) > 0.5f) {
                                        if (abs(pan.x) > 0.01f || abs(pan.y) > 0.01f) moved = true
                                        offsetX += pan.x
                                        offsetY += pan.y
                                    }
                                    clampOffsets(source)
                                    event.changes.forEach { if (it.pressed) it.consume() }
                                }
                            } while (event.changes.any { it.pressed })

                            if (transformed) {
                                localRotation = releasedViewerRotation(rotation)
                                resetZoomOnly()
                                onGestureActive(false)
                            } else if (!moved) {
                                val distanceFromLast = hypot(
                                    downX - lastTapPosition[0],
                                    downY - lastTapPosition[1]
                                )
                                val doubleTap = isViewerDoubleTap(
                                    previousUpMs = lastTapTime[0],
                                    currentUpMs = lastEventTime,
                                    distancePx = distanceFromLast
                                )
                                if (doubleTap) {
                                    lastTapTime[0] = 0L
                                    val oldScale = scale
                                    val nextScale = nextDoubleTapScale(oldScale)
                                    if (nextScale <= 1.01f) {
                                        resetZoomOnly()
                                    } else {
                                        offsetX = zoomOffsetAroundFocus(offsetX, downX - fitted.width / 2f, oldScale, nextScale)
                                        offsetY = zoomOffsetAroundFocus(offsetY, downY - fitted.height / 2f, oldScale, nextScale)
                                        scale = nextScale
                                        clampOffsets(source)
                                        onScaleChanged(scale)
                                    }
                                } else {
                                    lastTapTime[0] = lastEventTime
                                    lastTapPosition[0] = downX
                                    lastTapPosition[1] = downY
                                }
                            }
                        }
                    }
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offsetX,
                        translationY = offsetY,
                        rotationZ = localRotation,
                        clip = false
                    )
            )
        }
    }
}
