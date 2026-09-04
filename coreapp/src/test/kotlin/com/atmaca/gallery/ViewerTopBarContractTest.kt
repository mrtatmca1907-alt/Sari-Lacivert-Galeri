package com.atmaca.gallery

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewerTopBarContractTest {
    @Test
    fun viewer_top_bar_keeps_only_filename_and_overflow() {
        val source = File("src/main/kotlin/com/atmaca/gallery/GalleryApp.kt").readText()
        val top = source
            .substringAfter(".align(Alignment.TopCenter)")
            .substringBefore("DropdownMenu(")

        assertTrue("Üst çubukta dosya adı kalmalı", top.contains("current.name"))
        assertTrue("Üst çubukta üç nokta menüsü kalmalı", top.contains("Icons.Default.MoreVert"))
        assertFalse("Döndürme üst çubukta olmamalı", top.contains("Icons.Default.RotateRight"))
        assertFalse("Kırpma üst çubukta olmamalı", top.contains("Icons.Default.Crop"))

        val menu = source
            .substringAfter("DropdownMenu(")
            .substringBefore("Row(\n                    Modifier\n                        .align(Alignment.BottomCenter)")
        assertTrue("Döndürme üç nokta menüsüne taşınmalı", menu.contains("Text(\"Döndür\")"))
    }
}
