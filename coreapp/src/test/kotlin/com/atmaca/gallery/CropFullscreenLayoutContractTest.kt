package com.atmaca.gallery

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CropFullscreenLayoutContractTest {
    @Test
    fun crop_editor_uses_full_remaining_screen_for_photo() {
        val source = File("src/main/kotlin/com/atmaca/gallery/CropEditor.kt").readText()
        assertTrue("Kırpma fotoğraf alanı ekranın kalan tamamını kullanmalı", source.contains("Modifier.weight(1f)"))
        assertTrue("Kırpma ekranı tam ekran düzen kullanmalı", source.contains("Column(Modifier.fillMaxSize().background(Color.Black))"))
        assertFalse("Kontroller fotoğrafın üstüne binmemeli", source.contains("Modifier.align(Alignment.BottomCenter).fillMaxWidth()"))
    }
}
