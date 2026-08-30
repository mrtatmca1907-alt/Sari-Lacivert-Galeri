package com.atmaca.video50;

public final class BatchPolicy {
    private BatchPolicy() {}

    public static String folderNameForIndex(int zeroBasedIndex) {
        if (zeroBasedIndex < 0) throw new IllegalArgumentException("index");
        return "Video " + ((zeroBasedIndex / 50) + 1);
    }

    public static String relativeMoviesPathForIndex(int zeroBasedIndex) {
        return "Movies/" + folderNameForIndex(zeroBasedIndex);
    }
}
