package com.atmaca.video50;

import android.net.Uri;

public final class VideoItem {
    public final Uri uri;
    public final String name;
    public final long size;
    public final String filePath;

    public VideoItem(Uri uri, String name, long size, String filePath) {
        this.uri = uri;
        this.name = name == null || name.trim().isEmpty() ? "video" : name;
        this.size = size;
        this.filePath = filePath;
    }

    public String key() {
        return filePath != null && !filePath.isEmpty() ? filePath : uri.toString();
    }
}
