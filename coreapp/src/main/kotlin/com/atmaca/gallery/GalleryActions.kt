package com.atmaca.gallery

import android.app.PendingIntent
import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.result.IntentSenderRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GalleryActions(context: Context) {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver

    fun shareIntent(items: List<GalleryMedia>): Intent? {
        if (items.isEmpty()) return null
        val uris = ArrayList(items.map { it.uri })
        val mime = if (items.all { it.isVideo }) "video/*" else if (items.all { !it.isVideo }) "image/*" else "*/*"
        return if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uris.first())
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = mime
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
    }

    fun writeRequest(items: List<GalleryMedia>): IntentSenderRequest? {
        if (Build.VERSION.SDK_INT < 30 || items.isEmpty()) return null
        return MediaStore.createWriteRequest(resolver, items.map { it.uri }).toRequest()
    }

    fun trashRequest(items: List<GalleryMedia>, trashed: Boolean): IntentSenderRequest? {
        if (Build.VERSION.SDK_INT < 30 || items.isEmpty()) return null
        return MediaStore.createTrashRequest(resolver, items.map { it.uri }, trashed).toRequest()
    }

    fun deleteRequest(items: List<GalleryMedia>): IntentSenderRequest? {
        if (Build.VERSION.SDK_INT < 30 || items.isEmpty()) return null
        return MediaStore.createDeleteRequest(resolver, items.map { it.uri }).toRequest()
    }

    suspend fun rename(item: GalleryMedia, requestedName: String): Boolean = withContext(Dispatchers.IO) {
        val name = finalDisplayName(item.name, requestedName)
        if (name.isBlank()) return@withContext false
        runCatching { resolver.update(item.uri, ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, name) }, null, null) > 0 }.getOrDefault(false)
    }

    suspend fun move(items: List<GalleryMedia>, targetRelativePath: String): Int = withContext(Dispatchers.IO) {
        val target = normalizeRelativePath(targetRelativePath)
        items.count { item ->
            runCatching { resolver.update(item.uri, ContentValues().apply { put(MediaStore.MediaColumns.RELATIVE_PATH, target) }, null, null) > 0 }.getOrDefault(false)
        }
    }

    suspend fun copy(items: List<GalleryMedia>, targetRelativePath: String): Int = withContext(Dispatchers.IO) {
        val target = normalizeRelativePath(targetRelativePath)
        var copied = 0
        for (item in items) {
            val collection = if (item.isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, item.name)
                put(MediaStore.MediaColumns.MIME_TYPE, item.mimeType)
                if (Build.VERSION.SDK_INT >= 29) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, target)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }
            val destination = runCatching { resolver.insert(collection, values) }.getOrNull() ?: continue
            val success = runCatching {
                resolver.openInputStream(item.uri)?.use { input ->
                    resolver.openOutputStream(destination, "w")?.use { output -> input.copyTo(output, DEFAULT_BUFFER_SIZE * 8) }
                        ?: error("Hedef açılamadı")
                } ?: error("Kaynak açılamadı")
                if (Build.VERSION.SDK_INT >= 29) resolver.update(destination, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
                true
            }.getOrElse {
                runCatching { resolver.delete(destination, null, null) }
                false
            }
            if (success) copied++
        }
        copied
    }

    suspend fun overwriteCropped(source: GalleryMedia, bitmap: Bitmap): Boolean = withContext(Dispatchers.IO) {
        if (source.isVideo) return@withContext false

        val isPng = source.mimeType.equals("image/png", ignoreCase = true) || source.name.endsWith(".png", ignoreCase = true)
        val format = if (isPng) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
        val quality = if (isPng) 100 else 96
        val temp = runCatching { File.createTempFile("atmaca_crop_", if (isPng) ".png" else ".jpg", appContext.cacheDir) }.getOrNull()
            ?: return@withContext false

        try {
            val encoded = runCatching {
                temp.outputStream().buffered().use { output ->
                    check(bitmap.compress(format, quality, output))
                }
                true
            }.getOrDefault(false)
            if (!encoded) return@withContext false

            val written = runCatching {
                temp.inputStream().buffered().use { input ->
                    resolver.openOutputStream(source.uri, "w")?.buffered()?.use { output ->
                        input.copyTo(output, DEFAULT_BUFFER_SIZE * 8)
                    } ?: error("Kaynak fotoğraf yazma için açılamadı")
                }
                true
            }.getOrDefault(false)
            if (!written) return@withContext false

            if (!isPng && !source.name.endsWith(".jpg", true) && !source.name.endsWith(".jpeg", true)) {
                val base = source.name.substringBeforeLast('.', source.name)
                runCatching {
                    resolver.update(source.uri, ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, "$base.jpg")
                        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    }, null, null)
                }
            }
            true
        } finally {
            runCatching { temp.delete() }
        }
    }

    suspend fun saveScreenshot(bitmap: Bitmap): Uri? = withContext(Dispatchers.IO) {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "ATMACA_SCREEN_$stamp.jpg")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/ATMACA Screenshots/")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return@withContext null
        val ok = runCatching {
            resolver.openOutputStream(uri, "w")?.use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, 96, output))
            } ?: error("Screenshot dosyası açılamadı")
            if (Build.VERSION.SDK_INT >= 29) {
                resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            }
            true
        }.getOrDefault(false)
        if (!ok) {
            runCatching { resolver.delete(uri, null, null) }
            null
        } else uri
    }

    suspend fun deleteLegacy(items: List<GalleryMedia>): Int = withContext(Dispatchers.IO) {
        items.count { runCatching { resolver.delete(it.uri, null, null) > 0 }.getOrDefault(false) }
    }

    fun prepareCameraImage(): Uri? {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "ATMACA_$stamp.jpg")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/Camera/")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }
        return resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
    }

    fun cameraIntent(uri: Uri): Intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
        putExtra(MediaStore.EXTRA_OUTPUT, uri)
        clipData = ClipData.newRawUri("ATMACA camera output", uri)
        addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    fun finishCameraImage(uri: Uri, success: Boolean) {
        if (!success) {
            runCatching { resolver.delete(uri, null, null) }
            return
        }
        if (Build.VERSION.SDK_INT >= 29) runCatching {
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        }
    }

    private fun PendingIntent.toRequest(): IntentSenderRequest = IntentSenderRequest.Builder(intentSender).build()

    private fun finalDisplayName(current: String, requested: String): String {
        val clean = requested.trim().replace('/', '_').replace('\\', '_')
        if (clean.isBlank()) return ""
        if ('.' in clean) return clean
        val extension = current.substringAfterLast('.', "")
        return if (extension.isBlank()) clean else "$clean.$extension"
    }
}
