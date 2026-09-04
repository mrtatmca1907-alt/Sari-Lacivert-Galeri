package com.atmaca.gallery

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoLandscapeContractTest {
    @Test
    fun video_enters_landscape_and_restores_orientation_after_exit() {
        val source = File("src/main/kotlin/com/atmaca/gallery/GalleryApp.kt").readText()
        val video = source
            .substringAfter("private fun VideoPage")
            .substringBefore("private fun RenameDialog")

        assertTrue(
            "Video açılınca yatay tam ekran yönlendirmesi yapılmalı",
            video.contains("SCREEN_ORIENTATION_SENSOR_LANDSCAPE") ||
                video.contains("SCREEN_ORIENTATION_LANDSCAPE")
        )
        assertTrue(
            "Videodan çıkınca önceki ekran yönü geri yüklenmeli",
            video.contains("oldOrientation") && video.contains("requestedOrientation = oldOrientation")
        )
    }
}
