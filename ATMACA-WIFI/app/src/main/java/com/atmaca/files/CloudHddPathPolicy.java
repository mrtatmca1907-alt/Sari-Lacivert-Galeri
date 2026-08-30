package com.atmaca.files;

public final class CloudHddPathPolicy {
    private static final String ROOT = "/MOVIES";

    private CloudHddPathPolicy() {}

    public static String remoteDir(String relativeDir) {
        String raw = relativeDir == null ? "" : relativeDir.trim().replace('\\', '/');
        while (raw.startsWith("/")) raw = raw.substring(1);
        while (raw.endsWith("/")) raw = raw.substring(0, raw.length() - 1);
        if (raw.isEmpty()) return ROOT;
        return PathUtil.normalize(ROOT + "/" + raw);
    }
}
