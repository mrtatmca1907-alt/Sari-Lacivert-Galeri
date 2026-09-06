package com.atmaca.gallery

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

suspend fun saveScreenshotToTree(context: Context, bitmap: Bitmap, treeUri: Uri): Uri? = withContext(Dispatchers.IO) {
    val resolver = context.applicationContext.contentResolver
    val treeId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()
        ?: return@withContext null
    val parent = runCatching { DocumentsContract.buildDocumentUriUsingTree(treeUri, treeId) }.getOrNull()
        ?: return@withContext null
    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
    val created = runCatching {
        DocumentsContract.createDocument(resolver, parent, "image/jpeg", "ATMACA_SCREEN_$stamp.jpg")
    }.getOrNull() ?: return@withContext null

    val ok = runCatching {
        resolver.openOutputStream(created, "w")?.use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, 96, output))
        } ?: error("Screenshot hedefi açılamadı")
        true
    }.getOrDefault(false)

    if (!ok) {
        runCatching { DocumentsContract.deleteDocument(resolver, created) }
        null
    } else created
}
