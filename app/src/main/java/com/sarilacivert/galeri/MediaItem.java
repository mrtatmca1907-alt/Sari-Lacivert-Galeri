package com.sarilacivert.galeri;

import android.net.Uri;

public class MediaItem {

    private final long id;
    private final Uri uri;
    private final String name;
    private final String path;
    private final String mimeType;
    private final long dateModified;
    private final long size;
    private final boolean video;
    private final long duration;

    public MediaItem(
            long id,
            Uri uri,
            String name,
            String path,
            String mimeType,
            long dateModified,
            long size,
            boolean video,
            long duration
    ) {
        this.id = id;
        this.uri = uri;
        this.name = name;
        this.path = path;
        this.mimeType = mimeType;
        this.dateModified = dateModified;
        this.size = size;
        this.video = video;
        this.duration = duration;
    }

    public long getId() {
        return id;
    }

    public Uri getUri() {
        return uri;
    }

    public String getName() {
        return name;
    }

    public String getPath() {
        return path;
    }

    public String getMimeType() {
        return mimeType;
    }

    public long getDateModified() {
        return dateModified;
    }

    public long getSize() {
        return size;
    }

    public boolean isVideo() {
        return video;
    }

    public long getDuration() {
        return duration;
    }
}
