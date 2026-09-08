package com.atmaca.videokareleri;

import org.junit.Test;
import static org.junit.Assert.*;

public class SourceModeRulesTest {
    @Test public void allModeNeedsAllFilesAccessOnAndroid11Plus() {
        assertTrue(SourceModeRules.needsAllFilesAccess(true, 30));
        assertTrue(SourceModeRules.needsAllFilesAccess(true, 33));
    }

    @Test public void folderModeNeverNeedsAllFilesAccess() {
        assertFalse(SourceModeRules.needsAllFilesAccess(false, 33));
    }

    @Test public void preAndroid11AllModeDoesNotNeedSpecialAccess() {
        assertFalse(SourceModeRules.needsAllFilesAccess(true, 29));
    }
}
