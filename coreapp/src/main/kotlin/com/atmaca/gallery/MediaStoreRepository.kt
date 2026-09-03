package com.atmaca.gallery

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

data class GalleryMedia(
    val id: Long,
    val uri: Uri,
    val name: String,
    val mimeType: String?,
    val isVideo: Boolean,
    val dateAdded: Long,
    val width: Int,
    val height: Int,
    val bucketId: Long,
    val bucketName: String?,
    val relativePath: String,
    val size: Long,
    val durationMs: Long,
    val isTrashed: Boolean
)

data class GalleryAlbum(
    val relativePath: String,
    val name: String,
    val count: Int,
    val cover: GalleryMedia?
)

class MediaStoreRepository(context: Context) {
    private val resolver = context.applicationContext.contentResolver
    private val filesCollection = MediaStore.Files.getContentUri("external")

    suspend fun loadPage(
        tab: GalleryTab,
        offset: Int,
        limit: Int = PAGE_SIZE,
        albumPath: String? = null,
        trashedOnly: Boolean = false
    ): List<GalleryMedia> = withContext(Dispatchers.IO) {
        if (trashedOnly && Build.VERSION.SDK_INT < 30) return@withContext emptyList()

        val wantedType = when (tab) {
            GalleryTab.PHOTOS -> MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE
            GalleryTab.VIDEOS -> MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
        }
        val selectionParts = mutableListOf("${MediaStore.Files.FileColumns.MEDIA_TYPE}=?")
        val selectionArgs = mutableListOf(wantedType.toString())
        albumPath?.let {
            selectionParts += "${MediaStore.MediaColumns.RELATIVE_PATH}=?"
            selectionArgs += normalizeRelativePath(it)
        }

        val args = Bundle().apply {
            putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selectionParts.joinToString(" AND "))
            putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selectionArgs.toTypedArray())
            putStringArray(
                ContentResolver.QUERY_ARG_SORT_COLUMNS,
                arrayOf(MediaStore.MediaColumns.DATE_ADDED, MediaStore.Files.FileColumns._ID)
            )
            putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION, ContentResolver.QUERY_SORT_DIRECTION_DESCENDING)
            putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
            putInt(ContentResolver.QUERY_ARG_OFFSET, offset)
            if (Build.VERSION.SDK_INT >= 30 && trashedOnly) {
                putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_ONLY)
            }
        }

        queryMedia(args, limit)
    }

    suspend fun loadAlbums(): List<GalleryAlbum> = withContext(Dispatchers.IO) {
        val args = Bundle().apply {
            putString(
                ContentResolver.QUERY_ARG_SQL_SELECTION,
                "${MediaStore.Files.FileColumns.MEDIA_TYPE}=? OR ${MediaStore.Files.FileColumns.MEDIA_TYPE}=?"
            )
            putStringArray(
                ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,
                arrayOf(
                    MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
                    MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString()
                )
            )
            putStringArray(
                ContentResolver.QUERY_ARG_SORT_COLUMNS,
                arrayOf(MediaStore.MediaColumns.DATE_ADDED, MediaStore.Files.FileColumns._ID)
            )
            putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION, ContentResolver.QUERY_SORT_DIRECTION_DESCENDING)
        }
        val counts = linkedMapOf<String, Int>()
        val covers = linkedMapOf<String, GalleryMedia>()
        resolver.query(filesCollection, PROJECTION, args, null)?.use { cursor ->
            val cols = Columns(cursor)
            while (cursor.moveToNext()) {
                coroutineContext.ensureActive()
                val item = cols.read(cursor)
                val path = normalizeRelativePath(item.relativePath)
                counts[path] = (counts[path] ?: 0) + 1
                if (path !in covers) covers[path] = item
            }
        }
        counts.map { (path, count) ->
            GalleryAlbum(path, albumDisplayName(path), count, covers[path])
        }.sortedBy { it.name.lowercase() }
    }

    suspend fun findExactDuplicates(onProgress: (Int) -> Unit = {}): List<List<GalleryMedia>> =
        withContext(Dispatchers.IO) {
            val selection = "(${MediaStore.Files.FileColumns.MEDIA_TYPE}=? OR ${MediaStore.Files.FileColumns.MEDIA_TYPE}=?) AND ${MediaStore.MediaColumns.SIZE}>0"
            val args = Bundle().apply {
                putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                putStringArray(
                    ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,
                    arrayOf(
                        MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
                        MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString()
                    )
                )
                putStringArray(
                    ContentResolver.QUERY_ARG_SORT_COLUMNS,
                    arrayOf(MediaStore.MediaColumns.SIZE, MediaStore.Files.FileColumns._ID)
                )
                putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION, ContentResolver.QUERY_SORT_DIRECTION_ASCENDING)
            }

            val duplicates = mutableListOf<List<GalleryMedia>>()
            resolver.query(filesCollection, PROJECTION, args, null)?.use { cursor ->
                val cols = Columns(cursor)
                var currentSize = -1L
                val sameSize = mutableListOf<GalleryMedia>()
                var scanned = 0

                fun flushSizeGroup() {
                    if (sameSize.size > 1) {
                        val byHash = linkedMapOf<String, MutableList<GalleryMedia>>()
                        for (item in sameSize) {
                            coroutineContext.ensureActive()
                            val hash = sha256(item.uri) ?: continue
                            byHash.getOrPut(hash) { mutableListOf() } += item
                        }
                        duplicates += byHash.values.filter { it.size > 1 }.map { it.toList() }
                    }
                    sameSize.clear()
                }

                while (cursor.moveToNext()) {
                    coroutineContext.ensureActive()
                    val item = cols.read(cursor)
                    if (currentSize != -1L && item.size != currentSize) flushSizeGroup()
                    currentSize = item.size
                    sameSize += item
                    scanned++
                    if (scanned % 250 == 0) onProgress(scanned)
                }
                flushSizeGroup()
                onProgress(scanned)
            }
            duplicates.sortedByDescending { group -> group.sumOf { it.size } }
        }

    private fun sha256(uri: Uri): String? = runCatching {
        val digest = MessageDigest.getInstance("SHA-256")
        resolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 4)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        } ?: return@runCatching null
        digest.digest().joinToString("") { "%02x".format(it) }
    }.getOrNull()

    private fun queryMedia(args: Bundle, capacity: Int): List<GalleryMedia> {
        val result = ArrayList<GalleryMedia>(capacity)
        resolver.query(filesCollection, PROJECTION, args, null)?.use { cursor ->
            val cols = Columns(cursor)
            while (cursor.moveToNext()) result += cols.read(cursor)
        }
        return result
    }

    private class Columns(cursor: android.database.Cursor) {
        private val id = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
        private val type = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
        private val name = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
        private val mime = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
        private val date = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
        private val width = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH)
        private val height = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT)
        private val bucketId = cursor.getColumnIndex(MediaStore.Images.ImageColumns.BUCKET_ID)
        private val bucketName = cursor.getColumnIndex(MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME)
        private val relativePath = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
        private val size = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
        private val duration = cursor.getColumnIndex(MediaStore.Video.VideoColumns.DURATION)
        private val trashed = if (Build.VERSION.SDK_INT >= 30) cursor.getColumnIndex(MediaStore.MediaColumns.IS_TRASHED) else -1

        fun read(cursor: android.database.Cursor): GalleryMedia {
            val itemId = cursor.getLong(id)
            val mediaType = cursor.getInt(type)
            val isVideo = mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
            val baseUri = if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            return GalleryMedia(
                id = itemId,
                uri = ContentUris.withAppendedId(baseUri, itemId),
                name = cursor.getString(name) ?: "",
                mimeType = cursor.getString(mime),
                isVideo = isVideo,
                dateAdded = cursor.getLong(date),
                width = cursor.getInt(width),
                height = cursor.getInt(height),
                bucketId = if (bucketId >= 0) cursor.getLong(bucketId) else 0L,
                bucketName = if (bucketName >= 0) cursor.getString(bucketName) else null,
                relativePath = if (relativePath >= 0) cursor.getString(relativePath) ?: "" else "",
                size = if (size >= 0) cursor.getLong(size) else 0L,
                durationMs = if (duration >= 0) cursor.getLong(duration) else 0L,
                isTrashed = trashed >= 0 && cursor.getInt(trashed) != 0
            )
        }
    }

    companion object {
        const val PAGE_SIZE = 120

        private val PROJECTION = buildList {
            add(MediaStore.Files.FileColumns._ID)
            add(MediaStore.Files.FileColumns.MEDIA_TYPE)
            add(MediaStore.MediaColumns.DISPLAY_NAME)
            add(MediaStore.MediaColumns.MIME_TYPE)
            add(MediaStore.MediaColumns.DATE_ADDED)
            add(MediaStore.MediaColumns.WIDTH)
            add(MediaStore.MediaColumns.HEIGHT)
            add(MediaStore.Images.ImageColumns.BUCKET_ID)
            add(MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME)
            add(MediaStore.MediaColumns.RELATIVE_PATH)
            add(MediaStore.MediaColumns.SIZE)
            add(MediaStore.Video.VideoColumns.DURATION)
            if (Build.VERSION.SDK_INT >= 30) add(MediaStore.MediaColumns.IS_TRASHED)
        }.toTypedArray()
    }
}
