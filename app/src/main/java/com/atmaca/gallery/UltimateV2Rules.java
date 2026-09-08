package com.atmaca.gallery;

public final class UltimateV2Rules {
    public static final String APP_NAME = "Galeri";
    public static final boolean SHOW_DUPLICATES_FEATURE = false;
    public static final boolean TRASH_IN_SETTINGS = true;

    private UltimateV2Rules() {}

    public static float[] clampTranslation(float tx, float ty, float viewWidth, float viewHeight, float scale) {
        float maxX = Math.max(0f, (viewWidth * scale - viewWidth) / 2f);
        float maxY = Math.max(0f, (viewHeight * scale - viewHeight) / 2f);
        tx = Math.max(-maxX, Math.min(maxX, tx));
        ty = Math.max(-maxY, Math.min(maxY, ty));
        return new float[]{tx, ty};
    }
}
