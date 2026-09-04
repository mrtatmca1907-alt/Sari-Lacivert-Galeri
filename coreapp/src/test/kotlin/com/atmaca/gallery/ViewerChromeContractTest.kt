package com.atmaca.gallery

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewerChromeContractTest {
    @Test
    fun viewer_keeps_info_crop_trash_and_back_on_bottom_and_moves_share_to_menu() {
        val source = File("src/main/kotlin/com/atmaca/gallery/GalleryApp.kt").readText()
        val bottom = source
            .substringAfter("horizontalArrangement = Arrangement.SpaceEvenly")
            .substringBefore("if (showInfo)")

        assertFalse("Favori kalbi alt çubukta olmamalı", bottom.contains("toggleFavorite(current)"))
        assertFalse("Paylaş alt çubukta olmamalı", bottom.contains("onShare(current)"))
        assertFalse("Slayt alt çubukta olmamalı", bottom.contains("slideshowRunning = !slideshowRunning"))
        assertTrue("Bilgi alt çubukta olmalı", bottom.contains("showInfo = !showInfo"))
        assertTrue("Kırp alt çubukta olmalı", bottom.contains("onCrop(current)"))
        assertTrue("Çöp alt çubukta olmalı", bottom.contains("onTrash(current)"))
        assertTrue("Geri düğmesi alt çubuğun sağ tarafında/sonunda olmalı", bottom.lastIndexOf("onBack") > bottom.lastIndexOf("onTrash(current)"))
        assertTrue("Paylaş üç nokta menüsüne taşınmalı", source.contains("text = { Text(\"Paylaş\") }"))
    }
}
