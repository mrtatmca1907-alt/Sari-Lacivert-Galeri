package com.atmaca.reeldroppro.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "jobs")
data class JobEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val platform: String,
    val sourceKey: String,
    val inputValue: String,
    val state: String,
    val createdAt: Long,
    val updatedAt: Long,
    val attempt: Int = 0,
    val nextAttemptAt: Long = 0,
    val lastErrorType: String? = null,
    val lastErrorMessage: String? = null,
    val progress: Float = 0f,
    val downloadedCount: Int = 0,
    val photoCount: Int = 0,
    val videoCount: Int = 0,
    val failedCount: Int = 0,
    val currentFile: String? = null,
    val bytesTransferred: Long = 0,
    val speedBytesPerSec: Long = 0,
    val slotId: Int = 0
)
