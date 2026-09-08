package com.atmaca.videokareleri;

public final class SourceModeRules {
    private SourceModeRules() {}

    public static boolean needsAllFilesAccess(boolean allMode, int sdkInt) {
        return allMode && sdkInt >= 30;
    }
}
