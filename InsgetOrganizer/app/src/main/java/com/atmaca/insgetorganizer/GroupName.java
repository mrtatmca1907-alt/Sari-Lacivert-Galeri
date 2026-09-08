package com.atmaca.insgetorganizer;

public final class GroupName {
    private GroupName() {}

    public static String fromFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) return "Bilinmeyen";

        int dot = fileName.lastIndexOf('.');
        String stem = (dot > 0) ? fileName.substring(0, dot) : fileName;

        String cleaned = stem.replaceFirst("(?:[_-]\\d+)+$", "").trim();
        if (cleaned.isEmpty()) cleaned = stem.trim();
        if (cleaned.isEmpty()) cleaned = "Bilinmeyen";

        return sanitize(cleaned);
    }

    private static String sanitize(String name) {
        String sanitized = name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        while (sanitized.endsWith(".")) {
            sanitized = sanitized.substring(0, sanitized.length() - 1).trim();
        }
        return sanitized.isEmpty() ? "Bilinmeyen" : sanitized;
    }
}
