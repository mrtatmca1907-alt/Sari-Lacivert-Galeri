package com.atmaca.reeldroppro.engine

import com.atmaca.reeldroppro.model.Platform
import java.util.concurrent.ConcurrentHashMap

enum class ExtractorBackend { GALLERY_DL, YT_DLP }

object ExtractorBackendPolicy {
    fun backendFor(platform: Platform): ExtractorBackend = when (platform) {
        Platform.INSTAGRAM_PROFILE, Platform.INSTAGRAM_HASHTAG -> ExtractorBackend.GALLERY_DL
        Platform.FACEBOOK -> ExtractorBackend.YT_DLP
    }
}

object EngineSlotPolicy {
    val slotIds: List<Int> = (1..5).toList()
    const val maxConcurrentSlots: Int = 5
    fun isValid(slotId: Int): Boolean = slotId in 1..5
}

class SlotProcessRegistry {
    private val ids = ConcurrentHashMap<Int, String>()
    fun set(slotId: Int, processId: String) {
        require(EngineSlotPolicy.isValid(slotId))
        ids[slotId] = processId
    }
    fun get(slotId: Int): String? = ids[slotId]
    fun clear(slotId: Int) { ids.remove(slotId) }
}

object SlotStateText {
    fun turkish(state: String): String = when (state.uppercase()) {
        "QUEUED" -> "Hazır"
        "RESOLVING" -> "İçerik aranıyor"
        "DOWNLOADING" -> "İndiriliyor"
        "POST_PROCESSING" -> "Kaydediliyor"
        "RETRY_WAIT" -> "Tekrar beklenecek"
        "COMPLETED" -> "Tamamlandı"
        "FAILED" -> "Hata"
        "CANCELLED" -> "Durduruldu"
        else -> state
    }
}
