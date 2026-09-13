package com.atmaca.hizlisil

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract

class AndroidTreeOps(
    private val resolver: ContentResolver,
    private val treeUri: Uri
) : TreeOps<Uri> {

    override fun childrenOf(directory: Uri): Sequence<NodeInfo> = sequence {
        val parentDocumentId = DocumentsContract.getDocumentId(directory)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri,
            parentDocumentId
        )
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )

        resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val mimeIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            while (cursor.moveToNext()) {
                val id = cursor.getString(idIndex)
                val mime = cursor.getString(mimeIndex)
                yield(NodeInfo(id, mime == DocumentsContract.Document.MIME_TYPE_DIR))
            }
        }
    }

    override fun childHandle(parent: Uri, child: NodeInfo): Uri =
        DocumentsContract.buildDocumentUriUsingTree(treeUri, child.id)

    override fun delete(handle: Uri): Boolean =
        DocumentsContract.deleteDocument(resolver, handle)
}
