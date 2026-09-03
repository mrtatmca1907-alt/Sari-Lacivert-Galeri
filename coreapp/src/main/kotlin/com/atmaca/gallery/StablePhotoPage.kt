package com.atmaca.gallery

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
                    .pointerInput(item.id, rotation) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            var handled = false
                            do {
                                val event = awaitPointerEvent()
                                val pressed = event.changes.count { it.pressed }
                                val canHandle = pressed >= 2 || scale > 1.001f
                                if (canHandle) {
                                    if (!handled) {
                                        handled = true
                                        onGestureActive(true)
                                    }

                                    val factor = if (pressed >= 2) dampedZoomFactor(event.calculateZoom()) else 1f
                                    val rotationDelta = if (pressed >= 2) event.calculateRotation() else 0f
                                    val newScale = clampViewerScale(scale * factor)
                                    val newRotation = applyViewerRotationDelta(rotation, rotationDelta)

                                    if (rotationDelta != 0f) onRotationChanged(newRotation)

                                    if (newScale <= 1.001f) {
                                        scale = 1f
                                        offsetX = 0f
                                        offsetY = 0f
                                        onScaleChanged(1f)
                                    } else {
                                        val pan = event.calculatePan()
                                        scale = newScale
                                        offsetX += pan.x
                                        offsetY += pan.y
                                        clampOffsets(source, newScale, newRotation)
                                        onScaleChanged(newScale)
                                    }

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
                            onDoubleTap = {
                                val target = nextDoubleTapScale(scale)
                                scale = target
                                if (target <= 1.001f) {
                                    offsetX = 0f
                                    offsetY = 0f
                                }
                                clampOffsets(source, target)
                                onScaleChanged(target)
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
