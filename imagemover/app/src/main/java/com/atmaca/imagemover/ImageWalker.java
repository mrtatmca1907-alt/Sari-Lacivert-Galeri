package com.atmaca.imagemover;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

public final class ImageWalker {
    private static final Set<String> IMAGE_EXTENSIONS = new HashSet<>(Arrays.asList(
            "jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif", "avif",
            "dng", "tif", "tiff", "raw", "cr2", "nef", "arw", "rw2", "orf", "raf"
    ));

    public void walk(Path root, Path targetDirectory, Consumer<Path> consumer) {
        if (root == null || targetDirectory == null || consumer == null) {
            return;
        }

        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path normalizedTarget = targetDirectory.toAbsolutePath().normalize();
        Deque<Path> directories = new ArrayDeque<>();
        directories.push(normalizedRoot);

        while (!directories.isEmpty()) {
            Path directory = directories.pop();
            Path normalizedDirectory = directory.toAbsolutePath().normalize();
            if (normalizedDirectory.startsWith(normalizedTarget)) {
                continue;
            }

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
                for (Path child : stream) {
                    Path normalizedChild = child.toAbsolutePath().normalize();
                    if (normalizedChild.startsWith(normalizedTarget)) {
                        continue;
                    }

                    try {
                        if (Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) {
                            directories.push(child);
                        } else if (Files.isRegularFile(child, LinkOption.NOFOLLOW_LINKS) && isImage(child)) {
                            consumer.accept(child);
                        }
                    } catch (SecurityException ignored) {
                        // Android'in erişime kapalı tuttuğu klasör/dosya atlanır, tarama devam eder.
                    }
                }
            } catch (IOException | SecurityException ignored) {
                // Okunamayan bir klasör tüm işlemi durdurmamalı.
            }
        }
    }

    private boolean isImage(Path path) {
        Path fileNamePath = path.getFileName();
        if (fileNamePath == null) {
            return false;
        }
        String fileName = fileNamePath.toString();
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return false;
        }
        String extension = fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        return IMAGE_EXTENSIONS.contains(extension);
    }
}
