package com.atmaca.gallery

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class CropRefreshContractTest {
    @Test
    fun `crop overwrite notifies media observers after metadata update`() {
        val source = File("src/main/kotlin/com/atmaca/gallery/GalleryActions.kt").readText()
        val cropBlock = source.substringAfter("suspend fun overwriteCropped").substringBefore("suspend fun saveScreenshot")
        assertTrue(cropBlock.contains("resolver.notifyChange(source.uri, null)"))
    }

    @Test
    fun `crop overwrite publishes new dimensions`() {
        val source = File("src/main/kotlin/com/atmaca/gallery/GalleryActions.kt").readText()
        val cropBlock = source.substringAfter("suspend fun overwriteCropped").substringBefore("suspend fun saveScreenshot")
        assertTrue(cropBlock.contains("MediaStore.MediaColumns.WIDTH"))
        assertTrue(cropBlock.contains("MediaStore.MediaColumns.HEIGHT"))
    }
}
