package com.atmaca.gallery

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

data class BitmapCrop(val x: Int, val y: Int, val width: Int, val height: Int)

fun fitImageBounds(viewWidth: Float, viewHeight: Float, imageWidth: Int, imageHeight: Int): Rect {
    if (viewWidth <= 0f || viewHeight <= 0f || imageWidth <= 0 || imageHeight <= 0) return Rect.Zero
    val scale = min(viewWidth / imageWidth.toFloat(), viewHeight / imageHeight.toFloat())
    val width = imageWidth * scale
    val height = imageHeight * scale
    val left = (viewWidth - width) / 2f
    val top = (viewHeight - height) / 2f
    return Rect(left, top, left + width, top + height)
}

fun mapCropToBitmap(left: Float, top: Float, right: Float, bottom: Float, imageWidth: Int, imageHeight: Int): BitmapCrop {
    val crop = normalizedCropRect(left, top, right, bottom)
    val x = (crop.left * imageWidth).roundToInt().coerceIn(0, imageWidth - 1)
    val y = (crop.top * imageHeight).roundToInt().coerceIn(0, imageHeight - 1)
    val w = (crop.width * imageWidth).roundToInt().coerceIn(1, imageWidth - x)
    val h = (crop.height * imageHeight).roundToInt().coerceIn(1, imageHeight - y)
    return BitmapCrop(x, y, w, h)
}

@Composable
fun CropEditor(
    item: GalleryMedia,
    actions: GalleryActions,
    onCancel: () -> Unit,
    onSaved: () -> Unit,
    onMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val bitmap by produceState<Bitmap?>(null, item.uri) { value = loadHighResolutionBitmap(context, item) }
    var left by remember(item.id) { mutableFloatStateOf(0.08f) }
    var top by remember(item.id) { mutableFloatStateOf(0.08f) }
    var right by remember(item.id) { mutableFloatStateOf(0.92f) }
    var bottom by remember(item.id) { mutableFloatStateOf(0.92f) }
    var ratio by remember(item.id) { mutableStateOf(CropRatio.FREE) }

    fun resetForRatio(newRatio: CropRatio) {
        ratio = newRatio
        if (newRatio == CropRatio.FREE) {
            left = .08f; top = .08f; right = .92f; bottom = .92f
        } else {
            val r = newRatio.ratio
            val w = .84f
            val h = (w / r).coerceAtMost(.84f)
            val finalW = (h * r).coerceAtMost(.84f)
            left = (1f - finalW) / 2f; right = 1f - left
            top = (1f - h) / 2f; bottom = 1f - top
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        val source = bitmap
        if (source == null) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        } else {
            Image(source.asImageBitmap(), item.name, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            Canvas(
                Modifier.fillMaxSize().pointerInput(item.id, ratio, source.width, source.height) {
                    detectDragGestures { change, drag ->
                        val imageBounds = fitImageBounds(size.width.toFloat(), size.height.toFloat(), source.width, source.height)
                        if (imageBounds.width <= 0f || imageBounds.height <= 0f) return@detectDragGestures
                        change.consume()
                        val dx = drag.x / imageBounds.width
                        val dy = drag.y / imageBounds.height
                        val p = Offset(
                            ((change.position.x - imageBounds.left) / imageBounds.width).coerceIn(0f, 1f),
                            ((change.position.y - imageBounds.top) / imageBounds.height).coerceIn(0f, 1f)
                        )
                        val distances = listOf(abs(p.x-left), abs(p.x-right), abs(p.y-top), abs(p.y-bottom))
                        when (distances.indexOf(distances.minOrNull())) {
                            0 -> left = (left + dx).coerceIn(0f, right - .05f)
                            1 -> right = (right + dx).coerceIn(left + .05f, 1f)
                            2 -> top = (top + dy).coerceIn(0f, bottom - .05f)
                            else -> bottom = (bottom + dy).coerceIn(top + .05f, 1f)
                        }
                    }
                }
            ) {
                val imageBounds = fitImageBounds(size.width, size.height, source.width, source.height)
                drawRect(
                    Color.White,
                    topLeft = Offset(imageBounds.left + left * imageBounds.width, imageBounds.top + top * imageBounds.height),
                    size = Size((right-left) * imageBounds.width, (bottom-top) * imageBounds.height),
                    style = Stroke(width = 4f)
                )
            }
        }

        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(alpha=.75f)).padding(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                TextButton({ resetForRatio(CropRatio.FREE) }) { Text("Serbest") }
                TextButton({ resetForRatio(CropRatio.SQUARE) }) { Text("1:1") }
                TextButton({ resetForRatio(CropRatio.FOUR_THREE) }) { Text("4:3") }
                TextButton({ resetForRatio(CropRatio.SIXTEEN_NINE) }) { Text("16:9") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onCancel) { Text("İptal") }
                TextButton(onClick = { resetForRatio(ratio) }) { Text("Sıfırla") }
                Button(onClick = {
                    val source = bitmap ?: return@Button
                    val crop = mapCropToBitmap(left, top, right, bottom, source.width, source.height)
                    scope.launch {
                        val cropped = runCatching { Bitmap.createBitmap(source, crop.x, crop.y, crop.width, crop.height) }.getOrNull()
                        val saved = cropped?.let { actions.overwriteCropped(item, it) } == true
                        if (cropped !== source) cropped?.recycle()
                        if (saved) {
                            onMessage("Fotoğraf kırpılarak güncellendi")
                            onSaved()
                        } else {
                            onMessage("Fotoğraf güncellenemedi")
                        }
                    }
                }) { Text("Kaydet") }
            }
        }
    }
}
