package com.atmaca.files;

import static org.junit.Assert.*;
import org.junit.Test;

public class StorageTargetPolicyTest {
    @Test public void targetMenuIsCardCloudHdd() {
        assertArrayEquals(new String[]{"Kart", "Bulut", "HDD"}, StorageTargetPolicy.targets());
    }

    @Test public void hddUsesCurrentFolder() {
        assertEquals("/Videolar", StorageTargetPolicy.hddFolder("/Videolar"));
        assertEquals("/", StorageTargetPolicy.hddFolder(null));
    }
}
