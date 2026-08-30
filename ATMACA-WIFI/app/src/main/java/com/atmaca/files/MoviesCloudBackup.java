package com.atmaca.files;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public final class MoviesCloudBackup {
    private MoviesCloudBackup() {}

    public interface ProgressListener {
        void onProgress(int completed, int total, String currentName);
    }

    public static final class Result {
        public final int total;
        public final int processed;
        public final int copied;
        public final int skipped;

        Result(int total, int processed, int copied, int skipped) {
            this.total = total;
            this.processed = processed;
            this.copied = copied;
            this.skipped = skipped;
        }
    }

    private static final class Entry {
        final Uri uri;
        final String name;
        final String mime;
        final String relativePath;

        Entry(Uri uri, String name, String mime, String relativePath) {
            this.uri = uri;
            this.name = name;
            this.mime = mime;
            this.relativePath = relativePath;
        }
    }

    public static Result backup(Context context, Uri destTree, ProgressListener listener) throws Exception {
        ContentResolver resolver = context.getContentResolver();
        List<Entry> entries = queryMovies(resolver);
        Uri destRoot = DocumentsContract.buildDocumentUriUsingTree(destTree, DocumentsContract.getTreeDocumentId(destTree));

        int processed = 0;
        int copied = 0;
        int skipped = 0;
        int total = entries.size();

        for (Entry entry : entries) {
            if (listener != null) listener.onProgress(processed, total, entry.name);
            Uri targetDir = ensureDirectoryPath(resolver, destRoot, MoviesBackupPolicy.subdirectory(entry.relativePath));
            Uri existing = findChild(resolver, targetDir, entry.name);
            if (existing == null) {
                Uri dest = DocumentsContract.createDocument(
                        resolver,
                        targetDir,
                        entry.mime == null ? "video/*" : entry.mime,
                        safeName(entry.name)
                );
                if (dest == null) throw new IllegalStateException("Hedef dosya oluşturulamadı: " + entry.name);
                copyStream(resolver, entry.uri, dest);
                copied++;
            } else {
                skipped++;
            }
            processed++;
            if (listener != null) listener.onProgress(processed, total, entry.name);
        }

        return new Result(total, processed, copied, skipped);
    }

    private static List<Entry> queryMovies(ContentResolver resolver) {
        ArrayList<Entry> out = new ArrayList<>();
        Uri collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            String[] projection = {
                    MediaStore.Video.Media._ID,
                    MediaStore.Video.Media.DISPLAY_NAME,
                    MediaStore.Video.Media.MIME_TYPE,
                    MediaStore.Video.Media.RELATIVE_PATH
            };
            String selection = MediaStore.Video.Media.RELATIVE_PATH + " LIKE ?";
            String[] args = {"Movies/%"};
            try (Cursor c = resolver.query(collection, projection, selection, args, MediaStore.Video.Media.DATE_ADDED + " ASC")) {
                if (c == null) return out;
                while (c.moveToNext()) {
                    long id = c.getLong(0);
                    String name = c.getString(1);
                    String mime = c.getString(2);
                    String relative = c.getString(3);
                    if (!MoviesBackupPolicy.isMoviesPath(relative)) continue;
                    out.add(new Entry(ContentUris.withAppendedId(collection, id), safeName(name), mime, relative));
                }
            }
        } else {
            String[] projection = {
                    MediaStore.Video.Media._ID,
                    MediaStore.Video.Media.DISPLAY_NAME,
                    MediaStore.Video.Media.MIME_TYPE,
                    MediaStore.Video.Media.DATA
            };
            String moviesPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES).getAbsolutePath();
            String selection = MediaStore.Video.Media.DATA + " LIKE ?";
            String[] args = {moviesPath + "/%"};
            try (Cursor c = resolver.query(collection, projection, selection, args, MediaStore.Video.Media.DATE_ADDED + " ASC")) {
                if (c == null) return out;
                while (c.moveToNext()) {
                    long id = c.getLong(0);
                    String name = c.getString(1);
                    String mime = c.getString(2);
                    String data = c.getString(3);
                    String relative = "Movies/";
                    if (data != null && data.startsWith(moviesPath)) {
                        String remainder = data.substring(moviesPath.length());
                        int slash = remainder.lastIndexOf('/');
                        if (slash > 0) relative = "Movies" + remainder.substring(0, slash + 1);
                    }
                    out.add(new Entry(ContentUris.withAppendedId(collection, id), safeName(name), mime, relative));
                }
            }
        }

        return out;
    }

    private static Uri ensureDirectoryPath(ContentResolver resolver, Uri root, String subdir) throws Exception {
        Uri current = root;
        if (subdir == null || subdir.trim().isEmpty()) return current;
        String[] parts = subdir.replace('\\', '/').split("/");
        for (String raw : parts) {
            String part = safeName(raw);
            if (part.isEmpty()) continue;
            Uri child = findChild(resolver, current, part);
            if (child == null) {
                child = DocumentsContract.createDocument(
                        resolver,
                        current,
                        DocumentsContract.Document.MIME_TYPE_DIR,
                        part
                );
            }
            if (child == null) throw new IllegalStateException("Hedef klasör oluşturulamadı: " + part);
            current = child;
        }
        return current;
    }

    private static Uri findChild(ContentResolver resolver, Uri parent, String name) {
        try {
            String parentId = DocumentsContract.getDocumentId(parent);
            Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(parent, parentId);
            String[] projection = {
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME
            };
            try (Cursor c = resolver.query(children, projection, null, null, null)) {
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

    private static void copyStream(ContentResolver resolver, Uri src, Uri dest) throws Exception {
        try (InputStream in = resolver.openInputStream(src); OutputStream out = resolver.openOutputStream(dest)) {
            if (in == null || out == null) throw new IllegalStateException("Video açılamadı");
            byte[] buffer = new byte[1024 * 1024];
            int n;
            while ((n = in.read(buffer)) >= 0) out.write(buffer, 0, n);
            out.flush();
        }
    }

    private static String safeName(String value) {
        if (value == null || value.trim().isEmpty()) return "video";
        return value.replace('/', '_').replace('\\', '_').trim();
    }
}
