package com.atmaca.zippaketleyici;

final class ZipNames {
    private ZipNames() {}

    static String safeSegment(String value) {
        if (value == null || value.isEmpty()) return "_";
        String s = value.replace('/', '_').replace('\\', '_').replace('\u0000', '_');
        if (".".equals(s) || "..".equals(s)) return "_";
        return s;
    }

    static String join(String parent, String child) {
        String c = safeSegment(child);
        if (parent == null || parent.isEmpty()) return c;
        return parent + "/" + c;
    }

    static String duplicateName(String name, int index) {
        if (index <= 1) return name;
        int dot = name.lastIndexOf('.');
        if (dot <= 0 || dot == name.length() - 1) return name + " (" + index + ")";
        return name.substring(0, dot) + " (" + index + ")" + name.substring(dot);
    }
}
