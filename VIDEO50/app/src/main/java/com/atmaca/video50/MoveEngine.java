package com.atmaca.video50;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;

public final class MoveEngine {
    public interface ProgressCallback {
        void onProgress(int moved, int total, String folderName, String fileName);
    }

    private MoveEngine() {}

    public static void move(Context context, List<VideoItem> items, ProgressCallback progress) throws Exception {
        File movies = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES);
        if (!movies.exists() && !movies.mkdirs()) throw new IllegalStateException("Movies klasörü oluşturulamadı");
        int moved = 0;
        for (int i = 0; i < items.size(); i++) {
            VideoItem item = items.get(i);
            String folderName = BatchPolicy.folderNameForIndex(i);
            File targetDir = new File(movies, folderName);
            if (!targetDir.exists() && !targetDir.mkdirs()) throw new IllegalStateException(folderName + " oluşturulamadı");
            moveOne(context, item, targetDir);
            moved++;
            if (progress != null) progress.onProgress(moved, items.size(), folderName, item.name);
        }
    }

    private static void moveOne(Context context, VideoItem item, File targetDir) throws Exception {
        File target = uniqueTarget(targetDir, safeName(item.name));
        if (item.filePath != null && !item.filePath.isEmpty()) {
            File source = new File(item.filePath);
            if (source.exists()) {
                if (Build.VERSION.SDK_INT >= 26) {
                    try {
                        Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE);
                        return;
                    } catch (Exception ignored) {
                        try {
                            Files.move(source.toPath(), target.toPath());
                            return;
                        } catch (Exception ignoredAgain) {}
                    }
                }
                if (source.renameTo(target)) return;
            }
        }

        ContentResolver resolver = context.getContentResolver();
        boolean written = false;
        try (InputStream in = resolver.openInputStream(item.uri); OutputStream out = new FileOutputStream(target)) {
            if (in == null) throw new IllegalStateException("Kaynak açılamadı: " + item.name);
            copy(in, out);
            written = true;
        } catch (Exception e) {
            if (target.exists()) target.delete();
            throw e;
        }

        if (written) {
            int deleted = 0;
            try { deleted = resolver.delete(item.uri, null, null); } catch (Exception ignored) {}
            if (deleted <= 0 && item.filePath != null) {
                File source = new File(item.filePath);
                if (source.exists() && source.delete()) deleted = 1;
            }
            if (deleted <= 0) {
                target.delete();
                throw new SecurityException("Kaynak silinemedi; video taşınmadı: " + item.name);
            }
            scanCreated(context, target);
        }
    }

    private static void scanCreated(Context context, File file) {
        try {
            android.media.MediaScannerConnection.scanFile(context, new String[]{file.getAbsolutePath()}, null, null);
        } catch (Exception ignored) {}
    }

    private static File uniqueTarget(File dir, String name) {
        File f = new File(dir, name);
        if (!f.exists()) return f;
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : "";
        int n = 2;
        while (f.exists()) f = new File(dir, base + " (" + (n++) + ")" + ext);
        return f;
    }

    private static String safeName(String s) {
        if (s == null || s.trim().isEmpty()) return "video";
        return s.replace('/', '_').replace('\\', '_');
    }

    private static void copy(InputStream in, OutputStream out) throws Exception {
        byte[] buf = new byte[1024 * 1024];
        int n;
        while ((n = in.read(buf)) != -1) {
            if (n > 0) out.write(buf, 0, n);
        }
        out.flush();
    }
}
