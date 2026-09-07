package com.atmaca.files;

public final class CatalogEntry {
    public final String type;
    public final String name;
    public final String path;
    public final long size;
    public final String date;
    public final String extension;

    public CatalogEntry(String type, String name, String path, long size, String date, String extension) {
        this.type = type == null ? "DOSYA" : type;
        this.name = name == null ? "" : name;
        this.path = PathUtil.normalize(path);
        this.size = Math.max(0L, size);
        this.date = date == null ? "" : date;
        this.extension = extension == null ? "" : extension;
    }

    public boolean isFolder() { return "KLASOR".equalsIgnoreCase(type); }
}
