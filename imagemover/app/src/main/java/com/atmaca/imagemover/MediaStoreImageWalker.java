package com.atmaca.imagemover;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

public final class MediaStoreImageWalker {
    public void walk(Context context, Path targetDirectory, Consumer<Path> consumer) {
        if (context == null || targetDirectory == null || consumer == null) {
            return;
        }

        ContentResolver resolver = context.getContentResolver();
        String[] projection;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            projection = new String[]{
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DISPLAY_NAME,
                    MediaStore.Images.Media.RELATIVE_PATH
            };
        } else {
            projection = new String[]{
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DISPLAY_NAME,
                    MediaStore.Images.Media.DATA
            };
        }

        Path normalizedTarget = targetDirectory.toAbsolutePath().normalize();
        try (Cursor cursor = resolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                MediaStore.Images.Media._ID + " ASC"
        )) {
            if (cursor == null) {
                return;
            }

            int nameColumn = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME);
            int locationColumn = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                    ? cursor.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH)
                    : cursor.getColumnIndex(MediaStore.Images.Media.DATA);

            while (cursor.moveToNext()) {
                if (Thread.currentThread().isInterrupted()) {
                    return;
                }

                String name = nameColumn >= 0 ? cursor.getString(nameColumn) : null;
                String location = locationColumn >= 0 ? cursor.getString(locationColumn) : null;
                Path source = resolveSource(name, location);
                if (source == null) {
                    continue;
                }

                Path normalizedSource = source.toAbsolutePath().normalize();
                if (normalizedSource.startsWith(normalizedTarget)) {
                    continue;
                }

                try {
                    if (Files.isRegularFile(source)) {
                        consumer.accept(source);
                    }
                } catch (SecurityException ignored) {
                    // Tek bir kayda erişilememesi bütün taşıma işlemini durdurmaz.
                }
            }
        } catch (Throwable ignored) {
            // Vendor MediaStore hatasında servis dosya sistemi yedek yoluna devam eder.
        }
    }

    private Path resolveSource(String name, String location) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (name == null || name.isEmpty() || location == null) {
                return null;
            }
            File root = Environment.getExternalStorageDirectory();
            return new File(new File(root, location), name).toPath();
        }

        if (location == null || location.isEmpty()) {
            return null;
        }
        return new File(location).toPath();
    }
}
