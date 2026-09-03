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
    var localRotation by remember(item.id) { mutableFloatStateOf(rotation) }
    var offsetX by remember(item.id) { mutableFloatStateOf(0f) }
    var offsetY by remember(item.id) { mutableFloatStateOf(0f) }
    var viewportWidth by remember(item.id) { mutableIntStateOf(0) }
    var viewportHeight by remember(item.id) { mutableIntStateOf(0) }

    LaunchedEffect(rotation, item.id) {
        if (abs(normalizeViewerRotation(rotation) - normalizeViewerRotation(localRotation)) > 0.5f) localRotation = rotation
    }

    val bitmap by produceState<Bitmap?>(initialValue = null, item.uri) {
        value = loadHighResolutionBitmap(context, item)
    }

    fun clampOffsets(source: Bitmap?, targetScale: Float = scale, targetRotation: Float = localRotation) {
        val image = source ?: return
        val bounds = viewerPanBounds(viewportWidth.toFloat(), viewportHeight.toFloat(), image.width.toFloat(), image.height.toFloat(), targetScale, targetRotation)
        offsetX = clampViewerOffset(offsetX, bounds.maxX)
        offsetY = clampViewerOffset(offsetY, bounds.maxY)
    }

    fun resetTransform() {
        scale = 1f; localRotation = 0f; offsetX = 0f; offsetY = 0f
        onScaleChanged(1f); onRotationChanged(0f)
    }

    Box(
        Modifier.fillMaxSize().onSizeChanged { viewportWidth = it.width; viewportHeight = it.height; clampOffsets(bitmap) },
        contentAlignment = Alignment.Center
    ) {
        val source = bitmap
        if (source == null) CircularProgressIndicator()
        else Image(
            bitmap = source.asImageBitmap(),
            contentDescription = item.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
                // One stable pointer coroutine: no scale/rotation keys, no competing tap recognizer.
                .pointerInput(item.id) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        var transformed = false
                        var moved = false
                        do {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.count { it.pressed }
                            val pan = event.calculatePan()
                            val zoomRaw = if (pressed >= 2) event.calculateZoom() else 1f
                            val rotationDelta = if (pressed >= 2) event.calculateRotation() else 0f
                            val owns = shouldPhotoConsumeGesture(pressed, scale, localRotation)

                            if (owns) {
                                if (!transformed) { transformed = true; onGestureActive(true) }
                                if (pressed >= 2) {
                                    val oldScale = scale
                                    val nextScale = clampViewerScale(oldScale * galleryZoomFactor(zoomRaw))
                                    if (abs(nextScale - oldScale) > 0.0005f) {
                                        val centroid = event.calculateCentroid(useCurrent = true)
                                        offsetX = zoomOffsetAroundFocus(offsetX, centroid.x - viewportWidth / 2f, oldScale, nextScale)
                                        offsetY = zoomOffsetAroundFocus(offsetY, centroid.y - viewportHeight / 2f, oldScale, nextScale)
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
                                    offsetX += pan.x; offsetY += pan.y
                                }
                                clampOffsets(source)
                                event.changes.forEach { if (it.pressed) it.consume() }
                            }
                        } while (event.changes.any { it.pressed })

                        if (transformed) {
                            clampOffsets(source)
                            // Publish expensive parent state once, after fingers leave the screen.
                            if (shouldCommitViewerTransform(true)) {
                                onScaleChanged(scale)
                                onRotationChanged(localRotation)
                            }
                            onGestureActive(false)
                        } else if (!moved) {
                            if (scale > 1.001f || abs(localRotation) > 0.5f) resetTransform() else onFitTap()
                        }
                    }
                }
                .graphicsLayer(scaleX=scale, scaleY=scale, translationX=offsetX, translationY=offsetY, rotationZ=localRotation, clip=true)
        )
    }
}
