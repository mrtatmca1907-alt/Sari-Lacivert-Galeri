package com.atmaca.reeldroppro.cookie

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CookieModePolicyTest {
    @Test
    fun `accepts Netscape cookie header and instagram session cookie`() {
        val text = "# Netscape HTTP Cookie File\n.instagram.com\tTRUE\t/\tTRUE\t0\tsessionid\tabc123\n"
        assertTrue(CookieModePolicy.looksUsable(text))
    }

    @Test
    fun `rejects empty or unrelated files`() {
        assertFalse(CookieModePolicy.looksUsable(""))
        assertFalse(CookieModePolicy.looksUsable("hello world"))
    }
}
