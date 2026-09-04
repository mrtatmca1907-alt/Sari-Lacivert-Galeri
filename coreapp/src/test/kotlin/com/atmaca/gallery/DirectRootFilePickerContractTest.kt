package com.atmaca.gallery

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectRootFilePickerContractTest {
    private fun source(): String = File("src/main/kotlin/com/atmaca/gallery/DirectFolderPicker.kt").readText()

    @Test
    fun `direct picker lists compatible files as well as folders`() {
        val text = source()
        assertTrue(text.contains("var files by remember"))
        assertTrue(text.contains("toolAcceptsDocument(tool"))
    }

    @Test
    fun `compatible file can be selected directly from root browser`() {
        val text = source()
        assertTrue(text.contains("onSelected(listOf(Uri.fromFile(file)))"))
    }
}
