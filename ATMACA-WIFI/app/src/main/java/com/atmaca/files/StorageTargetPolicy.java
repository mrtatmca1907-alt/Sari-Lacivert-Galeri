package com.atmaca.files;

public final class StorageTargetPolicy {
    private StorageTargetPolicy() {}

    public static String[] targets() {
        return new String[]{"Kart", "Bulut", "HDD"};
    }

    public static boolean requiresFolderSelection(String target) {
        return "HDD".equalsIgnoreCase(target == null ? "" : target.trim());
    }

    public static String hddFolder(String selectedPath) {
        return PathUtil.normalize(selectedPath == null ? "/" : selectedPath);
    }

    public static String hddConfirmLabel() {
        return "Bu klasöre gönder";
    }
}
