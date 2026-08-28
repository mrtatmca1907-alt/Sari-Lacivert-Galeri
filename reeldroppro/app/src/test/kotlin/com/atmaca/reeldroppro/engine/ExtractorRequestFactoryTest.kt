package com.atmaca.reeldroppro.engine

import com.atmaca.reeldroppro.model.ParsedInput
import com.atmaca.reeldroppro.model.Platform
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtractorRequestFactoryTest {
    @Test fun instagramProfileUsesArchiveFriendlyBulkOptions() {
        val spec = ExtractorRequestFactory.optionsFor(ParsedInput(Platform.INSTAGRAM_PROFILE, "https://www.instagram.com/acme/", "acme"), "/tmp/out")
        assertTrue(spec.contains("--continue"))
        assertTrue(spec.contains("--ignore-errors"))
        assertTrue(spec.contains("--no-overwrites"))
    }

    @Test fun facebookUsesBestVideoAudioAndMp4Merge() {
        val spec = ExtractorRequestFactory.optionsFor(ParsedInput(Platform.FACEBOOK, "https://facebook.com/reel/1", "reel-1"), "/tmp/out")
        assertTrue(spec.contains("bestvideo*+bestaudio/best"))
        assertTrue(spec.contains("mp4"))
    }
}
