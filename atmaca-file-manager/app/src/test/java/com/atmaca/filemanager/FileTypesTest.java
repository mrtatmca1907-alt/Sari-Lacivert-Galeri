package com.atmaca.filemanager;

import org.junit.Test;
import static org.junit.Assert.*;

public class FileTypesTest {
    @Test public void classifiesModernImages() {
        assertEquals(FileTypes.Category.IMAGE, FileTypes.categoryOf("photo.HEIC"));
        assertEquals(FileTypes.Category.IMAGE, FileTypes.categoryOf("scan.avif"));
        assertEquals(FileTypes.Category.IMAGE, FileTypes.categoryOf("raw.dng"));
    }

    @Test public void classifiesVideoDocumentApkArchiveAndOther() {
        assertEquals(FileTypes.Category.VIDEO, FileTypes.categoryOf("movie.mkv"));
        assertEquals(FileTypes.Category.DOCUMENT, FileTypes.categoryOf("report.pdf"));
        assertEquals(FileTypes.Category.APK, FileTypes.categoryOf("app.apk"));
        assertEquals(FileTypes.Category.ARCHIVE, FileTypes.categoryOf("backup.7z"));
        assertEquals(FileTypes.Category.OTHER, FileTypes.categoryOf("blob.xyz"));
    }

    @Test public void previewableOnlyForImageAndVideo() {
        assertTrue(FileTypes.isPreviewable("a.jpg"));
        assertTrue(FileTypes.isPreviewable("a.mp4"));
        assertFalse(FileTypes.isPreviewable("a.zip"));
    }
}
