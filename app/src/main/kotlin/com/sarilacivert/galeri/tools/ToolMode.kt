package com.sarilacivert.galeri.tools

enum class ToolMode {
    PERSON_CROP,
    VIDEO_FRAMES,
    PACKAGER
}

internal object ToolModePolicy {
    private val imageExtensions = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif", "avif", "tif", "tiff")
    private val videoExtensions = setOf("mp4", "mkv", "webm", "mov", "avi", "m4v", "3gp", "ts", "mpeg", "mpg")

    fun accepts(mode: ToolMode, name: String, mimeType: String?): Boolean {
        val mime = mimeType.orEmpty().lowercase()
        val ext = name.substringAfterLast('.', "").lowercase()
        val image = mime.startsWith("image/") || ext in imageExtensions
        val video = mime.startsWith("video/") || ext in videoExtensions
        return when (mode) {
            ToolMode.PERSON_CROP -> image
            ToolMode.VIDEO_FRAMES -> video
            ToolMode.PACKAGER -> image || video
        }
    }
}
