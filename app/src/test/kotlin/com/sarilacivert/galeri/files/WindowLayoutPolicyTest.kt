package com.sarilacivert.galeri.files

import org.junit.Assert.assertFalse
import org.junit.Test

class WindowLayoutPolicyTest {
    @Test
    fun `file manager must not force edge to edge layout`() {
        assertFalse(WindowLayoutPolicy.forceEdgeToEdge)
    }
}
