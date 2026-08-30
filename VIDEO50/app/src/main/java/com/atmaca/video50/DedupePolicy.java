package com.atmaca.video50;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public final class DedupePolicy {
    private DedupePolicy() {}

    public static List<String> unique(List<String> keys) {
        return new ArrayList<>(new LinkedHashSet<>(keys));
    }

    public static boolean isGeneratedMoviesPath(String path) {
        if (path == null) return false;
        String p = path.replace('\\', '/');
        while (p.endsWith("/")) p = p.substring(0, p.length() - 1);
        return p.matches("(?i).*?Movies/Video \\d+");
    }
}
