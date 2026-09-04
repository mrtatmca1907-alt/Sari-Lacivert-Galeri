package com.atmaca.gallery

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CropFullscreenLayoutContractTest {
    @Test
    fun crop_editor_uses_full_remaining_screen_for_photo() {
        val source = File("src/main/kotlin/com/atmaca/gallery/CropEditor.kt").readText()
        assertTrue(source.contains("Modifier.weight(1f)"), "Kırpma fotoğraf alanı ekranın kalan tamamını kullanmalı")
        assertTrue(source.contains("Column(Modifier.fillMaxSize().background(Color.Black))"), "Kırpma ekranı tam ekran düzen kullanmalı")
        assertFalse(source.contains("Modifier.align(Alignment.BottomCenter).fillMaxWidth()"), "Kontroller fotoğrafın üstüne binmemeli")
    }
}
