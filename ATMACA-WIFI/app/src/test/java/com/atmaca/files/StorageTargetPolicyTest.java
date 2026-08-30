package com.atmaca.files;

import static org.junit.Assert.*;
import org.junit.Test;

public class StorageTargetPolicyTest {
    @Test public void targetMenuIsCardCloudHdd() {
        assertArrayEquals(new String[]{"Kart", "Bulut", "HDD"}, StorageTargetPolicy.targets());
    }

    @Test public void hddUsesExplicitlyConfirmedFolder() {
        assertTrue(StorageTargetPolicy.requiresFolderSelection("HDD"));
        assertFalse(StorageTargetPolicy.requiresFolderSelection("Kart"));
        assertEquals("/Videolar/2026", StorageTargetPolicy.hddFolder("/Videolar/2026"));
        assertEquals("Bu klasöre gönder", StorageTargetPolicy.hddConfirmLabel());
    }
}
