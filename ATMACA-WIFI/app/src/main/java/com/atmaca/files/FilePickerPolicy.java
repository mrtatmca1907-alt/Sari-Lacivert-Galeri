package com.atmaca.files;

/** Uses the broad Android content picker for better OEM file-manager compatibility. */
public final class FilePickerPolicy {
    private FilePickerPolicy() {}

    public static String action() {
        return "android.intent.action.GET_CONTENT";
    }

    public static String mimeType() {
        return "*/*";
    }
}
