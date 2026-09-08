package com.atmaca.videokareleri;

import java.util.Locale;

public final class FrameRules {
    private FrameRules() {}

    public static int frameCount(long durationMs) {
        if (durationMs <= 0) return 0;
        long count = (durationMs + 999L) / 1000L;
        return count > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) count;
    }

    public static String baseName(String name) {
        if (name == null || name.isEmpty()) return "video";
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    public static String frameName(String base, int oneBasedIndex) {
        return String.format(Locale.ROOT, "%s_%06d.jpg", base, oneBasedIndex);
    }

    public static boolean isVideo(String mime, String name) {
        if (mime != null && mime.toLowerCase(Locale.ROOT).startsWith("video/")) return true;
        if (name == null) return false;
        String n = name.toLowerCase(Locale.ROOT);
        return n.endsWith(".mp4") || n.endsWith(".mkv") || n.endsWith(".mov") || n.endsWith(".avi") || n.endsWith(".webm") || n.endsWith(".3gp") || n.endsWith(".m4v");
    }
}
