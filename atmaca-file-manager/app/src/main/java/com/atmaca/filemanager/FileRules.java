package com.atmaca.filemanager;

import java.io.File;
import java.io.IOException;
import java.text.Collator;
import java.util.Comparator;
import java.util.Locale;

public final class FileRules {
    private FileRules() {}

    private static final Collator COLLATOR;
    static {
        COLLATOR = Collator.getInstance(new Locale("tr", "TR"));
        COLLATOR.setStrength(Collator.PRIMARY);
    }

    public static final Comparator<FileEntry> ENTRY_COMPARATOR = (a, b) -> {
        if (a.directory != b.directory) return a.directory ? -1 : 1;
        synchronized (COLLATOR) {
            int c = COLLATOR.compare(a.name, b.name);
            if (c != 0) return c;
        }
        return a.path.compareToIgnoreCase(b.path);
    };

    public static File uniqueTarget(File dir, String name) {
        File first = new File(dir, name);
        if (!first.exists()) return first;
        String base = name;
        String ext = "";
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            base = name.substring(0, dot);
            ext = name.substring(dot);
        }
        for (int n = 2; n < Integer.MAX_VALUE; n++) {
            File candidate = new File(dir, base + " (" + n + ")" + ext);
            if (!candidate.exists()) return candidate;
        }
        throw new IllegalStateException("Unique filename could not be generated");
    }

    public static boolean isSafeChild(File root, File child) {
        try {
            String rp = root.getCanonicalPath();
            String cp = child.getCanonicalPath();
            return cp.equals(rp) || cp.startsWith(rp + File.separator);
        } catch (IOException | SecurityException e) {
            return false;
        }
    }
}
