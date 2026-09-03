package com.atmaca.gallery

fun quickAlbums(items: List<GalleryMedia>): List<GalleryAlbum> =
    items
        .groupBy { normalizeRelativePath(it.relativePath) }
        .map { (path, media) ->
            GalleryAlbum(
                relativePath = path,
                name = albumDisplayName(path),
                count = media.size,
                cover = media.maxByOrNull { it.dateAdded }
            )
        }
        .sortedBy { it.name.lowercase() }
