package com.sarilacivert.galeri.data

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.LinkedHashMap
import java.util.Locale

class MediaRepository(private val context: Context) {
    private val resolver: ContentResolver = context.contentResolver
    private val cacheMutex = Mutex()

    @Volatile
    private var mediaCache: List<MediaItem>? = null

    @Volatile
    private var cacheBuiltAtMs: Long = 0L

    private val cacheTtlMs = 2_000L

    fun invalidateCache() {
        mediaCache = null
        cacheBuiltAtMs = 0L
    }

    suspend fun loadAll(
        showImages: Boolean = true,
        showVideos: Boolean = true,
        includeTrashed: Boolean = false,
        onlyTrashed: Boolean = false
    ): List<MediaItem> = withContext(Dispatchers.IO) {
        val base = if (includeTrashed || onlyTrashed) {
            buildMediaList(includeTrashed, onlyTrashed)
        } else {
            val now = SystemClock.elapsedRealtime()
            val cached = mediaCache
            if (cached != null && now - cacheBuiltAtMs <= cacheTtlMs) {
                cached
            } else {
                cacheMutex.withLock {
                    val again = mediaCache
                    val nowInside = SystemClock.elapsedRealtime()
                    if (again != null && nowInside - cacheBuiltAtMs <= cacheTtlMs) {
                        again
                    } else {
                        buildMediaList(false, false).also {
                            mediaCache = it
                            cacheBuiltAtMs = SystemClock.elapsedRealtime()
                        }
                    }
                }
            }
        }

        when {
            showImages && showVideos -> base
            showImages -> base.filterNot { it.isVideo }
            showVideos -> base.filter { it.isVideo }
            else -> emptyList()
        }
    }

    private fun buildMediaList(includeTrashed: Boolean, onlyTrashed: Boolean): List<MediaItem> {
        val out = ArrayList<MediaItem>(4096)
        out += queryCollection(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, false, includeTrashed, onlyTrashed)
        out += queryCollection(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, includeTrashed, onlyTrashed)
        return out.sortedByDescending { maxOf(it.dateTaken, it.dateAdded) }
    }

    suspend fun loadAlbums(
        showImages: Boolean = true,
        showVideos: Boolean = true,
        sort: AlbumSort = AlbumSort.NEWEST
    ): List<Album> = withContext(Dispatchers.Default) {
        data class Acc(
            var count: Int,
            var cover: MediaItem,
            var hasVideo: Boolean,
            var newestDate: Long
        )

        // groupBy binlerce öğede her albüm için yeni List oluşturup GC baskısı yapıyordu.
        // Tek geçişte sadece albüm özeti tutuluyor.
        val map = LinkedHashMap<String, Acc>()
        for (item in loadAll(showImages, showVideos)) {
            val whenMs = maxOf(item.dateTaken, item.dateAdded)
            val acc = map[item.albumPath]
            if (acc == null) {
                map[item.albumPath] = Acc(1, item, item.isVideo, whenMs)
            } else {
                acc.count++
                acc.hasVideo = acc.hasVideo || item.isVideo
                if (whenMs > acc.newestDate) {
                    acc.newestDate = whenMs
                    acc.cover = item
                }
            }
        }

        val albums = ArrayList<Album>(map.size)
        for ((path, acc) in map) {
            albums += Album(
                name = acc.cover.albumName,
                path = path,
                count = acc.count,
                coverUri = acc.cover.uri,
                hasVideo = acc.hasVideo,
                newestDate = acc.newestDate
            )
        }

        when (sort) {
            AlbumSort.NEWEST -> albums.sortedByDescending { it.newestDate }
            AlbumSort.NAME -> albums.sortedBy { it.name.lowercase(Locale.getDefault()) }
            AlbumSort.COUNT -> albums.sortedByDescending { it.count }
        }
    }

    suspend fun loadAlbum(
        albumPath: String,
        showImages: Boolean = true,
        showVideos: Boolean = true,
        sort: MediaSort = MediaSort.NEWEST
    ): List<MediaItem> = withContext(Dispatchers.Default) {
        sortMedia(loadAll(showImages, showVideos).filter { it.albumPath == albumPath }, sort)
    }

    suspend fun search(
        text: String,
        showImages: Boolean = true,
        showVideos: Boolean = true,
        sort: MediaSort = MediaSort.NEWEST
    ): List<MediaItem> = withContext(Dispatchers.Default) {
        val q = text.trim().lowercase(Locale.getDefault())
        val items = if (q.isBlank()) emptyList() else loadAll(showImages, showVideos).filter {
            it.name.lowercase(Locale.getDefault()).contains(q) ||
                it.albumName.lowercase(Locale.getDefault()).contains(q) ||
                it.albumPath.lowercase(Locale.getDefault()).contains(q)
        }
        sortMedia(items, sort)
    }

