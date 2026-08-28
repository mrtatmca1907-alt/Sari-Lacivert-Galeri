package com.atmaca.reeldroppro.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadNameTest {
    @Test
    fun `normalizes whitespace and reserved path characters`() {
        assertEquals("a_b_c", DownloadName.normalize(" a/b:c "))
    }
}
