package com.atmaca.gallery

data class MutationRefreshPlan(
    val reloadCollectionImmediately: Boolean,
    val scanAlbumsImmediately: Boolean,
    val scanDuplicatesImmediately: Boolean,
    val refreshSecondaryScreensWhenOpened: Boolean
)

fun removeMutatedIds(currentIds: List<Long>, mutatedIds: Set<Long>): List<Long> {
    if (mutatedIds.isEmpty()) return currentIds
    return currentIds.filterNot(mutatedIds::contains)
}

fun mutationRefreshPlan(success: Boolean): MutationRefreshPlan = if (success) {
    MutationRefreshPlan(
        reloadCollectionImmediately = false,
        scanAlbumsImmediately = false,
        scanDuplicatesImmediately = false,
        refreshSecondaryScreensWhenOpened = true
    )
} else {
    MutationRefreshPlan(
        reloadCollectionImmediately = false,
        scanAlbumsImmediately = false,
        scanDuplicatesImmediately = false,
        refreshSecondaryScreensWhenOpened = false
    )
}
