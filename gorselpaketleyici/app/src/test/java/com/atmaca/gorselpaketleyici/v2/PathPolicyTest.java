package com.atmaca.gorselpaketleyici.v2;

import org.junit.Test;
import static org.junit.Assert.*;

public class PathPolicyTest {
    @Test public void recognizesModernImageFormats() {
        assertTrue(PathPolicy.isImageName("foto.JPG"));
        assertTrue(PathPolicy.isImageName("kamera.heic"));
        assertTrue(PathPolicy.isImageName("web.avif"));
        assertTrue(PathPolicy.isImageName("raw.dng"));
        assertFalse(PathPolicy.isImageName("video.mp4"));
    }

    @Test public void skipsProtectedAndOutputFolders() {
        assertTrue(PathPolicy.shouldSkipPath("/storage/emulated/0/Android/data/x/a.jpg", "/storage/emulated/0/Pictures/GorselPaketleri"));
        assertTrue(PathPolicy.shouldSkipPath("/storage/emulated/0/Pictures/GorselPaketleri/Paket_1/a.jpg", "/storage/emulated/0/Pictures/GorselPaketleri"));
        assertFalse(PathPolicy.shouldSkipPath("/storage/emulated/0/DCIM/Camera/a.jpg", "/storage/emulated/0/Pictures/GorselPaketleri"));
    }
}
