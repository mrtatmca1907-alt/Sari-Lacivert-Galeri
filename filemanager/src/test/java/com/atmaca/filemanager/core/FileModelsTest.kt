package com.atmaca.filemanager.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class FileModelsTest {
    @Test
    fun refreshScope_containsOnlyAffectedDirectories() {
        val scope = RefreshScope(
            directories = setOf("/storage/emulated/0/Pictures", "/storage/emulated/0/DCIM")
        )

        assertEquals(2, scope.directories.size)
        assertFalse(scope.directories.contains("/storage/emulated/0"))
    }
}
