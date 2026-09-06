package com.sarilacivert.galeri.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolInputPolicyTest {
    @Test
    fun folderSelectionNeverStartsScanning() {
        val state = ToolInputPolicy.folderSelected("content://tree/root")

        assertEquals("content://tree/root", state.treeUri)
        assertTrue(state.directUris.isEmpty())
        assertFalse(state.scanRequested)
    }

    @Test
    fun directFilesAreReadyWithoutFolderScan() {
        val state = ToolInputPolicy.filesSelected(
            listOf("content://media/1", "content://media/2")
        )

        assertEquals(2, state.directUris.size)
        assertEquals(null, state.treeUri)
        assertFalse(state.scanRequested)
    }

    @Test
    fun processingStartsOnlyAfterExplicitStart() {
        val selected = ToolInputPolicy.folderSelected("content://tree/DCIM")
        val started = ToolInputPolicy.start(selected)

        assertFalse(selected.scanRequested)
        assertTrue(started.scanRequested)
    }
}
