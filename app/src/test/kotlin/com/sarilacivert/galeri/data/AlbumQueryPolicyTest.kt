package com.sarilacivert.galeri.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlbumQueryPolicyTest {
    @Test
    fun `modern android uses exact relative path instead of loading whole library`() {
        val query = AlbumQueryPolicy.forAlbum("Pictures/Camera/", modern = true)
        assertEquals("relative_path = ?", query.selection)
        assertEquals(listOf("Pictures/Camera/"), query.args)
    }

    @Test
    fun `legacy android narrows query to album directory`() {
        val query = AlbumQueryPolicy.forAlbum("/storage/emulated/0/DCIM/Camera", modern = false)
        assertTrue(query.selection.contains("_data LIKE ?"))
        assertEquals(listOf("/storage/emulated/0/DCIM/Camera/%"), query.args)
    }
}
