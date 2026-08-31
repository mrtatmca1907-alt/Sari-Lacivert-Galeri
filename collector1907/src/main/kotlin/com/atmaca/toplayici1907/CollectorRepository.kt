package com.atmaca.toplayici1907

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest

class CollectorRepository(context: Context) {
    private val resolver = context.applicationContext.contentResolver

    suspend fun loadPhotos(): List<PhotoRecord> = withContext(Dispatchers.IO) {
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = buildList {
            add(MediaStore.Images.Media._ID)
            add(MediaStore.Images.Media.DISPLAY_NAME)
            add(MediaStore.Images.Media.SIZE)
            add(MediaStore.Images.Media.DATE_ADDED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.Images.Media.RELATIVE_PATH)
            }
        }.toTypedArray()

        val result = ArrayList<PhotoRecord>()
        resolver.query(
            collection,
            projection,
            null,
            null,
            "${MediaStore.Images.Media.DATE_ADDED} ASC, ${MediaStore.Images.Media._ID} ASC"
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val dateIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val pathIndex = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                cursor.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH)
            } else {
                -1
            }

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idIndex)
                val uri = ContentUris.withAppendedId(collection, id)
                result += PhotoRecord(
                    id = id,
                    uri = uri.toString(),
                    name = cursor.getString(nameIndex) ?: "foto_$id.jpg",
                    size = cursor.getLong(sizeIndex),
                    relativePath = if (pathIndex >= 0) cursor.getString(pathIndex).orEmpty() else "",
                    dateAdded = cursor.getLong(dateIndex)
                )
            }
        }
        result
    }

    fun sha256(photo: PhotoRecord): String? = runCatching {
        val digest = MessageDigest.getInstance("SHA-256")
        resolver.openInputStream(Uri.parse(photo.uri))?.use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count > 0) digest.update(buffer, 0, count)
            }
        } ?: return null
        digest.digest().joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }.getOrNull()

    fun exists(photo: PhotoRecord): Boolean = runCatching {
        resolver.openAssetFileDescriptor(Uri.parse(photo.uri), "r")?.use { true } ?: false
    }.getOrDefault(false)

    fun moveTo1907(photo: PhotoRecord, targetName: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.RELATIVE_PATH, CollectorPolicy.TARGET)
            if (targetName != photo.name) {
                put(MediaStore.MediaColumns.DISPLAY_NAME, targetName)
            }
        }
        return resolver.update(Uri.parse(photo.uri), values, null, null) == 1
    }
}
