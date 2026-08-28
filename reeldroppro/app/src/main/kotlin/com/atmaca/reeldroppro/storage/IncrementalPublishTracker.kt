package com.atmaca.reeldroppro.storage

class IncrementalPublishTracker {
    private val published = linkedSetOf<String>()

    fun unpublished(paths: List<String>): List<String> = paths.filterNot(published::contains)

    fun markPublished(paths: Collection<String>) {
        published.addAll(paths)
    }
}
