package com.atmaca.reeldroppro.data

import androidx.room.Entity

@Entity(
    tableName = "completed_items",
    primaryKeys = ["platform", "sourceKey", "mediaKey"]
)
data class CompletedItemEntity(
    val platform: String,
    val sourceKey: String,
    val mediaKey: String,
    val localUri: String,
    val completedAt: Long
)
