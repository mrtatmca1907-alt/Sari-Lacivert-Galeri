package com.atmaca.files;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class BackupProgressTest {
    @Test public void formatsProgressWithCurrentFile() {
        assertEquals("12 / 347 dosya • IMG_001.jpg", BackupProgress.text(12, 347, "IMG_001.jpg"));
    }

    @Test public void formatsCompletedSummary() {
        assertEquals("Bulut yedekleme tamamlandı: 347 / 347 dosya", BackupProgress.completed(347, 347));
    }
}
