package com.atmaca.zippaketleyici;

import org.junit.Test;
import static org.junit.Assert.*;

public class ZipNamesTest {
    @Test public void unsafeSeparatorsAreRemoved() {
        assertEquals("a_b_c", ZipNames.safeSegment("a/b\\c"));
    }

    @Test public void dotSegmentsCannotEscapeZip() {
        assertEquals("_", ZipNames.safeSegment(".."));
        assertEquals("_", ZipNames.safeSegment("."));
    }

    @Test public void nestedPathIsRelative() {
        assertEquals("klasor/resim.jpg", ZipNames.join("klasor", "resim.jpg"));
    }

    @Test public void duplicateKeepsExtension() {
        assertEquals("foto (2).jpg", ZipNames.duplicateName("foto.jpg", 2));
    }
}
