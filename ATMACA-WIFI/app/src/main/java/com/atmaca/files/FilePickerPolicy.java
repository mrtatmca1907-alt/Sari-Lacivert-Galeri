package com.atmaca.files;

public final class FilePickerPolicy {
    private FilePickerPolicy() {}

    public static String action() {
        return "android.intent.action.GET_CONTENT";
    }

    public static String mimeType() {
        return "*/*";
    }
}