    suspend fun loadFavoriteItems(favoriteUris: Set<String>, sort: MediaSort = MediaSort.NEWEST): List<MediaItem> {
        if (favoriteUris.isEmpty()) return emptyList()
        val favoriteSet = favoriteUris.toHashSet()
        return sortMedia(loadAll().filter { it.uri.toString() in favoriteSet }, sort)
    }

    suspend fun loadTrash(sort: MediaSort = MediaSort.NEWEST): List<MediaItem> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return emptyList()
        return sortMedia(loadAll(includeTrashed = true, onlyTrashed = true), sort)
    }

    fun createTrashRequest(items: Collection<MediaItem>, trash: Boolean): IntentSender? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || items.isEmpty()) return null
        return runCatching { MediaStore.createTrashRequest(resolver, items.map { it.uri }, trash).intentSender }.getOrNull()
    }

    fun createDeleteRequest(items: Collection<MediaItem>): IntentSender? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || items.isEmpty()) return null
        return runCatching { MediaStore.createDeleteRequest(resolver, items.map { it.uri }).intentSender }.getOrNull()
    }

    fun createWriteRequest(items: Collection<MediaItem>): IntentSender? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || items.isEmpty()) return null
        return runCatching { MediaStore.createWriteRequest(resolver, items.map { it.uri }).intentSender }.getOrNull()
    }

    suspend fun deleteLegacy(item: MediaItem): Boolean = withContext(Dispatchers.IO) {
        val deleted = runCatching { resolver.delete(item.uri, null, null) > 0 }.getOrDefault(false)
        if (deleted) invalidateCache()
        deleted
    }

    suspend fun rename(item: MediaItem, newName: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val clean = newName.trim().ifBlank { error("Dosya adı boş olamaz") }
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, clean)
            }
            val changed = resolver.update(item.uri, values, null, null)
            if (changed <= 0) error("Dosya adı değiştirilemedi")
            invalidateCache()
        }
    }

    suspend fun copyToTree(item: MediaItem, treeUri: Uri): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            val tree = DocumentFile.fromTreeUri(context, treeUri) ?: error("Hedef klasör açılamadı")
            val mime = item.mimeType.ifBlank { if (item.isVideo) "video/*" else "image/*" }
            val target = tree.createFile(mime, item.name) ?: error("Hedef dosya oluşturulamadı")
            resolver.openInputStream(item.uri).use { input ->
                requireNotNull(input) { "Kaynak dosya açılamadı" }
                resolver.openOutputStream(target.uri, "w").use { output ->
                    requireNotNull(output) { "Hedef dosya açılamadı" }
                    input.copyTo(output, 1024 * 1024)
                }
            }
            target.uri
        }
    }

    suspend fun mediaInfo(item: MediaItem): String = withContext(Dispatchers.IO) {
        val date = maxOf(item.dateTaken, item.dateAdded)
        val dateText = if (date > 0) {
            val millis = if (date < 100_000_000_000L) date * 1000 else date
            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.getDefault()).format(Date(millis))
        } else "Bilinmiyor"

        val base = buildString {
            appendLine("Dosya: ${item.name}")
            appendLine("Tür: ${item.mimeType.ifBlank { "Bilinmiyor" }}")
            appendLine("Boyut: ${formatFileSize(item.size)}")
            if (item.width > 0 && item.height > 0) appendLine("Çözünürlük: ${item.width} × ${item.height}")
            if (item.isVideo && item.duration > 0) appendLine("Süre: ${formatDuration(item.duration)}")
            appendLine("Tarih: $dateText")
            appendLine("Albüm: ${item.albumName}")
            appendLine("Klasör: ${item.albumPath}")
        }

        if (item.isVideo) return@withContext base

        val exifText = runCatching {
            resolver.openInputStream(item.uri)?.use { input ->
                val exif = ExifInterface(input)
                buildString {
                    val make = exif.getAttribute(ExifInterface.TAG_MAKE)
                    val model = exif.getAttribute(ExifInterface.TAG_MODEL)
                    val exposure = exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME)
                    val iso = exif.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY)
                    val focal = exif.getAttribute(ExifInterface.TAG_FOCAL_LENGTH)
                    if (!make.isNullOrBlank() || !model.isNullOrBlank()) appendLine("Kamera: ${listOfNotNull(make, model).joinToString(" ")}")
                    if (!exposure.isNullOrBlank()) appendLine("Pozlama: $exposure")
                    if (!iso.isNullOrBlank()) appendLine("ISO: $iso")
                    if (!focal.isNullOrBlank()) appendLine("Odak: $focal")
                }
            }.orEmpty()
        }.getOrDefault("")
        base + exifText
    }

    private fun queryCollection(
        collection: Uri,
        isVideo: Boolean,
        includeTrashed: Boolean,
        onlyTrashed: Boolean
    ): List<MediaItem> {
        val projection = buildList {
            add(MediaStore.MediaColumns._ID)
            add(MediaStore.MediaColumns.DISPLAY_NAME)
            add(MediaStore.MediaColumns.MIME_TYPE)
            add(MediaStore.MediaColumns.SIZE)
            add(MediaStore.MediaColumns.DATE_ADDED)
            add(MediaStore.MediaColumns.DATE_MODIFIED)
            add(MediaStore.MediaColumns.WIDTH)
            add(MediaStore.MediaColumns.HEIGHT)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.MediaColumns.RELATIVE_PATH)
                add(if (isVideo) MediaStore.Video.Media.DATE_TAKEN else MediaStore.Images.Media.DATE_TAKEN)
            } else {
                @Suppress("DEPRECATION") add(MediaStore.MediaColumns.DATA)
            }
            if (isVideo) add(MediaStore.Video.Media.DURATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) add(MediaStore.MediaColumns.IS_TRASHED)
        }.toTypedArray()

        val cursor: Cursor? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && (includeTrashed || onlyTrashed)) {
            val args = Bundle().apply {
                putInt(
                    MediaStore.QUERY_ARG_MATCH_TRASHED,
                    if (onlyTrashed) MediaStore.MATCH_ONLY else MediaStore.MATCH_INCLUDE
                )
                putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, "${MediaStore.MediaColumns.DATE_ADDED} DESC")
            }
            resolver.query(collection, projection, args, null)
        } else {
            resolver.query(collection, projection, null, null, "${MediaStore.MediaColumns.DATE_ADDED} DESC")
        }

        val out = ArrayList<MediaItem>()
        cursor?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val mimeCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
            val sizeCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val addedCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            val widthCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH)
            val heightCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT)
            val relativeCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) c.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH) else -1
            val takenCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) c.getColumnIndex(if (isVideo) MediaStore.Video.Media.DATE_TAKEN else MediaStore.Images.Media.DATE_TAKEN) else -1
            @Suppress("DEPRECATION")
            val dataCol = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) c.getColumnIndex(MediaStore.MediaColumns.DATA) else -1
            val durationCol = if (isVideo) c.getColumnIndex(MediaStore.Video.Media.DURATION) else -1
            val trashedCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) c.getColumnIndex(MediaStore.MediaColumns.IS_TRASHED) else -1

            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val uri = ContentUris.withAppendedId(collection, id)
                val name = c.getString(nameCol) ?: if (isVideo) "Video" else "Fotoğraf"
                val relativePath = if (relativeCol >= 0) c.getString(relativeCol).orEmpty().trimEnd('/') else ""
                val dataPath = if (dataCol >= 0) c.getString(dataCol).orEmpty() else ""
                val albumPath = if (relativePath.isNotBlank()) relativePath else File(dataPath).parent.orEmpty()
                val albumName = albumPath.trimEnd('/').substringAfterLast('/').ifBlank { "Diğer" }
                val trashed = trashedCol >= 0 && c.getInt(trashedCol) == 1
                if (onlyTrashed && !trashed) continue
                if (!includeTrashed && trashed) continue

                out += MediaItem(
                    id = id,
                    uri = uri,
                    name = name,
                    mimeType = c.getString(mimeCol).orEmpty(),
                    size = c.getLong(sizeCol),
                    dateAdded = c.getLong(addedCol),
                    dateTaken = if (takenCol >= 0) c.getLong(takenCol) else 0L,
                    duration = if (durationCol >= 0) c.getLong(durationCol) else 0L,
                    width = if (widthCol >= 0) c.getInt(widthCol) else 0,
                    height = if (heightCol >= 0) c.getInt(heightCol) else 0,
                    albumPath = albumPath,
                    albumName = albumName,
                    isVideo = isVideo,
                    isTrashed = trashed
                )
            }
        }
        return out
    }

    private fun sortMedia(items: List<MediaItem>, sort: MediaSort): List<MediaItem> = when (sort) {
        MediaSort.NEWEST -> items.sortedByDescending { maxOf(it.dateTaken, it.dateAdded) }
        MediaSort.OLDEST -> items.sortedBy { maxOf(it.dateTaken, it.dateAdded) }
        MediaSort.NAME_ASC -> items.sortedBy { it.name.lowercase(Locale.getDefault()) }
        MediaSort.NAME_DESC -> items.sortedByDescending { it.name.lowercase(Locale.getDefault()) }
        MediaSort.SIZE_DESC -> items.sortedByDescending { it.size }
    }

    companion object {
        fun formatFileSize(bytes: Long): String {
            if (bytes <= 0) return "0 B"
            val kb = bytes / 1024.0
            if (kb < 1024) return String.format(Locale.getDefault(), "%.1f KB", kb)
            val mb = kb / 1024.0
            if (mb < 1024) return String.format(Locale.getDefault(), "%.1f MB", mb)
            return String.format(Locale.getDefault(), "%.2f GB", mb / 1024.0)
        }

        fun formatDuration(ms: Long): String {
            val total = ms / 1000
            val hours = total / 3600
            val minutes = (total % 3600) / 60
            val seconds = total % 60
            return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%02d:%02d".format(minutes, seconds)
        }
    }
}
