package com.atmaca.gallery

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
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
import kotlin.math.abs

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
    var scale by remember(item.id) { mutableFloatStateOf(1f) }
    var offsetX by remember(item.id) { mutableFloatStateOf(0f) }
    var offsetY by remember(item.id) { mutableFloatStateOf(0f) }
    var viewportWidth by remember(item.id) { mutableIntStateOf(0) }
    var viewportHeight by remember(item.id) { mutableIntStateOf(0) }
    val bitmap by produceState<Bitmap?>(initialValue = null, item.uri) {
        value = loadHighResolutionBitmap(context, item)
    }

    fun resetTransform() {
        scale = 1f
        offsetX = 0f
        offsetY = 0f
        onScaleChanged(1f)
    }

    fun clampOffsets(source: Bitmap?, targetScale: Float = scale, targetRotation: Float = rotation) {
        val image = source ?: return
        val bounds = viewerPanBounds(
            viewportWidth.toFloat(),
            viewportHeight.toFloat(),
            image.width.toFloat(),
            image.height.toFloat(),
            targetScale,
            targetRotation
        )
        offsetX = clampViewerOffset(offsetX, bounds.maxX)
        offsetY = clampViewerOffset(offsetY, bounds.maxY)
    }

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged {
                viewportWidth = it.width
                viewportHeight = it.height
                clampOffsets(bitmap)
            },
        contentAlignment = Alignment.Center
    ) {
        val source = bitmap
        if (source == null) {
            CircularProgressIndicator()
        } else {
            Image(
                bitmap = source.asImageBitmap(),
                contentDescription = item.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(item.id, rotation, viewportWidth, viewportHeight) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            var handled = false
                            do {
                                val event = awaitPointerEvent()
                                val pressed = event.changes.count { it.pressed }
                                val zoomRaw = if (pressed >= 2) event.calculateZoom() else 1f
                                val rotationDelta = if (pressed >= 2) event.calculateRotation() else 0f
                                val pan = event.calculatePan()
                                val zooming = pressed >= 2 && abs(zoomRaw - 1f) > 0.0005f
                                val rotating = pressed >= 2 && abs(rotationDelta) > 0.02f
                                val panning = scale > 1.001f && (abs(pan.x) > 0.01f || abs(pan.y) > 0.01f)
                                val canHandle = pressed >= 2 || panning

                                if (canHandle) {
                                    if (!handled) {
                                        handled = true
                                        onGestureActive(true)
                                    }

                                    var nextRotation = rotation
                                    if (rotating) {
                                        nextRotation = applyViewerRotationDelta(rotation, rotationDelta)
                                        onRotationChanged(nextRotation)
                                    }

                                    if (zooming) {
                                        val oldScale = scale
                                        val nextScale = clampViewerScale(oldScale * galleryZoomFactor(zoomRaw))
                                        val centroid = event.calculateCentroid(useCurrent = true)
                                        val focusX = centroid.x - viewportWidth / 2f
                                        val focusY = centroid.y - viewportHeight / 2f

                                        offsetX = zoomOffsetAroundFocus(offsetX, focusX, oldScale, nextScale)
                                        offsetY = zoomOffsetAroundFocus(offsetY, focusY, oldScale, nextScale)
                                        scale = nextScale
                                        onScaleChanged(nextScale)

                                        if (nextScale <= 1.001f) {
                                            resetTransform()
                                        }
                                    }

                                    if (scale > 1.001f) {
                                        offsetX += pan.x
                                        offsetY += pan.y
                                    }

                                    clampOffsets(source, scale, nextRotation)
                                    event.changes.forEach { change ->
                                        if (change.pressed) change.consume()
                                    }
                                }
                            } while (event.changes.any { it.pressed })

                            if (handled) {
                                clampOffsets(source)
                                onGestureActive(false)
                            }
                        }
                    }
                    .pointerInput(item.id, scale) {
                        detectTapGestures(
                            onTap = {
                                if (scale > 1.001f) resetTransform() else onFitTap()
                            },
                            onDoubleTap = { tap ->
                                val oldScale = scale
                                val target = nextDoubleTapScale(oldScale)
                                if (target <= 1.001f) {
                                    resetTransform()
                                } else {
                                    val focusX = tap.x - viewportWidth / 2f
                                    val focusY = tap.y - viewportHeight / 2f
                                    offsetX = zoomOffsetAroundFocus(offsetX, focusX, oldScale, target)
                                    offsetY = zoomOffsetAroundFocus(offsetY, focusY, oldScale, target)
                                    scale = target
                                    clampOffsets(source, target)
                                    onScaleChanged(target)
                                }
                            }
                        )
                    }
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offsetX,
                        translationY = offsetY,
                        rotationZ = rotation,
                        clip = true
                    )
            )
        }
    }
}
