package com.atmaca.imagemover;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

public final class FileMoveEngine {
    public boolean move(Path source, Path targetDirectory) {
        if (source == null || targetDirectory == null) {
            return false;
        }

        Path temporary = null;
        try {
            if (!Files.isRegularFile(source)) {
                return false;
            }
            if (Files.exists(targetDirectory) && !Files.isDirectory(targetDirectory)) {
                return false;
            }

            Files.createDirectories(targetDirectory);
            Path destination = targetDirectory.resolve(source.getFileName().toString());

            // En hızlı yol: aynı dosya sistemi içindeyse veri kopyalamadan atomik rename.
            try {
                Files.move(
                        source,
                        destination,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING
                );
                return true;
            } catch (IOException | UnsupportedOperationException atomicMoveUnavailable) {
                // Farklı mount / sağlayıcı / ATOMIC_MOVE desteği yoksa güvenli kopyalama yoluna düş.
            }

            long sourceSize = Files.size(source);
            temporary = targetDirectory.resolve(
                    "." + source.getFileName() + ".atmaca_tmp_" + UUID.randomUUID()
            );

            Files.copy(source, temporary, StandardCopyOption.REPLACE_EXISTING);
            if (Files.size(temporary) != sourceSize) {
                Files.deleteIfExists(temporary);
                return false;
            }

            try {
                Files.move(
                        temporary,
                        destination,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING
                );
            } catch (IOException | UnsupportedOperationException atomicReplaceUnavailable) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
            temporary = null;

            if (!Files.isRegularFile(destination) || Files.size(destination) != sourceSize) {
                return false;
            }

            // Kaynak ancak hedefin tamam olduğu doğrulandıktan sonra silinir.
            Files.delete(source);
            return true;
        } catch (Exception failure) {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (Exception ignored) {
                    // Kaynağı korumak birincil güvenlik şartıdır.
                }
            }
            return false;
        }
    }
}
