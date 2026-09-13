package com.atmaca.hizlisil

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract

class TreeDeleteEngine(private val resolver: ContentResolver) {
    fun deleteTreeContents(
        treeUri: Uri,
        onProgress: ((DeleteStats) -> Unit)? = null
    ): DeleteStats {
        val rootDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
        val rootDocumentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootDocumentId)
        return DeleteCore(AndroidTreeOps(resolver, treeUri)).deleteSubtree(rootDocumentUri, onProgress)
    }
}
