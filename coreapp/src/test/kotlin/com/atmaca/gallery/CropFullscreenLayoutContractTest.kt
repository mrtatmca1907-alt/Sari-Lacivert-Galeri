package com.atmaca.gallery

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CropFullscreenLayoutContractTest {
    @Test
    fun crop_editor_uses_large_photo_area_without_overlay_controls() {
        val source = File("src/main/kotlin/com/atmaca/gallery/CropEditor.kt").readText()
        assertTrue("Kırpma ekranı tam ekran düzen kullanmalı", source.contains("Column(Modifier.fillMaxSize().background(Color.Black))"))
        assertTrue("Fotoğraf alanı ekranın büyük bölümünü kullanmalı", source.contains("Box(Modifier.fillMaxWidth().fillMaxHeight(0.82f))"))
        assertFalse("Kontroller fotoğrafın üstüne binmemeli", source.contains("Modifier.align(Alignment.BottomCenter).fillMaxWidth()"))
    }
}
