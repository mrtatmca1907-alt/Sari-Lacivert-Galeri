package com.atmaca.files;

public final class FileActionPolicy {
    public enum Action { NAVIGATE, FILE_MENU }

    private FileActionPolicy() {}

    public static Action onTap(boolean isDirectory) {
        return isDirectory ? Action.NAVIGATE : Action.FILE_MENU;
    }

    public static String[] fileMenu() {
        return new String[]{"Aç", "İndir", "Yeniden adlandır", "Taşı", "Sil"};
    }
}
