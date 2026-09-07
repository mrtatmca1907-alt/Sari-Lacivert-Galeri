package com.atmaca.files;

import static org.junit.Assert.*;
import org.junit.Test;

public class FileActionPolicyTest {
    @Test public void folderTapNavigatesButFileTapOpensActions() {
        assertEquals(FileActionPolicy.Action.NAVIGATE, FileActionPolicy.onTap(true));
        assertEquals(FileActionPolicy.Action.FILE_MENU, FileActionPolicy.onTap(false));
    }
    @Test public void fileMenuContainsOpenDownloadRenameMoveDelete() {
        String[] a = FileActionPolicy.fileMenu();
        assertArrayEquals(new String[]{"Aç", "İndir", "Yeniden adlandır", "Taşı", "Sil"}, a);
    }
}
