package com.atmaca.files;

public final class NavigationPolicy {
    private NavigationPolicy() {}

    public static String backTarget(String currentPath) {
        String normalized = PathUtil.normalize(currentPath);
        if ("/".equals(normalized)) return null;
        return PathUtil.parent(normalized);
    }
}
