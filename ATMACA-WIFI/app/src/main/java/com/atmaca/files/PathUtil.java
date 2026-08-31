package com.atmaca.files;

public final class PathUtil {
    private PathUtil() {}

    public static String normalize(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "/";
        String p = raw.replace('\\', '/').trim();
        while (p.contains("//")) p = p.replace("//", "/");
        if (!p.startsWith("/")) p = "/" + p;
        if (p.length() > 1 && p.endsWith("/")) p = p.substring(0, p.length() - 1);
        return p;
    }

    public static String parent(String raw) {
        String p = normalize(raw);
        if ("/".equals(p)) return "/";
        int i = p.lastIndexOf('/');
        return i <= 0 ? "/" : p.substring(0, i);
    }

    public static String child(String parent, String name) {
        String p = normalize(parent);
        String n = name == null ? "" : name.trim().replace("/", "");
        if (n.isEmpty()) return p;
        return "/".equals(p) ? "/" + n : p + "/" + n;
    }
}
