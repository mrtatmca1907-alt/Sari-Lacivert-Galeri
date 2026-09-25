package com.atmaca.gallery

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

data class VerifiedLibrary(val items: List<GalleryMedia>, val skipped: Int, val errors: List<String>)

/** Reads MediaStore metadata, never walks storage directories or starts MediaScanner. */
class VerifiedMediaLibrary(private val resolver: ContentResolver) {
    constructor(context: Context) : this(context.applicationContext.contentResolver)

    suspend fun load(onBatch: suspend (List<GalleryMedia>) -> Unit = {}): VerifiedLibrary = withContext(Dispatchers.IO) {
        val all = ArrayList<GalleryMedia>()
        var skipped = 0
        val errors = ArrayList<String>()
        val collections = listOf(MediaStore.Images.Media.EXTERNAL_CONTENT_URI to false,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI to true)
        for ((collection, video) in collections) {
            coroutineContext.ensureActive()
            try {
                query(collection)?.use { cursor ->
                    val pending = ArrayList<GalleryMedia>(64)
                    suspend fun flush() {
                        // At most four file descriptors open at once; no bitmap decoding here.
                        val verified = coroutineScope {
                            pending.chunked(16).map { chunk -> async {
                                chunk.filter { item ->
                                    coroutineContext.ensureActive()
                                    readable(item.uri)
                                }
                            } }.awaitAll().flatten()
                        }
                        skipped += pending.size - verified.size
                        all.addAll(verified)
                        if (verified.isNotEmpty()) onBatch(verified)
                        pending.clear()
                    }
                    val columns = IndexedColumns(cursor)
                    while (cursor.moveToNext()) {
                        coroutineContext.ensureActive()
                        if (columns.pending(cursor)) continue
                        pending += columns.read(cursor, collection, video)
                        if (pending.size == 64) flush()
                    }
                    if (pending.isNotEmpty()) flush()
                } ?: error("Medya sağlayıcısı liste döndürmedi")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errors += "${if (video) "Videolar" else "Fotoğraflar"}: ${e.message ?: "okunamadı"}"
            }
        }
        VerifiedLibrary(all.distinctBy { it.uri }, skipped, errors)
    }

    private fun query(uri: Uri): Cursor? {
        val projection = buildList {
            add(MediaStore.MediaColumns._ID)
            add(MediaStore.MediaColumns.DISPLAY_NAME)
            add(MediaStore.MediaColumns.MIME_TYPE)
            add(MediaStore.MediaColumns.DATE_ADDED)
            add(MediaStore.MediaColumns.DATE_MODIFIED)
            add(MediaStore.MediaColumns.DATE_TAKEN)
            add(MediaStore.MediaColumns.WIDTH)
            add(MediaStore.MediaColumns.HEIGHT)
            add(MediaStore.MediaColumns.SIZE)
            add(MediaStore.Images.ImageColumns.BUCKET_ID)
            add(MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME)
            if (Build.VERSION.SDK_INT >= 29) {
                add(MediaStore.MediaColumns.RELATIVE_PATH)
                add(MediaStore.MediaColumns.IS_PENDING)
            }
            if (Build.VERSION.SDK_INT >= 30) add(MediaStore.MediaColumns.IS_TRASHED)
            if (uri == MediaStore.Video.Media.EXTERNAL_CONTENT_URI) add(MediaStore.Video.VideoColumns.DURATION)
        }.toTypedArray()
        // No LIMIT, OFFSET or date boundary: traverse the full indexed cursor exactly once.
        val args = Bundle().apply {
            putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, "${MediaStore.MediaColumns._ID} DESC")
            if (Build.VERSION.SDK_INT >= 30) {
                putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_INCLUDE)
                putInt(MediaStore.QUERY_ARG_MATCH_PENDING, MediaStore.MATCH_EXCLUDE)
            }
        }
        val preferred = try {
            resolver.query(uri, projection, args, null)
        } catch (e: IllegalArgumentException) {
            null
        }
        if (preferred != null && preferred.count > 0) return preferred
        preferred?.close()
        // Some OEMs silently ignore MATCH_INCLUDE or reject optional columns.
        return resolver.query(uri, null, null, null, "${MediaStore.MediaColumns._ID} DESC")
    }

    private fun readable(uri: Uri): Boolean = try {
        resolver.openFileDescriptor(uri, "r")?.use { it.statSize != 0L } ?: false
    } catch (_: java.io.IOException) {
        false
    } catch (_: SecurityException) {
        false
    } catch (_: IllegalArgumentException) {
        false
    }

    private class IndexedColumns(cursor: Cursor) {
        private val columns = cursor.columnNames.withIndex().associate { it.value to it.index }
        private fun text(c: Cursor, name: String) = columns[name]?.let { if (c.isNull(it)) null else c.getString(it) }
        private fun number(c: Cursor, name: String) = columns[name]?.let { if (c.isNull(it)) 0L else c.getLong(it) } ?: 0L
        fun pending(c: Cursor) = number(c, MediaStore.MediaColumns.IS_PENDING) != 0L
        fun read(c: Cursor, collection: Uri, video: Boolean): GalleryMedia {
            val id = number(c, MediaStore.MediaColumns._ID)
            return GalleryMedia(
                id, ContentUris.withAppendedId(collection, id),
                text(c, MediaStore.MediaColumns.DISPLAY_NAME).orEmpty(),
                text(c, MediaStore.MediaColumns.MIME_TYPE), video,
                number(c, MediaStore.MediaColumns.DATE_ADDED), number(c, MediaStore.MediaColumns.DATE_MODIFIED),
                number(c, MediaStore.MediaColumns.DATE_TAKEN),
                number(c, MediaStore.MediaColumns.WIDTH).toInt(), number(c, MediaStore.MediaColumns.HEIGHT).toInt(),
                number(c, MediaStore.Images.ImageColumns.BUCKET_ID), text(c, MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME),
                text(c, MediaStore.MediaColumns.RELATIVE_PATH).orEmpty(), number(c, MediaStore.MediaColumns.SIZE),
                if (video) number(c, MediaStore.Video.VideoColumns.DURATION) else 0L,
                number(c, MediaStore.MediaColumns.IS_TRASHED) != 0L
            )
        }
    }
}

fun verifiedAlbums(items: List<GalleryMedia>): List<GalleryAlbum> = items.asSequence()
    .filter { !it.isTrashed }
    .groupBy { albumIdentityKey(it.relativePath, it.bucketId, it.bucketName) }
    .values.map { media ->
        val cover = media.maxWith(compareBy<GalleryMedia> { it.dateAdded }.thenBy { it.id })
        val path = cover.relativePath.takeIf { it.isNotBlank() }?.let(::normalizeRelativePath).orEmpty()
        GalleryAlbum(path, cover.bucketName?.takeIf { it.isNotBlank() }
            ?: if (path.isNotBlank()) albumDisplayName(path) else "Depolama",
            media.size, cover, cover.bucketId, cover.bucketName)
    }.sortedBy { it.name.lowercase() }

fun belongsToAlbum(item: GalleryMedia, path: String?, bucketId: Long, name: String?): Boolean =
    albumIdentityKey(item.relativePath, item.bucketId, item.bucketName) == albumIdentityKey(path, bucketId, name)
