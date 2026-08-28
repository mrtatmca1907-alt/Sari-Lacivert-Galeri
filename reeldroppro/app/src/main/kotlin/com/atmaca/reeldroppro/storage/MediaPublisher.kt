package com.atmaca.reeldroppro.storage

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import com.atmaca.reeldroppro.engine.MediaKind
import com.atmaca.reeldroppro.engine.MediaTypePolicy
import java.io.File

class MediaPublisher(private val context: Context) {
    data class Published(val photos: Int, val videos: Int, val files: Int, val bytes: Long)

    fun publishTree(platform: String, source: String, root: File): Published {
        var photos = 0
        var videos = 0
        var files = 0
        var bytes = 0L
        root.walkTopDown().filter { it.isFile && !it.name.endsWith(".part") }.forEach { file ->
            val ext = file.extension.lowercase()
            val kind = MediaTypePolicy.fromExtension(ext)
            if (kind == MediaKind.OTHER) return@forEach
            val bucket = if (kind == MediaKind.PHOTO) MediaBucket.PHOTO else MediaBucket.VIDEO
            publishOne(file, OutputPathPolicy.relativePath(platform, source, bucket), kind)
            files++
            bytes += file.length()
            if (kind == MediaKind.PHOTO) photos++ else videos++
        }
        return Published(photos, videos, files, bytes)
    }

    private fun publishOne(file: File, relativePath: String, kind: MediaKind) {
        val resolver = context.contentResolver
        val collection = if (kind == MediaKind.PHOTO) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val mime = if (kind == MediaKind.PHOTO) "image/${file.extension.lowercase().let { if (it == "jpg") "jpeg" else it }}" else "video/${file.extension.lowercase()}"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            if (Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }
        val uri = requireNotNull(resolver.insert(collection, values)) { "MediaStore kaydı oluşturulamadı" }
        try {
            resolver.openOutputStream(uri, "w")!!.use { out -> file.inputStream().use { it.copyTo(out, 1024 * 1024) } }
            if (Build.VERSION.SDK_INT >= 29) {
                resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            }
        } catch (t: Throwable) {
            resolver.delete(uri, null, null)
            throw t
        }
    }
}
