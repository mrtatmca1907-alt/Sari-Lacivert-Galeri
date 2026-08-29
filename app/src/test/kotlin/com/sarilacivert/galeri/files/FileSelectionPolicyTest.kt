package com.sarilacivert.galeri.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileSelectionPolicyTest {
    @Test
    fun `toggle adds and removes paths`() {
        val first = "/storage/emulated/0/DCIM/a.jpg"
        val second = "/storage/emulated/0/DCIM/b.jpg"

        var selected = FileSelectionPolicy.toggle(emptySet(), first)
        assertTrue(first in selected)

        selected = FileSelectionPolicy.toggle(selected, second)
        assertEquals(setOf(first, second), selected)

        selected = FileSelectionPolicy.toggle(selected, first)
        assertEquals(setOf(second), selected)
    }

    @Test
    fun `select all replaces selection with visible paths`() {
        val visible = listOf("/a", "/b", "/c")
        val selected = FileSelectionPolicy.selectAll(visible)

        assertEquals(visible.toSet(), selected)
        assertFalse(selected.isEmpty())
    }

    @Test
    fun `clear removes every selected path`() {
        assertTrue(FileSelectionPolicy.clear(setOf("/a", "/b")).isEmpty())
    }
}
