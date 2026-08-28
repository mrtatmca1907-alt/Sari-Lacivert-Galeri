package com.atmaca.reeldroppro.core

import com.atmaca.reeldroppro.model.Platform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InputParserTest {
    @Test fun parsesInstagramProfileUrlAndUsername() {
        val a = InputParser.parse(Platform.INSTAGRAM_PROFILE, "https://www.instagram.com/acme/ @acme")
        assertEquals(1, a.size)
        assertEquals("acme", a.first().sourceKey)
    }

    @Test fun parsesHashtagWithOrWithoutHashAndDedupes() {
        val a = InputParser.parse(Platform.INSTAGRAM_HASHTAG, "#pelinakil pelinakil")
        assertEquals(1, a.size)
        assertEquals("pelinakil", a.first().sourceKey)
    }

    @Test fun acceptsFacebookUrlsAndRejectsOtherHosts() {
        val a = InputParser.parse(Platform.FACEBOOK, "https://facebook.com/reel/1 https://fb.watch/abc https://example.com/x")
        assertEquals(2, a.size)
        assertTrue(a.all { it.value.contains("facebook.com") || it.value.contains("fb.watch") })
    }
}
