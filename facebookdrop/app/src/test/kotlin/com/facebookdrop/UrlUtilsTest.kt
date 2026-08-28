package com.facebookdrop

import org.junit.Assert.*
import org.junit.Test

class UrlUtilsTest {
    @Test fun parsesAndDeduplicatesSupportedUrls(){ assertEquals(listOf("https://facebook.com/a","https://fb.watch/b"), UrlUtils.parseUrls("https://facebook.com/a\nhttps://fb.watch/b https://facebook.com/a")) }
    @Test fun acceptsFacebookHosts(){ assertTrue(UrlUtils.isSupported("https://www.facebook.com/reel/123")); assertTrue(UrlUtils.isSupported("https://fb.watch/abc")) }
    @Test fun rejectsOtherHostsAndSchemes(){ assertFalse(UrlUtils.isSupported("https://example.com/x")); assertFalse(UrlUtils.isSupported("ftp://facebook.com/x")) }
}
