package com.atmaca.gallery;

import org.junit.Test;

import static org.junit.Assert.*;

public class UltimateV2BehaviorTest {
    @Test public void appNameIsGaleri() {
        assertEquals("Galeri", UltimateV2Rules.APP_NAME);
    }

    @Test public void duplicateFeatureIsDisabled() {
        assertFalse(UltimateV2Rules.SHOW_DUPLICATES_FEATURE);
    }

    @Test public void trashLivesInSettings() {
        assertTrue(UltimateV2Rules.TRASH_IN_SETTINGS);
    }

    @Test public void clampTranslationKeepsContentInsideViewport() {
        float[] p = UltimateV2Rules.clampTranslation(900f, -900f, 1080f, 1920f, 2f);
        assertTrue(Math.abs(p[0]) <= 540f);
        assertTrue(Math.abs(p[1]) <= 960f);
    }
}
