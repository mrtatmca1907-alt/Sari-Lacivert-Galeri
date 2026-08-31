package com.atmaca.toplayici1907

data class ConsolidationPlan(
    val survivors: List<PhotoRecord>,
    val duplicates: List<PhotoRecord>,
    val hashFailures: List<PhotoRecord>
)

object ConsolidationPlanner {
    fun plan(
        photos: List<PhotoRecord>,
        hashProvider: (PhotoRecord) -> String?
    ): ConsolidationPlan {
        val survivors = mutableListOf<PhotoRecord>()
        val duplicates = mutableListOf<PhotoRecord>()
        val hashFailures = mutableListOf<PhotoRecord>()

        val bySize = photos.groupBy { it.size }
        for ((size, group) in bySize) {
            if (size <= 0L || group.size == 1) {
                survivors += group
                continue
            }

            val hashed = linkedMapOf<String, MutableList<PhotoRecord>>()
            for (photo in group) {
                val hash = runCatching { hashProvider(photo) }.getOrNull()
                if (hash.isNullOrBlank()) {
                    survivors += photo
                    hashFailures += photo
                } else {
                    hashed.getOrPut(hash) { mutableListOf() } += photo
                }
            }

            for (exactGroup in hashed.values) {
                if (exactGroup.size == 1) {
                    survivors += exactGroup.single()
                } else {
                    val survivor = CollectorPolicy.chooseSurvivor(exactGroup)
                    survivors += survivor
                    duplicates += exactGroup.filterNot { it.id == survivor.id }
                }
            }
        }

        return ConsolidationPlan(
            survivors = survivors.sortedBy { it.id },
            duplicates = duplicates.sortedBy { it.id },
            hashFailures = hashFailures.sortedBy { it.id }
        )
    }
}
