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
    val dateModified: Long,
    val dateTaken: Long,
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
    val cover: GalleryMedia?,
    val bucketId: Long = 0L,
    val bucketName: String? = null
)

class MediaStoreRepository(context: Context) {
    private val resolver = context.applicationContext.contentResolver
    private val filesCollection = MediaStore.Files.getContentUri("external")

    suspend fun loadPage(
        tab: GalleryTab,
        offset: Int,
        limit: Int = PAGE_SIZE,
        albumPath: String? = null,
        albumBucketId: Long = 0L,
        albumBucketName: String? = null,
        trashedOnly: Boolean = false
    ): List<GalleryMedia> = withContext(Dispatchers.IO) {
        if (trashedOnly && Build.VERSION.SDK_INT < 30) return@withContext emptyList()
        val wantedType = when (tab) {
            GalleryTab.PHOTOS -> MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE
            GalleryTab.VIDEOS -> MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
        }
        val selectionParts = mutableListOf("${MediaStore.Files.FileColumns.MEDIA_TYPE}=?")
        val selectionArgs = mutableListOf(wantedType.toString())
        addAlbumSelector(selectionParts, selectionArgs, albumPath, albumBucketId, albumBucketName)
        queryPage(selectionParts, selectionArgs, offset, limit, trashedOnly)
    }

