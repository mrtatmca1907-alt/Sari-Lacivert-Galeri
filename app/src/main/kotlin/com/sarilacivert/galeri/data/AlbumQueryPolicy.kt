package com.sarilacivert.galeri.data

internal data class AlbumQuery(
    val selection: String,
    val args: List<String>
)

internal object AlbumQueryPolicy {
    fun forAlbum(albumPath: String, modern: Boolean): AlbumQuery {
        return if (modern) {
            AlbumQuery(
                selection = "relative_path = ?",
                args = listOf(albumPath.trimEnd('/') + "/")
            )
        } else {
            AlbumQuery(
                selection = "_data LIKE ?",
                args = listOf(albumPath.trimEnd('/') + "/%")
            )
        }
    }
}
