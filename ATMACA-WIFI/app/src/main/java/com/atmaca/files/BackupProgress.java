package com.atmaca.files;

public final class BackupProgress {
    private BackupProgress() {}

    public static String text(int done, int total, String currentName) {
        String name = currentName == null || currentName.trim().isEmpty() ? "dosya" : currentName;
        return done + " / " + total + " dosya • " + name;
    }

    public static String completed(int done, int total) {
        return "Bulut yedekleme tamamlandı: " + done + " / " + total + " dosya";
    }
}
