package com.atmaca.insgetorganizer;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class GroupNameTest {
    @Test
    public void stripsSingleNumericSuffix() {
        assertEquals("aysetolga", GroupName.fromFileName("aysetolga_12345.jpg"));
    }

    @Test
    public void preservesUnderscoresInAccountName() {
        assertEquals("ayse_tolga", GroupName.fromFileName("ayse_tolga_12345.jpg"));
    }

    @Test
    public void stripsMultipleTrailingNumericSegments() {
        assertEquals("ayse_tolga", GroupName.fromFileName("ayse_tolga_12345_2.jpg"));
    }

    @Test
    public void leavesNameWithoutNumericSuffixAlone() {
        assertEquals("ayse_tolga", GroupName.fromFileName("ayse_tolga.mp4"));
    }

    @Test
    public void sanitizesIllegalFolderCharacters() {
        assertEquals("ayse_tolga", GroupName.fromFileName("ayse:tolga_99.jpg"));
    }
}
