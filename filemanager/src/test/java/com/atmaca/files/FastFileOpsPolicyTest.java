package com.atmaca.files;

import static org.junit.Assert.*;
import org.junit.Test;

public class FastFileOpsPolicyTest {
    @Test public void movePrefersRenameBeforeCopyDelete() {
        assertEquals("RENAME_FIRST", FastFileOpsPolicy.moveStrategy());
    }

    @Test public void deleteIsPermanentAndDoesNotUseTrash() {
        assertTrue(FastFileOpsPolicy.permanentDelete());
        assertFalse(FastFileOpsPolicy.useTrash());
    }

    @Test public void refreshIsCurrentDirectoryOnly() {
        assertEquals("CURRENT_DIRECTORY", FastFileOpsPolicy.refreshScope());
    }
}