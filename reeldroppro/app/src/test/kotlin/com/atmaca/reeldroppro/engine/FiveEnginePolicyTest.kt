package com.atmaca.reeldroppro.engine

import com.atmaca.reeldroppro.model.Platform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FiveEnginePolicyTest {
    @Test
    fun `exposes exactly five independent slots`() {
        assertEquals(listOf(1, 2, 3, 4, 5), EngineSlotPolicy.slotIds)
        assertEquals(5, EngineSlotPolicy.maxConcurrentSlots)
        assertTrue(EngineSlotPolicy.isValid(1))
        assertTrue(EngineSlotPolicy.isValid(5))
        assertFalse(EngineSlotPolicy.isValid(0))
        assertFalse(EngineSlotPolicy.isValid(6))
    }

    @Test
    fun `routes Instagram to gallery dl and Facebook to yt dlp`() {
        assertEquals(ExtractorBackend.GALLERY_DL, ExtractorBackendPolicy.backendFor(Platform.INSTAGRAM_PROFILE))
        assertEquals(ExtractorBackend.GALLERY_DL, ExtractorBackendPolicy.backendFor(Platform.INSTAGRAM_HASHTAG))
        assertEquals(ExtractorBackend.YT_DLP, ExtractorBackendPolicy.backendFor(Platform.FACEBOOK))
    }

    @Test
    fun `process identities are isolated per slot`() {
        val registry = SlotProcessRegistry()
        registry.set(1, "p1")
        registry.set(2, "p2")
        assertEquals("p1", registry.get(1))
        assertEquals("p2", registry.get(2))
        registry.clear(1)
        assertEquals(null, registry.get(1))
        assertEquals("p2", registry.get(2))
    }

    @Test
    fun `localized states never expose queue wording`() {
        assertEquals("İndiriliyor", SlotStateText.turkish("DOWNLOADING"))
        assertEquals("Tekrar beklenecek", SlotStateText.turkish("RETRY_WAIT"))
        assertEquals("Tamamlandı", SlotStateText.turkish("COMPLETED"))
        assertFalse(SlotStateText.turkish("QUEUED").contains("kuyruk", ignoreCase = true))
    }
}
