package com.atmaca.files;

import java.io.*;
import java.nio.file.*;

/** Direct file operations: no trash, no staging directory, no storage-wide rescan. */
public final class FastFileOps {
    private FastFileOps() {}

    public static void move(File source, File target) throws IOException {
        if (source == null || target == null) throw new IOException("Kaynak/hedef yok");
        File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) throw new IOException("Hedef klasor olusturulamadi");
        try {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return;
        } catch (Exception ignored) {
            try {
                Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                return;
            } catch (Exception ignoredAgain) { }
        }
        copy(source, target);
        if (!source.delete()) {
            target.delete();
            throw new IOException("Kaynak silinemedi; tasima geri alindi");
        }
    }

    public static void copy(File source, File target) throws IOException {
        Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
    }

    public static void deletePermanent(File file) throws IOException {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) deletePermanent(child);
        }
        if (!file.delete()) throw new IOException("Silinemedi: " + file.getAbsolutePath());
    }
}