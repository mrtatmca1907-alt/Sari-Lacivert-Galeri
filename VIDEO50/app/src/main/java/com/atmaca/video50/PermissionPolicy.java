package com.atmaca.video50;

public final class PermissionPolicy {
    private PermissionPolicy() {}

    public static String[] forSdk(int sdk) {
        if (sdk >= 33) return new String[]{"android.permission.READ_MEDIA_VIDEO"};
        if (sdk >= 29) return new String[]{"android.permission.READ_EXTERNAL_STORAGE"};
        return new String[]{"android.permission.READ_EXTERNAL_STORAGE", "android.permission.WRITE_EXTERNAL_STORAGE"};
    }
}
