package com.atmaca.gallery

import android.content.ContentResolver
import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import java.util.LinkedHashMap

data class AlbumInfo(val path: String, val name: String, val count: Int, val coverUri: Uri?)
data class MediaItem(val uri: Uri, val name: String, val mime: String, val path: String, val dateModified: Long)

class MediaRepository(private val resolver: ContentResolver) {
    private val filesUri = MediaStore.Files.getContentUri("external")

    fun albums(): List<AlbumInfo> {
        data class Acc(var count: Int = 0, var cover: Uri? = null)
        val data = LinkedHashMap<String, Acc>()
        val projection = arrayOf(MediaStore.Files.FileColumns._ID, MediaStore.MediaColumns.RELATIVE_PATH)
        val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE}=? OR ${MediaStore.Files.FileColumns.MEDIA_TYPE}=?"
        val args = arrayOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString()
        )
        resolver.query(filesUri, projection, selection, args, "${MediaStore.MediaColumns.DATE_MODIFIED} DESC")?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val pathCol = c.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
            while (c.moveToNext()) {
                val raw = if (pathCol >= 0) c.getString(pathCol) else null
                val path = raw?.takeIf { it.isNotBlank() } ?: ROOT
                val acc = data.getOrPut(path) { Acc() }
                acc.count++
                if (acc.cover == null) acc.cover = ContentUris.withAppendedId(filesUri, c.getLong(idCol))
            }
        }
        return data.map { (path, acc) ->
            val name = if (path == ROOT) ROOT else path.trimEnd('/').substringAfterLast('/').ifBlank { path }
            AlbumInfo(path, name, acc.count, acc.cover)
        }.sortedWith(compareByDescending<AlbumInfo> { it.count }.thenBy { it.name.lowercase() })
    }

    fun items(path: String): List<MediaItem> {
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.RELATIVE_PATH,
            MediaStore.MediaColumns.DATE_MODIFIED
        )
        val type = "(${MediaStore.Files.FileColumns.MEDIA_TYPE}=? OR ${MediaStore.Files.FileColumns.MEDIA_TYPE}=?)"
        val (selection, args) = if (path == ROOT) {
            "$type AND (${MediaStore.MediaColumns.RELATIVE_PATH} IS NULL OR ${MediaStore.MediaColumns.RELATIVE_PATH}='')" to arrayOf(
                MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
                MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString()
            )
        } else {
            "$type AND ${MediaStore.MediaColumns.RELATIVE_PATH}=?" to arrayOf(
                MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
                MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
                path
            )
        }
        val out = ArrayList<MediaItem>()
        resolver.query(filesUri, projection, selection, args, "${MediaStore.MediaColumns.DATE_MODIFIED} DESC")?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val mimeCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
            val pathCol = c.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
            val dateCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                out += MediaItem(
                    ContentUris.withAppendedId(filesUri, id),
                    c.getString(nameCol).orEmpty(),
                    c.getString(mimeCol).orEmpty(),
                    if (pathCol >= 0) c.getString(pathCol).orEmpty() else "",
                    c.getLong(dateCol)
                )
            }
        }
        return out
    }

    companion object { const val ROOT = "Ana Depolama" }
}
