package com.videokareleri.v5

import android.content.ContentResolver
import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Environment
import android.provider.MediaStore
import java.io.IOException

class FrameWriter(private val resolver: ContentResolver) {
    fun existingNames(videoBase: String): HashSet<String> {
        val names = HashSet<String>()
        resolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Images.Media.DISPLAY_NAME),
            "${MediaStore.Images.Media.RELATIVE_PATH}=?", arrayOf(relativePath(videoBase)), null)?.use { c ->
            val col = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            while (c.moveToNext()) c.getString(col)?.let(names::add)
        }
        return names
    }

    fun write(bitmap: Bitmap, videoBase: String, number: Int, existing: MutableSet<String>): Boolean {
        val display = NameUtils.frameName(videoBase, number)
        if (display in existing) return false
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, display)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, relativePath(videoBase))
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("JPEG kaydı oluşturulamadı")
        var ok = false
        try {
            resolver.openOutputStream(uri, "w")?.use {
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it)) throw IOException("JPEG yazılamadı")
            } ?: throw IOException("JPEG akışı açılamadı")
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            existing += display
            ok = true
            return true
        } finally {
            if (!ok) resolver.delete(uri, null, null)
        }
    }

    private fun relativePath(base: String) = "${Environment.DIRECTORY_PICTURES}/VideoKareleri/${NameUtils.sanitizeBaseName(base)}/"
}
