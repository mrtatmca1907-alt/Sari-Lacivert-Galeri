package com.atmaca.video50;

import static org.junit.Assert.*;
import org.junit.Test;

public class PermissionPolicyTest {
    @Test public void android13UsesReadMediaVideo() {
        assertArrayEquals(new String[]{"android.permission.READ_MEDIA_VIDEO"}, PermissionPolicy.forSdk(33));
    }

    @Test public void android12UsesLegacyRead() {
        assertArrayEquals(new String[]{"android.permission.READ_EXTERNAL_STORAGE"}, PermissionPolicy.forSdk(31));
    }

    @Test public void android9UsesReadAndWrite() {
        assertArrayEquals(new String[]{"android.permission.READ_EXTERNAL_STORAGE", "android.permission.WRITE_EXTERNAL_STORAGE"}, PermissionPolicy.forSdk(28));
    }
}
