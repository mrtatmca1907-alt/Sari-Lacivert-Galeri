package com.atmaca.gorselpaketleyici.v2;

import java.util.Locale;

public final class PathPolicy {
    private PathPolicy() {}

    public static boolean isImageName(String name) {
        if (name == null) return false;
        String n = name.toLowerCase(Locale.ROOT);
        return n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") ||
                n.endsWith(".webp") || n.endsWith(".gif") || n.endsWith(".bmp") ||
                n.endsWith(".heic") || n.endsWith(".heif") || n.endsWith(".avif") ||
                n.endsWith(".dng") || n.endsWith(".tif") || n.endsWith(".tiff");
    }

    public static boolean shouldSkipPath(String path, String outputRoot) {
        if (path == null) return true;
        String p = path.replace('\\', '/').toLowerCase(Locale.ROOT);
        String out = outputRoot == null ? "" : outputRoot.replace('\\', '/').toLowerCase(Locale.ROOT);
        return p.contains("/android/data") || p.contains("/android/obb") ||
                p.contains("/.thumbnails") || p.contains("/.cache") ||
                (!out.isEmpty() && p.startsWith(out));
    }
}
