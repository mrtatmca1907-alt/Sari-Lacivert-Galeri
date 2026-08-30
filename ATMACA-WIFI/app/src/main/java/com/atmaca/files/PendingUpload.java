package com.atmaca.files;

public final class PendingUpload {
    public final long id;
    public final String localPath;
    public final String remoteDir;
    public final String name;
    public final String mime;
    public final long size;
    public final long createdAt;

    public PendingUpload(long id, String localPath, String remoteDir, String name, String mime, long size, long createdAt) {
        this.id = id;
        this.localPath = localPath == null ? "" : localPath;
        this.remoteDir = PathUtil.normalize(remoteDir);
        this.name = name == null ? "dosya" : name;
        this.mime = mime == null || mime.trim().isEmpty() ? "application/octet-stream" : mime;
        this.size = Math.max(0L, size);
        this.createdAt = createdAt;
    }

    public String remotePath() {
        return PathUtil.child(remoteDir, name);
    }
}
