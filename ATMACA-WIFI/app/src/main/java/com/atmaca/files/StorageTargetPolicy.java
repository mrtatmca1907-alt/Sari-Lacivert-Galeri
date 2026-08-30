package com.atmaca.files;

public final class StorageTargetPolicy {
    private StorageTargetPolicy() {}

    public static String[] targets() {
        return new String[]{"Kart", "Bulut", "HDD"};
    }

    public static String hddFolder(String currentPath) {
        return PathUtil.normalize(currentPath == null ? "/" : currentPath);
    }
}