    suspend fun loadMixedPage(
        offset: Int,
        limit: Int = PAGE_SIZE,
        albumPath: String? = null,
        albumBucketId: Long = 0L,
        albumBucketName: String? = null,
        trashedOnly: Boolean = false
    ): List<GalleryMedia> = withContext(Dispatchers.IO) {
        if (trashedOnly && Build.VERSION.SDK_INT < 30) return@withContext emptyList()
        val selectionParts = mutableListOf("(${MediaStore.Files.FileColumns.MEDIA_TYPE}=? OR ${MediaStore.Files.FileColumns.MEDIA_TYPE}=?)")
        val selectionArgs = mutableListOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString()
        )
        addAlbumSelector(selectionParts, selectionArgs, albumPath, albumBucketId, albumBucketName)
        queryPage(selectionParts, selectionArgs, offset, limit, trashedOnly)
    }

    suspend fun loadMixedPageAfter(
        afterDateAdded: Long?,
        afterId: Long?,
        limit: Int = PAGE_SIZE,
        albumPath: String? = null,
        albumBucketId: Long = 0L,
        albumBucketName: String? = null,
        trashedOnly: Boolean = false
    ): List<GalleryMedia> = withContext(Dispatchers.IO) {
        if (trashedOnly && Build.VERSION.SDK_INT < 30) return@withContext emptyList()
        val selectionParts = mutableListOf("(${MediaStore.Files.FileColumns.MEDIA_TYPE}=? OR ${MediaStore.Files.FileColumns.MEDIA_TYPE}=?)")
        val selectionArgs = mutableListOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString()
        )
        addAlbumSelector(selectionParts, selectionArgs, albumPath, albumBucketId, albumBucketName)
        if (afterDateAdded != null && afterId != null) {
            selectionParts += "(${MediaStore.MediaColumns.DATE_ADDED}<? OR (${MediaStore.MediaColumns.DATE_ADDED}=? AND ${MediaStore.Files.FileColumns._ID}<?))"
            selectionArgs += afterDateAdded.toString()
            selectionArgs += afterDateAdded.toString()
            selectionArgs += afterId.toString()
        }
        queryKeysetPage(selectionParts, selectionArgs, limit, trashedOnly)
    }

    private fun addAlbumSelector(
        selectionParts: MutableList<String>,
        selectionArgs: MutableList<String>,
        albumPath: String?,
        albumBucketId: Long,
        albumBucketName: String?
    ) {
        when (val locator = albumLocator(albumPath, albumBucketId, albumBucketName)) {
            is AlbumLocator.Bucket -> {
                selectionParts += "${MediaStore.Images.ImageColumns.BUCKET_ID}=?"
                selectionArgs += locator.id.toString()
            }
            is AlbumLocator.Path -> {
                selectionParts += "${MediaStore.MediaColumns.RELATIVE_PATH}=?"
                selectionArgs += locator.path
            }
            is AlbumLocator.Name -> {
                selectionParts += "${MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME}=?"
                selectionArgs += locator.name
            }
            AlbumLocator.Unknown -> Unit
        }
    }

    suspend fun loadAlbums(): List<GalleryAlbum> = withContext(Dispatchers.IO) {
        data class AlbumAccumulator(var relativePath: String, var name: String, var count: Int, var cover: GalleryMedia?, var bucketId: Long, var bucketName: String?)
        val selection = buildString {
            append("(${MediaStore.Files.FileColumns.MEDIA_TYPE}=? OR ${MediaStore.Files.FileColumns.MEDIA_TYPE}=?)")
            if (Build.VERSION.SDK_INT >= 30) append(" AND ${MediaStore.MediaColumns.IS_TRASHED}=0")
        }
        val selectionArgs = arrayOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString()
        )
        val sortOrder = "${MediaStore.MediaColumns.DATE_ADDED} DESC, ${MediaStore.Files.FileColumns._ID} DESC"
        val grouped = linkedMapOf<String, AlbumAccumulator>()
        resolver.query(filesCollection, PROJECTION, selection, selectionArgs, sortOrder)?.use { cursor ->
            val cols = Columns(cursor)
            while (cursor.moveToNext()) {
                coroutineContext.ensureActive()
                val item = cols.read(cursor)
                val rawPath = item.relativePath.trim()
                val key = albumIdentityKey(rawPath, item.bucketId, item.bucketName)
                val fallbackName = item.bucketName?.trim().orEmpty().ifBlank { if (rawPath.isNotBlank()) albumDisplayName(rawPath) else "Depolama" }
                val displayPath = if (rawPath.isNotBlank()) normalizeRelativePath(rawPath) else ""
                val existing = grouped[key]
                if (existing == null) grouped[key] = AlbumAccumulator(displayPath, fallbackName, 1, item, item.bucketId, item.bucketName)
                else existing.count++
            }
        }
        grouped.values.map { GalleryAlbum(it.relativePath, it.name, it.count, it.cover, it.bucketId, it.bucketName) }
            .sortedBy { it.name.lowercase() }
    }

    suspend fun loadAlbumsOemSafe(): List<GalleryAlbum> = loadCompleteAlbums().albums

    suspend fun loadCompleteAlbums(): AlbumQueryOutcome = withContext(Dispatchers.IO) {
        data class Acc(
            var relativePath: String,
            var name: String,
            var count: Int,
            var cover: GalleryMedia?,
            var bucketId: Long,
            var bucketName: String?
        )
        val grouped = linkedMapOf<String, Acc>()

        val ALBUM_RICH_PROJECTION = buildList {
            add(MediaStore.MediaColumns._ID)
            add(MediaStore.MediaColumns.DISPLAY_NAME)
            add(MediaStore.MediaColumns.MIME_TYPE)
            add(MediaStore.MediaColumns.DATE_ADDED)
            add(MediaStore.Images.ImageColumns.BUCKET_ID)
            add(MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME)
            if (Build.VERSION.SDK_INT >= 29) add(MediaStore.MediaColumns.RELATIVE_PATH)
        }.toTypedArray()
        val ALBUM_CORE_PROJECTION = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.Images.ImageColumns.BUCKET_ID,
            MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME
        )

        fun queryAlbumCollectionOemSafe(collection: Uri, selection: String?) =
            albumQuerySortFallbacks().firstNotNullOfOrNull { sort ->
                runCatching { resolver.query(collection, ALBUM_RICH_PROJECTION, selection, null, sort) }.getOrNull()
                    ?: runCatching { resolver.query(collection, ALBUM_CORE_PROJECTION, selection, null, sort) }.getOrNull()
            } ?: albumQuerySortFallbacks().firstNotNullOfOrNull { sort ->
                runCatching { resolver.query(collection, ALBUM_CORE_PROJECTION, null, null, sort) }.getOrNull()
            }

        fun scan(collection: Uri, isVideo: Boolean): Boolean = runCatching {
                val selection = if (Build.VERSION.SDK_INT >= 30) "${MediaStore.MediaColumns.IS_TRASHED}=0" else null
                val resultCursor = queryAlbumCollectionOemSafe(collection, selection)
                    ?: return@runCatching false
                resultCursor.use { cursor ->
                    val idI = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                    val nameI = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                    val mimeI = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
                    val dateI = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_ADDED)
                    val bucketI = cursor.getColumnIndex(MediaStore.Images.ImageColumns.BUCKET_ID)
                    val bucketNameI = cursor.getColumnIndex(MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME)
                    val pathI = if (Build.VERSION.SDK_INT >= 29) cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH) else -1
                    while (cursor.moveToNext()) {
                        coroutineContext.ensureActive()
                        val id = cursor.getLong(idI)
                        val rawPath = if (pathI >= 0) cursor.getString(pathI).orEmpty().trim() else ""
                        val bucketId = if (bucketI >= 0) cursor.getLong(bucketI) else 0L
                        val bucketName = if (bucketNameI >= 0) cursor.getString(bucketNameI) else null
                        val dateAdded = if (dateI >= 0) cursor.getLong(dateI) else 0L
                        val item = GalleryMedia(
                            id = id,
                            uri = ContentUris.withAppendedId(collection, id),
                            name = cursor.getString(nameI).orEmpty(),
                            mimeType = if (mimeI >= 0) cursor.getString(mimeI) else null,
                            isVideo = isVideo,
                            dateAdded = dateAdded,
                            dateModified = 0L,
                            dateTaken = 0L,
                            width = 0,
                            height = 0,
                            bucketId = bucketId,
                            bucketName = bucketName,
                            relativePath = rawPath,
                            size = 0L,
                            durationMs = 0L,
                            isTrashed = false
                        )
                        val key = albumIdentityKey(rawPath, bucketId, bucketName)
                        val displayPath = if (rawPath.isNotBlank()) normalizeRelativePath(rawPath) else ""
                        val displayName = bucketName?.trim().orEmpty().ifBlank {
                            if (displayPath.isNotBlank()) albumDisplayName(displayPath) else "Depolama"
                        }
                        val existing = grouped[key]
                        if (existing == null) {
                            grouped[key] = Acc(displayPath, displayName, 1, item, bucketId, bucketName)
                        } else {
                            existing.count++
                            if ((existing.cover?.dateAdded ?: Long.MIN_VALUE) < dateAdded) existing.cover = item
                        }
                    }
                }
                true
            }.getOrDefault(false)

        val imagesLoaded = scan(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, false)
        val videosLoaded = scan(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true)
        val primary = grouped.values
            .map { GalleryAlbum(it.relativePath, it.name, it.count, it.cover, it.bucketId, it.bucketName) }
            .sortedBy { it.name.lowercase() }
        albumQueryOutcome(primary, imageFailed = !imagesLoaded, videoFailed = !videosLoaded)
    }

    suspend fun loadAllInAlbum(album: GalleryAlbum): List<GalleryMedia> = loadAllInAlbumOemSafe(album)

    suspend fun loadAllInAlbumOemSafe(album: GalleryAlbum): List<GalleryMedia> = withContext(Dispatchers.IO) {
        val result = ArrayList<GalleryMedia>()
        val locator = albumLocator(
            album.relativePath.ifBlank { null },
            album.bucketId,
            album.bucketName
        )

        fun scan(collection: Uri, isVideo: Boolean) {
            val projection = buildList {
                add(MediaStore.MediaColumns._ID)
                add(MediaStore.MediaColumns.DISPLAY_NAME)
                add(MediaStore.MediaColumns.MIME_TYPE)
                add(MediaStore.MediaColumns.DATE_ADDED)
                add(MediaStore.MediaColumns.DATE_MODIFIED)
                add(MediaStore.MediaColumns.WIDTH)
                add(MediaStore.MediaColumns.HEIGHT)
                add(MediaStore.Images.ImageColumns.BUCKET_ID)
                add(MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME)
                if (Build.VERSION.SDK_INT >= 29) add(MediaStore.MediaColumns.RELATIVE_PATH)
                add(MediaStore.MediaColumns.SIZE)
                if (isVideo) add(MediaStore.Video.VideoColumns.DURATION)
                if (Build.VERSION.SDK_INT >= 30) add(MediaStore.MediaColumns.IS_TRASHED)
            }.toTypedArray()

            val selectionParts = mutableListOf<String>()
            val selectionArgs = mutableListOf<String>()
            if (Build.VERSION.SDK_INT >= 30) selectionParts += "${MediaStore.MediaColumns.IS_TRASHED}=0"
            val lookup = albumLookupKeys(album.relativePath, album.bucketId, album.bucketName)
            val albumParts = mutableListOf<String>()
            lookup.forEach { key ->
                when {
                    key.startsWith("path:") -> {
                        albumParts += "${MediaStore.MediaColumns.RELATIVE_PATH}=?"
                        selectionArgs += key.removePrefix("path:")
                    }
                    key.startsWith("bucket:") -> {
                        albumParts += "${MediaStore.Images.ImageColumns.BUCKET_ID}=?"
                        selectionArgs += key.removePrefix("bucket:")
                    }
                    key.startsWith("name:") -> {
                        albumParts += "${MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME}=?"
                        selectionArgs += key.removePrefix("name:")
                    }
                }
            }
            if (albumParts.isEmpty()) return
            selectionParts += albumParts.joinToString(prefix = "(", postfix = ")", separator = " OR ")

            val selection = selectionParts.takeIf { it.isNotEmpty() }?.joinToString(" AND ")
            val args = selectionArgs.takeIf { it.isNotEmpty() }?.toTypedArray()
            val sort = "${MediaStore.MediaColumns.DATE_ADDED} DESC, ${MediaStore.MediaColumns._ID} DESC"
            resolver.query(collection, projection, selection, args, sort)?.use { cursor ->
                val idI = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameI = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val mimeI = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
                val dateI = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_ADDED)
                val modifiedI = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
                val widthI = cursor.getColumnIndex(MediaStore.MediaColumns.WIDTH)
                val heightI = cursor.getColumnIndex(MediaStore.MediaColumns.HEIGHT)
                val bucketI = cursor.getColumnIndex(MediaStore.Images.ImageColumns.BUCKET_ID)
                val bucketNameI = cursor.getColumnIndex(MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME)
                val pathI = if (Build.VERSION.SDK_INT >= 29) cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH) else -1
                val sizeI = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
                val durationI = if (isVideo) cursor.getColumnIndex(MediaStore.Video.VideoColumns.DURATION) else -1
                while (cursor.moveToNext()) {
                    coroutineContext.ensureActive()
                    val id = cursor.getLong(idI)
                    result += GalleryMedia(
                        id = id,
                        uri = ContentUris.withAppendedId(collection, id),
                        name = cursor.getString(nameI).orEmpty(),
                        mimeType = if (mimeI >= 0) cursor.getString(mimeI) else null,
                        isVideo = isVideo,
                        dateAdded = if (dateI >= 0) cursor.getLong(dateI) else 0L,
                        dateModified = if (modifiedI >= 0) cursor.getLong(modifiedI) else 0L,
                        dateTaken = 0L,
                        width = if (widthI >= 0) cursor.getInt(widthI) else 0,
                        height = if (heightI >= 0) cursor.getInt(heightI) else 0,
                        bucketId = if (bucketI >= 0) cursor.getLong(bucketI) else 0L,
                        bucketName = if (bucketNameI >= 0) cursor.getString(bucketNameI) else null,
                        relativePath = if (pathI >= 0) cursor.getString(pathI).orEmpty() else "",
                        size = if (sizeI >= 0) cursor.getLong(sizeI) else 0L,
                        durationMs = if (durationI >= 0) cursor.getLong(durationI) else 0L,
                        isTrashed = false
                    )
                }
            }
        }

        scan(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, false)
        scan(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true)
        result.sortedWith(compareByDescending<GalleryMedia> { it.dateAdded }.thenByDescending { it.id })
    }

    suspend fun findExactDuplicates(onProgress: (Int) -> Unit = {}): List<List<GalleryMedia>> = withContext(Dispatchers.IO) {
        val selection = "(${MediaStore.Files.FileColumns.MEDIA_TYPE}=? OR ${MediaStore.Files.FileColumns.MEDIA_TYPE}=?) AND ${MediaStore.MediaColumns.SIZE}>0"
        val args = Bundle().apply {
            putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
            putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, arrayOf(MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(), MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString()))
            putStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS, arrayOf(MediaStore.MediaColumns.SIZE, MediaStore.Files.FileColumns._ID))
            putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION, ContentResolver.QUERY_SORT_DIRECTION_ASCENDING)
        }
        val duplicates = mutableListOf<List<GalleryMedia>>()
        resolver.query(filesCollection, PROJECTION, args, null)?.use { cursor ->
            val cols = Columns(cursor)
            var currentSize = -1L
            val sameSize = mutableListOf<GalleryMedia>()
            var scanned = 0
            suspend fun flushSizeGroup() {
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

    private fun queryPage(selectionParts: List<String>, selectionArgs: List<String>, offset: Int, limit: Int, trashedOnly: Boolean): List<GalleryMedia> {
        val args = Bundle().apply {
            putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selectionParts.joinToString(" AND "))
            putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selectionArgs.toTypedArray())
            putStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS, arrayOf(MediaStore.MediaColumns.DATE_ADDED, MediaStore.Files.FileColumns._ID))
            putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION, ContentResolver.QUERY_SORT_DIRECTION_DESCENDING)
            putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
            putInt(ContentResolver.QUERY_ARG_OFFSET, offset)
            if (Build.VERSION.SDK_INT >= 30 && trashedOnly) putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_ONLY)
        }
        return queryMedia(args, limit)
    }

    private fun queryKeysetPage(
        selectionParts: List<String>,
        selectionArgs: List<String>,
        limit: Int,
        trashedOnly: Boolean
    ): List<GalleryMedia> {
        val args = Bundle().apply {
            putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selectionParts.joinToString(" AND "))
            putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selectionArgs.toTypedArray())
            putStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS, arrayOf(MediaStore.MediaColumns.DATE_ADDED, MediaStore.Files.FileColumns._ID))
            putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION, ContentResolver.QUERY_SORT_DIRECTION_DESCENDING)
            putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
            if (Build.VERSION.SDK_INT >= 30 && trashedOnly) putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_ONLY)
        }
        return queryMedia(args, limit)
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
        private val dateModified = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
        private val dateTaken = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_TAKEN)
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
                dateModified = cursor.getLong(dateModified),
                dateTaken = if (dateTaken >= 0) cursor.getLong(dateTaken) else 0L,
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
            add(MediaStore.MediaColumns.DATE_MODIFIED)
            add(MediaStore.MediaColumns.DATE_TAKEN)
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
