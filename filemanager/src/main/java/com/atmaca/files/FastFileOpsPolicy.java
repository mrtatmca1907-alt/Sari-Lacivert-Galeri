package com.atmaca.files;

/** Stable behavior contract for the fast file manager. */
public final class FastFileOpsPolicy {
    private FastFileOpsPolicy() {}
    public static String moveStrategy() { return "RENAME_FIRST"; }
    public static boolean permanentDelete() { return true; }
    public static boolean useTrash() { return false; }
    public static String refreshScope() { return "CURRENT_DIRECTORY"; }
}