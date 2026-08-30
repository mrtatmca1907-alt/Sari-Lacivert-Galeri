package com.atmaca.files;

public final class MoviesBackupPolicy {
    private MoviesBackupPolicy() {}

    public static boolean isMoviesPath(String relativePath) {
        String p = normalize(relativePath);
        return p.equals("Movies") || p.startsWith("Movies/");
    }

    public static String subdirectory(String relativePath) {
        String p = normalize(relativePath);
        if (!isMoviesPath(p) || p.equals("Movies")) return "";
        String sub = p.substring("Movies/".length());
        return trimSlashes(sub);
    }

    private static String normalize(String value) {
        if (value == null) return "";
        return trimSlashes(value.replace('\\', '/'));
    }

    private static String trimSlashes(String value) {
        String s = value == null ? "" : value.trim();
        while (s.startsWith("/")) s = s.substring(1);
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s;
    }
}
