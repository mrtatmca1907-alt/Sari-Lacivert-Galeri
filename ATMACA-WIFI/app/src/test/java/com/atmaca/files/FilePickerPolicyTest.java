package com.atmaca.files;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class FilePickerPolicyTest {
    @Test public void phoneFilePickerUsesGetContentForOemCompatibility() {
        assertEquals("android.intent.action.GET_CONTENT", FilePickerPolicy.action());
    }

    @Test public void phoneFilePickerAcceptsEveryFileType() {
        assertEquals("*/*", FilePickerPolicy.mimeType());
    }
}
