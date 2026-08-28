package com.atmaca.reeldroppro.engine

import com.atmaca.reeldroppro.model.ParsedInput
import com.atmaca.reeldroppro.model.Platform
import com.yausername.youtubedl_android.YoutubeDLRequest

object ExtractorRequestFactory {
    fun optionsFor(input: ParsedInput, outputTemplate: String): List<String> {
        val options = mutableListOf(
            "--continue",
            "--ignore-errors",
            "--no-overwrites",
            "--no-mtime",
            "--restrict-filenames",
            "--newline",
            "-o", outputTemplate
        )
        if (input.platform == Platform.FACEBOOK) {
            options += listOf("--downloader", "libaria2c.so", "-f", "bestvideo*+bestaudio/best", "--merge-output-format", "mp4")
        }
        return options
    }

    fun build(input: ParsedInput, outputTemplate: String): YoutubeDLRequest {
        val request = YoutubeDLRequest(input.value)
        val options = optionsFor(input, outputTemplate)
        var index = 0
        while (index < options.size) {
            val key = options[index]
            val next = options.getOrNull(index + 1)
            if (key in setOf("-o", "--downloader", "-f", "--merge-output-format") && next != null) {
                request.addOption(key, next)
                index += 2
            } else {
                request.addOption(key)
                index++
            }
        }
        return request
    }
}
