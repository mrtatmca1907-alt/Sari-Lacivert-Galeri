package com.atmaca.insgetorganizer;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class Organizer {
    private static final Set<String> IMAGE_EXTENSIONS = new HashSet<>(Arrays.asList(
            "jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif", "avif"
    ));
    private static final Set<String> VIDEO_EXTENSIONS = new HashSet<>(Arrays.asList(
            "mp4", "mkv", "mov", "avi", "webm", "3gp", "m4v", "ts", "mpeg", "mpg"
    ));

    public interface ProgressListener {
        void onProgress(Result current, String message);
    }

    public static final class Result {
        public int moved;
        public int skipped;
        public int errors;

        public Result copy() {
            Result result = new Result();
            result.moved = moved;
            result.skipped = skipped;
            result.errors = errors;
            return result;
        }
    }

    public Result organize(File picturesInsget, File moviesInsget, ProgressListener listener) {
        Result result = new Result();
        organizeFolder(picturesInsget, IMAGE_EXTENSIONS, "Fotoğraflar", result, listener);
        organizeFolder(moviesInsget, VIDEO_EXTENSIONS, "Videolar", result, listener);
        return result;
    }

    private void organizeFolder(File root,
                                Set<String> allowedExtensions,
                                String label,
                                Result result,
                                ProgressListener listener) {
        if (root == null || !root.exists() || !root.isDirectory()) {
            result.skipped++;
            emit(listener, result, label + ": Insget klasörü bulunamadı");
            return;
        }

        File[] files = root.listFiles(file -> file.isFile() && allowedExtensions.contains(extensionOf(file.getName())));
        if (files == null || files.length == 0) {
            emit(listener, result, label + ": taşınacak dosya yok");
            return;
        }

        for (File source : files) {
            try {
                String group = GroupName.fromFileName(source.getName());
                File destinationDir = new File(root, group);
                if (!destinationDir.exists() && !destinationDir.mkdirs() && !destinationDir.isDirectory()) {
                    throw new IOException("Klasör oluşturulamadı: " + destinationDir.getAbsolutePath());
                }

                File destination = uniqueDestination(destinationDir, source.getName());
                Files.move(source.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE);
                result.moved++;
                emit(listener, result, label + ": " + source.getName() + " → " + group);
            } catch (Exception atomicMoveError) {
                try {
                    String group = GroupName.fromFileName(source.getName());
                    File destinationDir = new File(root, group);
                    if (!destinationDir.exists() && !destinationDir.mkdirs() && !destinationDir.isDirectory()) {
                        throw new IOException("Klasör oluşturulamadı: " + destinationDir.getAbsolutePath());
                    }
                    File destination = uniqueDestination(destinationDir, source.getName());
                    Files.move(source.toPath(), destination.toPath());
                    result.moved++;
                    emit(listener, result, label + ": " + source.getName() + " → " + group);
                } catch (Exception moveError) {
                    result.errors++;
                    emit(listener, result, label + " hata: " + source.getName());
                }
            }
        }
    }

    private static File uniqueDestination(File directory, String originalName) {
        File candidate = new File(directory, originalName);
        if (!candidate.exists()) return candidate;

        int dot = originalName.lastIndexOf('.');
        String base = dot > 0 ? originalName.substring(0, dot) : originalName;
        String ext = dot > 0 ? originalName.substring(dot) : "";

        int index = 1;
        do {
            candidate = new File(directory, base + " (" + index + ")" + ext);
            index++;
        } while (candidate.exists());
        return candidate;
    }

    private static String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) return "";
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static void emit(ProgressListener listener, Result result, String message) {
        if (listener != null) listener.onProgress(result.copy(), message);
    }
}
