package com.atmaca.gallery

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    val bucketName: String?
)

class MediaStoreRepository(context: Context) {
    private val resolver = context.applicationContext.contentResolver

    suspend fun loadPage(
        tab: GalleryTab,
        offset: Int,
        limit: Int = PAGE_SIZE
    ): List<GalleryMedia> = withContext(Dispatchers.IO) {
        val collection = MediaStore.Files.getContentUri("external")
        val wantedType = when (tab) {
            GalleryTab.PHOTOS -> MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE
            GalleryTab.VIDEOS -> MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
        }

        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT,
            MediaStore.Images.ImageColumns.BUCKET_ID,
            MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME
        )

        val args = Bundle().apply {
            putString(
                ContentResolver.QUERY_ARG_SQL_SELECTION,
                "${MediaStore.Files.FileColumns.MEDIA_TYPE}=?"
            )
            putStringArray(
                ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,
                arrayOf(wantedType.toString())
            )
            putStringArray(
                ContentResolver.QUERY_ARG_SORT_COLUMNS,
                arrayOf(MediaStore.MediaColumns.DATE_ADDED, MediaStore.Files.FileColumns._ID)
            )
            putInt(
                ContentResolver.QUERY_ARG_SORT_DIRECTION,
                ContentResolver.QUERY_SORT_DIRECTION_DESCENDING
            )
            putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
            putInt(ContentResolver.QUERY_ARG_OFFSET, offset)
        }

        val result = ArrayList<GalleryMedia>(limit)
        resolver.query(collection, projection, args, null)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val typeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            val widthCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH)
            val heightCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT)
            val bucketIdCol = cursor.getColumnIndexOrThrow(MediaStore.Images.ImageColumns.BUCKET_ID)
            val bucketNameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val mediaType = cursor.getInt(typeCol)
                val isVideo = mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                val baseUri = if (isVideo) {
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                } else {
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                }
                result += GalleryMedia(
                    id = id,
                    uri = ContentUris.withAppendedId(baseUri, id),
                    name = cursor.getString(nameCol) ?: "",
                    mimeType = cursor.getString(mimeCol),
                    isVideo = isVideo,
                    dateAdded = cursor.getLong(dateCol),
                    width = cursor.getInt(widthCol),
                    height = cursor.getInt(heightCol),
                    bucketId = cursor.getLong(bucketIdCol),
                    bucketName = cursor.getString(bucketNameCol)
                )
            }
        }
        result
    }

    companion object {
        const val PAGE_SIZE = 120
    }
}
