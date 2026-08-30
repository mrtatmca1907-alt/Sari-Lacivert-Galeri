package com.atmaca.files;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;

public final class HddStager {
    private HddStager() {}

    public static int stage(Context context, CatalogDb db, List<Uri> uris, String remoteDir) throws Exception {
        File outbox = new File(context.getFilesDir(), "hdd_outbox");
        if (!outbox.exists() && !outbox.mkdirs()) throw new IllegalStateException("HDD bekleme klasörü oluşturulamadı");

        ContentResolver resolver = context.getContentResolver();
        int done = 0;
        for (Uri uri : uris) {
            String name = displayName(resolver, uri);
            String mime = resolver.getType(uri);
            if (mime == null || mime.trim().isEmpty()) mime = mimeFor(name);

            String id = UUID.randomUUID().toString();
            File tmp = new File(outbox, id + ".part");
            File staged = new File(outbox, id + ".bin");
            long size = 0L;
            try (InputStream in = resolver.openInputStream(uri);
                 FileOutputStream out = new FileOutputStream(tmp, false)) {
                if (in == null) throw new IllegalStateException("Dosya açılamadı: " + name);
                byte[] buf = new byte[1024 * 1024];
                int n;
                while ((n = in.read(buf)) >= 0) {
                    out.write(buf, 0, n);
                    size += n;
                }
                out.flush();
                out.getFD().sync();
            } catch (Exception e) {
                tmp.delete();
                throw e;
            }

            if (!tmp.renameTo(staged)) {
                tmp.delete();
                throw new IllegalStateException("Dosya güvenli bekleme alanına alınamadı: " + name);
            }

            try {
                db.addUpload(staged.getAbsolutePath(), remoteDir, name, mime, size);
                done++;
            } catch (Exception e) {
                staged.delete();
                throw e;
            }
        }
        return done;
    }

    private static String displayName(ContentResolver resolver, Uri uri) {
        try (Cursor c = resolver.query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (i >= 0) return safeName(c.getString(i));
            }
        } catch (Exception ignored) {}
        return safeName(uri == null ? null : uri.getLastPathSegment());
    }

    private static String safeName(String name) {
        return (name == null || name.trim().isEmpty()) ? "dosya" : name.replace('/', '_').replace('\\', '_');
    }

    private static String mimeFor(String name) {
        String ext = MimeTypeMap.getFileExtensionFromUrl(name == null ? "" : name).toLowerCase();
        String type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
        return type == null ? "application/octet-stream" : type;
    }
}
