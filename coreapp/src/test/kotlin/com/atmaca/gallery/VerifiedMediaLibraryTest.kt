package com.atmaca.gallery

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import java.io.File
import java.io.FileNotFoundException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class VerifiedMediaLibraryTest {
    @Test fun readsPast1080SkipsDeletedAndBuildsAlbumsFromSameVisibleFiles() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val file = File.createTempFile("media-index-test", ".jpg", context.cacheDir).apply { writeText("actual bytes") }
        val provider = MediaProviderFake(file)
        ShadowContentResolver.registerProviderInternal("media", provider)
        var callbacks = 0
        val result = VerifiedMediaLibrary(context).load { callbacks++ }
        // 1,305 indexed rows; 8 missing backing files and 4 pending uploads are excluded.
        assertEquals("errors=${result.errors}; skipped=${result.skipped}; callbacks=$callbacks", 1293, result.items.size)
        assertEquals(8, result.skipped)
        assertTrue(callbacks > 10)
        assertFalse(result.items.any { it.id in 100L..107L || it.id in 108L..111L })
        assertEquals(5, result.items.count { it.isTrashed })
        assertEquals(1288, verifiedAlbums(result.items).sumOf { it.count })
        assertEquals(2, verifiedAlbums(result.items).size)
        assertTrue(result.errors.isEmpty())
    }

    private class MediaProviderFake(private val dataFile: File) : ContentProvider() {
        override fun onCreate() = true
        override fun getType(uri: Uri): String? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
            selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
            val columns = projection ?: error("unexpected projection")
            val cursor = MatrixCursor(columns)
            val video = uri.pathSegments.contains("video")
            val ids = if (video) 1301..1305 else 1..1300
            for (id in ids) {
                cursor.addRow(columns.map { key -> when (key) {
                    MediaStore.MediaColumns._ID -> id.toLong()
                    MediaStore.MediaColumns.DISPLAY_NAME -> "$id.jpg"
                    MediaStore.MediaColumns.MIME_TYPE -> if (video) "video/mp4" else "image/jpeg"
                    MediaStore.MediaColumns.DATE_ADDED -> id.toLong()
                    MediaStore.MediaColumns.IS_PENDING -> if (id in 108..111) 1 else 0
                    MediaStore.MediaColumns.IS_TRASHED -> if (id in 112..116) 1 else 0
                    MediaStore.MediaColumns.RELATIVE_PATH -> if (id < 700) "DCIM/Camera/" else "Pictures/Saved/"
                    MediaStore.Images.ImageColumns.BUCKET_ID -> if (id < 700) 101L else 102L
                    MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME -> if (id < 700) "Camera" else "Saved"
                    MediaStore.MediaColumns.SIZE -> 12L
                    else -> 0L
                } })
            }
            return cursor
        }
        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
            val id = uri.lastPathSegment?.toIntOrNull() ?: throw FileNotFoundException()
            if (id in 100..107) throw FileNotFoundException("deleted file")
            return ParcelFileDescriptor.open(dataFile, ParcelFileDescriptor.MODE_READ_ONLY)
        }
    }
}
