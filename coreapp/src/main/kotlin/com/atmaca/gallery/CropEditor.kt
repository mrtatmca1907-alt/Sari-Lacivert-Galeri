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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

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
                Modifier.fillMaxSize().pointerInput(item.id, ratio) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        val dx = drag.x / size.width.toFloat()
                        val dy = drag.y / size.height.toFloat()
                        val p = Offset(change.position.x / size.width, change.position.y / size.height)
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
                drawRect(Color.White, topLeft = Offset(left * size.width, top * size.height), size = androidx.compose.ui.geometry.Size((right-left)*size.width, (bottom-top)*size.height), style = Stroke(width = 4f))
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
                    val crop = normalizedCropRect(left, top, right, bottom)
                    val x = (crop.left * source.width).roundToInt().coerceIn(0, source.width - 1)
                    val y = (crop.top * source.height).roundToInt().coerceIn(0, source.height - 1)
                    val w = (crop.width * source.width).roundToInt().coerceIn(1, source.width - x)
                    val h = (crop.height * source.height).roundToInt().coerceIn(1, source.height - y)
                    scope.launch {
                        val cropped = runCatching { Bitmap.createBitmap(source, x, y, w, h) }.getOrNull()
                        val saved = cropped?.let { actions.saveCroppedCopy(item, it) }
                        if (cropped !== source) cropped?.recycle()
                        if (saved != null) { onMessage("Kırpılmış kopya kaydedildi"); onSaved() }
                        else onMessage("Kırpılmış fotoğraf kaydedilemedi")
                    }
                }) { Text("Kaydet") }
            }
        }
    }
}
