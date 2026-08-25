package com.sarilacivert.galeri;

import android.net.Uri;

public class Album {

    private String name;
    private String path;
    private Uri coverUri;

    private int itemCount;
    private int photoCount;
    private int videoCount;

    private boolean coverIsVideo;
    private long lastModified;

    public Album(
            String name,
            String path,
            Uri coverUri,
            int itemCount,
            int photoCount,
            int videoCount,
            boolean coverIsVideo,
            long lastModified
    ) {
        this.name = name;
        this.path = path;
        this.coverUri = coverUri;
        this.itemCount = itemCount;
        this.photoCount = photoCount;
        this.videoCount = videoCount;
        this.coverIsVideo = coverIsVideo;
        this.lastModified = lastModified;
    }

    public String getName() {
        return name;
    }

    public String getPath() {
        return path;
    }

    public Uri getCoverUri() {
        return coverUri;
    }

    public int getItemCount() {
        return itemCount;
    }

    public int getPhotoCount() {
        return photoCount;
    }

    public int getVideoCount() {
        return videoCount;
    }

    public boolean isCoverVideo() {
        return coverIsVideo;
    }

    public long getLastModified() {
        return lastModified;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public void setCoverUri(Uri coverUri) {
        this.coverUri = coverUri;
    }

    public void setItemCount(int itemCount) {
        this.itemCount = itemCount;
    }

    public void setPhotoCount(int photoCount) {
        this.photoCount = photoCount;
    }

    public void setVideoCount(int videoCount) {
        this.videoCount = videoCount;
    }

    public void setCoverVideo(boolean coverIsVideo) {
        this.coverIsVideo = coverIsVideo;
    }

    public void setLastModified(long lastModified) {
        this.lastModified = lastModified;
    }
  }
