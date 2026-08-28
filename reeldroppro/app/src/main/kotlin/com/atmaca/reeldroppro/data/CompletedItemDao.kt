package com.atmaca.reeldroppro.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CompletedItemDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(item: CompletedItemEntity): Long

    @Query("SELECT COUNT(*) FROM completed_items WHERE platform=:platform AND sourceKey=:sourceKey AND mediaKey=:mediaKey")
    suspend fun exists(platform: String, sourceKey: String, mediaKey: String): Int

    @Query("SELECT * FROM completed_items WHERE platform=:platform AND sourceKey=:sourceKey ORDER BY completedAt DESC")
    suspend fun bySource(platform: String, sourceKey: String): List<CompletedItemEntity>
}
