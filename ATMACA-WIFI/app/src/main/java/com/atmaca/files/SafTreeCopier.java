package com.atmaca.files;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

public final class SafTreeCopier {
    private SafTreeCopier() {}

    public interface ProgressListener {
        void onProgress(int completed, int total, String currentName);
    }

    public static int copyTree(Context context, Uri sourceTree, Uri destTree) throws Exception {
        return copyTree(context, sourceTree, destTree, null);
    }

    public static int copyTree(Context context, Uri sourceTree, Uri destTree, ProgressListener listener) throws Exception {
        ContentResolver r = context.getContentResolver();
        Uri sourceRoot = DocumentsContract.buildDocumentUriUsingTree(sourceTree, DocumentsContract.getTreeDocumentId(sourceTree));
        Uri destRoot = DocumentsContract.buildDocumentUriUsingTree(destTree, DocumentsContract.getTreeDocumentId(destTree));
        int total = countFiles(r, sourceTree, sourceRoot);
        int[] completed = {0};
        return copyChildren(r, sourceTree, sourceRoot, destRoot, total, completed, listener);
    }

    public static int copyFilesToTree(Context context, List<Uri> files, Uri destTree) throws Exception {
        ContentResolver r = context.getContentResolver();
        Uri destRoot = DocumentsContract.buildDocumentUriUsingTree(destTree, DocumentsContract.getTreeDocumentId(destTree));
        int count = 0;
        for (Uri src : files) {
            String name = displayName(r, src);
            String mime = r.getType(src);
            if (mime == null) mime = "application/octet-stream";
            Uri existing = findChild(r, destRoot, name);
            if (existing == null) {
                Uri dest = DocumentsContract.createDocument(r, destRoot, mime, name);
                if (dest == null) throw new IllegalStateException("Hedef dosya oluşturulamadı: " + name);
                copyStream(r, src, dest);
            }
            count++;
        }
        return count;
    }

    private static int countFiles(ContentResolver r, Uri sourceTree, Uri sourceParent) throws Exception {
        int count = 0;
        String sourceId = DocumentsContract.getDocumentId(sourceParent);
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(sourceTree, sourceId);
        String[] cols = {DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_MIME_TYPE};
        try (Cursor c = r.query(children, cols, null, null, null)) {
            if (c == null) return 0;
            while (c.moveToNext()) {
                String id = c.getString(0);
                String mime = c.getString(1);
                Uri src = DocumentsContract.buildDocumentUriUsingTree(sourceTree, id);
                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) count += countFiles(r, sourceTree, src);
                else count++;
            }
        }
        return count;
    }

    private static int copyChildren(ContentResolver r, Uri sourceTree, Uri sourceParent, Uri destParent,
                                    int total, int[] completed, ProgressListener listener) throws Exception {
        int count = 0;
        String sourceId = DocumentsContract.getDocumentId(sourceParent);
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(sourceTree, sourceId);
        String[] cols = {DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE};
        try (Cursor c = r.query(children, cols, null, null, null)) {
            if (c == null) return 0;
            while (c.moveToNext()) {
                String id = c.getString(0);
                String name = c.getString(1);
                String mime = c.getString(2);
                Uri src = DocumentsContract.buildDocumentUriUsingTree(sourceTree, id);
                Uri existing = findChild(r, destParent, name);
                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                    Uri destDir = existing;
                    if (destDir == null) destDir = DocumentsContract.createDocument(r, destParent, DocumentsContract.Document.MIME_TYPE_DIR, name);
                    if (destDir == null) throw new IllegalStateException("Hedef klasör oluşturulamadı: " + name);
                    count += copyChildren(r, sourceTree, src, destDir, total, completed, listener);
                } else {
                    if (listener != null) listener.onProgress(completed[0], total, name);
                    if (existing == null) {
                        Uri dest = DocumentsContract.createDocument(r, destParent, mime == null ? "application/octet-stream" : mime, name);
                        if (dest == null) throw new IllegalStateException("Hedef dosya oluşturulamadı: " + name);
                        copyStream(r, src, dest);
                    }
                    count++;
                    completed[0]++;
                    if (listener != null) listener.onProgress(completed[0], total, name);
                }
            }
        }
        return count;
    }

    private static Uri findChild(ContentResolver r, Uri parent, String name) {
        try {
            Uri tree = parent;
            String parentId = DocumentsContract.getDocumentId(parent);
            Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId);
            String[] cols = {DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME};
            try (Cursor c = r.query(children, cols, null, null, null)) {
                if (c != null) {
                    while (c.moveToNext()) {
                        if (name.equals(c.getString(1))) {
                            return DocumentsContract.buildDocumentUriUsingTree(parent, c.getString(0));
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static void copyStream(ContentResolver r, Uri src, Uri dest) throws Exception {
        try (InputStream in = r.openInputStream(src); OutputStream out = r.openOutputStream(dest)) {
            if (in == null || out == null) throw new IllegalStateException("Dosya açılamadı");
            byte[] buf = new byte[1024 * 1024];
            int n;
            while ((n = in.read(buf)) >= 0) out.write(buf, 0, n);
            out.flush();
        }
    }

    private static String displayName(ContentResolver r, Uri uri) {
        try (Cursor c = r.query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (i >= 0) return safeName(c.getString(i));
            }
        } catch (Exception ignored) {}
        return safeName(uri == null ? null : uri.getLastPathSegment());
    }

    private static String safeName(String s) {
        return s == null || s.trim().isEmpty() ? "dosya" : s.replace('/', '_').replace('\\', '_');
    }
}
