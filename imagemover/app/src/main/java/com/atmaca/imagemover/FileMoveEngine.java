package com.atmaca.imagemover;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

public final class FileMoveEngine {
    public boolean move(Path source, Path targetDirectory) {
        if (source == null || targetDirectory == null) return false;
        Path temporary = null;
        try {
            if (!Files.isRegularFile(source)) return false;
            Files.createDirectories(targetDirectory);
            if (!Files.isDirectory(targetDirectory)) return false;
            if (source.getParent().equals(targetDirectory)) return true;

            String filename = source.getFileName().toString();
            int dot = filename.lastIndexOf('.');
            String stem = dot > 0 ? filename.substring(0, dot) : filename;
            String ext = dot > 0 ? filename.substring(dot) : "";
            Path destination = targetDirectory.resolve(filename);
            int suffix = 1;
            while (Files.exists(destination)) {
                destination = targetDirectory.resolve(stem + "+" + suffix++ + ext);
            }

            // Fast path: metadata-only move on the same filesystem.
            try {
                Files.move(source, destination);
                return true;
            } catch (IOException unsupportedMove) {
                // Cross-device or provider restrictions: verified copy fallback.
            }

            long size = Files.size(source);
            temporary = targetDirectory.resolve("." + UUID.randomUUID() + ".atmaca_tmp");
            Files.copy(source, temporary);
            if (Files.size(temporary) != size) return false;

            // Never replace an existing image, even if a name collision occurs.
            while (Files.exists(destination)) {
                destination = targetDirectory.resolve(stem + "+" + suffix++ + ext);
            }
            Files.move(temporary, destination);
            temporary = null;
            if (Files.size(destination) != size) return false;
            Files.delete(source);
            return true;
        } catch (Exception ignored) {
            return false;
        } finally {
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
            }
        }
    }
}
