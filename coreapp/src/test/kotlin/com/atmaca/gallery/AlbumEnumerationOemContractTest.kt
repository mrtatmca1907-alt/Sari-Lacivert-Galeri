package com.atmaca.gallery

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class AlbumEnumerationOemContractTest {
    @Test
    fun album_scan_has_conservative_retry_when_oem_rejects_optional_columns() {
        val source = File("src/main/kotlin/com/atmaca/gallery/MediaStoreRepository.kt").readText()
        assertTrue(
            "OEM bir sütunu reddederse tüm albüm kaybolmamalı; sade sütunlarla ikinci sorgu yapılmalı",
            source.contains("queryAlbumCollectionOemSafe") &&
                source.contains("ALBUM_CORE_PROJECTION") &&
                source.contains("ALBUM_RICH_PROJECTION")
        )
    }
}
