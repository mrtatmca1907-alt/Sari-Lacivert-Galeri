package com.atmaca.filemanager;

import java.util.Locale;
import java.util.Set;

public final class FileTypes {
    public enum Category { IMAGE, VIDEO, DOCUMENT, APK, ARCHIVE, OTHER }

    private static final Set<String> IMAGES = Set.of("jpg","jpeg","png","webp","gif","bmp","heic","heif","avif","dng","tif","tiff");
    private static final Set<String> VIDEOS = Set.of("mp4","mkv","avi","mov","webm","3gp","m4v","ts","mpeg","mpg");
    private static final Set<String> DOCUMENTS = Set.of("pdf","txt","doc","docx","xls","xlsx","ppt","pptx","csv","rtf","odt","ods","epub");
    private static final Set<String> ARCHIVES = Set.of("zip","rar","7z","tar","gz","bz2","xz","tgz");

    private FileTypes() {}

    public static Category categoryOf(String name) {
        String ext = extension(name);
        if (IMAGES.contains(ext)) return Category.IMAGE;
        if (VIDEOS.contains(ext)) return Category.VIDEO;
        if (DOCUMENTS.contains(ext)) return Category.DOCUMENT;
        if ("apk".equals(ext)) return Category.APK;
        if (ARCHIVES.contains(ext)) return Category.ARCHIVE;
        return Category.OTHER;
    }

    public static boolean isPreviewable(String name) {
        Category c = categoryOf(name);
        return c == Category.IMAGE || c == Category.VIDEO;
    }

    public static String extension(String name) {
        if (name == null) return "";
        int i = name.lastIndexOf('.');
        if (i < 0 || i == name.length() - 1) return "";
        return name.substring(i + 1).toLowerCase(Locale.ROOT);
    }
}
