package com.atmaca.photo100

import java.io.File

object HiddenStoreRules {
    private val extensions = setOf("jpg", "jpeg", "png", "webp", "heic", "heif", "gif", "bmp", "dng")
    private val skippedDirs = setOf(".Foto100Kasa", "Android", ".thumbnails", "cache", ".cache")

    fun isPhoto(name: String): Boolean =
        name.substringAfterLast('.', "").lowercase() in extensions

    fun shouldEnter(path: String): Boolean =
        File(path).name !in skippedDirs
}
