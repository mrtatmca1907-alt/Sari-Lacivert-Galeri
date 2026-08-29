package com.atmaca.filemanager;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class ThumbnailLoader {
    private final ExecutorService pool = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "AtmacaThumb");
        t.setPriority(Thread.NORM_PRIORITY - 1);
        return t;
    });
    private final Handler main = new Handler(Looper.getMainLooper());
    private final LruCache<String, Bitmap> cache;

    ThumbnailLoader() {
        int maxKb = (int) Math.min(16 * 1024L, Math.max(4 * 1024L, Runtime.getRuntime().maxMemory() / 1024L / 16L));
        cache = new LruCache<String, Bitmap>(maxKb) {
            @Override protected int sizeOf(String key, Bitmap value) {
                return Math.max(1, value.getAllocationByteCount() / 1024);
            }
        };
    }

    void load(File file, ImageView target, int targetPx) {
        if (file == null || target == null) return;
        String key = file.getAbsolutePath() + ':' + file.lastModified() + ':' + file.length();
        target.setTag(key);
        Bitmap hit = cache.get(key);
        if (hit != null && !hit.isRecycled()) {
            target.setImageBitmap(hit);
            return;
        }
        target.setImageDrawable(null);
        pool.execute(() -> {
            Bitmap b = decodeSampled(file.getAbsolutePath(), targetPx, targetPx);
            if (b != null) cache.put(key, b);
            main.post(() -> {
                Object tag = target.getTag();
                if (key.equals(tag) && b != null && !b.isRecycled()) target.setImageBitmap(b);
            });
        });
    }

    static Bitmap decodeSampled(String path, int reqW, int reqH) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
            int sample = 1;
            while ((bounds.outWidth / sample) > reqW * 2 || (bounds.outHeight / sample) > reqH * 2) sample *= 2;
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = Math.max(1, sample);
            opts.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeFile(path, opts);
        } catch (OutOfMemoryError | RuntimeException e) {
            return null;
        }
    }

    void shutdown() {
        pool.shutdownNow();
        cache.evictAll();
    }
}
