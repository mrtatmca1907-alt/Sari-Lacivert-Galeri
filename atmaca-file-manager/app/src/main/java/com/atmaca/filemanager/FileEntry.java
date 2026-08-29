package com.atmaca.filemanager;

import java.io.File;
import java.util.Objects;

public final class FileEntry {
    public final String path;
    public final String name;
    public final boolean directory;
    public final long size;
    public final long modified;

    public FileEntry(String path, String name, boolean directory, long size, long modified) {
        this.path = path;
        this.name = name;
        this.directory = directory;
        this.size = size;
        this.modified = modified;
    }

    public File toFile() { return new File(path); }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FileEntry)) return false;
        FileEntry other = (FileEntry) o;
        return directory == other.directory && size == other.size && modified == other.modified
                && Objects.equals(path, other.path) && Objects.equals(name, other.name);
    }

    @Override public int hashCode() { return Objects.hash(path, name, directory, size, modified); }
}
