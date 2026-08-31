package com.atmaca.toplayici1907

import java.util.Locale

object CollectorPolicy {
    const val TARGET = "Pictures/1907/"

    fun isTarget(photo: PhotoRecord): Boolean =
        photo.relativePath.trim('/').equals("Pictures/1907", ignoreCase = true)

    fun chooseSurvivor(group: List<PhotoRecord>): PhotoRecord {
        require(group.isNotEmpty()) { "Kopya grubu boş olamaz" }
        return group.sortedWith(
            compareByDescending<PhotoRecord> { isTarget(it) }
                .thenBy { copySuffixScore(it.name) }
                .thenBy { it.dateAdded }
                .thenBy { it.id }
        ).first()
    }

    fun uniqueName(original: String, reserved: MutableSet<String>): String {
        val normalized = original.lowercase(Locale.ROOT)
        if (reserved.add(normalized)) return original

        val dot = original.lastIndexOf('.')
        val base = if (dot > 0) original.substring(0, dot) else original
        val ext = if (dot > 0) original.substring(dot) else ""
        var index = 2
        while (true) {
            val candidate = "${base}_${index}${ext}"
            if (reserved.add(candidate.lowercase(Locale.ROOT))) return candidate
            index++
        }
    }

    fun targetNames(photos: List<PhotoRecord>): Map<Long, String> {
        val targetPhotos = photos.filter(::isTarget).sortedBy { it.id }
        val incomingPhotos = photos.filterNot(::isTarget).sortedBy { it.id }
        val reserved = mutableSetOf<String>()
        val result = linkedMapOf<Long, String>()

        targetPhotos.forEach { photo ->
            reserved += photo.name.lowercase(Locale.ROOT)
            result[photo.id] = photo.name
        }
        incomingPhotos.forEach { photo ->
            result[photo.id] = uniqueName(photo.name, reserved)
        }
        return result
    }

    private fun copySuffixScore(name: String): Int {
        val dot = name.lastIndexOf('.')
        val stem = (if (dot > 0) name.substring(0, dot) else name).trim().lowercase(Locale.ROOT)
        if (Regex(".*\\(\\d+\\)$").matches(stem)) return 1
        val suffixes = listOf(" copy", "_copy", "-copy", " kopya", "_kopya", "-kopya")
        return if (suffixes.any { stem.endsWith(it) }) 1 else 0
    }
}
