package com.atmaca.reeldroppro.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface JobDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(job: JobEntity): Long

    @Update
    suspend fun update(job: JobEntity)

    @Query("SELECT * FROM jobs ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<JobEntity>>

    @Query("SELECT * FROM jobs WHERE state IN ('QUEUED','RETRY_WAIT') AND nextAttemptAt <= :now ORDER BY createdAt ASC LIMIT 1")
    suspend fun nextRunnable(now: Long): JobEntity?

    @Query("SELECT COUNT(*) FROM jobs WHERE state IN ('QUEUED','RESOLVING','DOWNLOADING','POST_PROCESSING','RETRY_WAIT')")
    suspend fun activeCount(): Int

    @Query("UPDATE jobs SET state=:state, updatedAt=:updatedAt, lastErrorType=:errorType, lastErrorMessage=:errorMessage, nextAttemptAt=:nextAttemptAt, attempt=:attempt WHERE id=:id")
    suspend fun setState(id: Long, state: String, updatedAt: Long, errorType: String?, errorMessage: String?, nextAttemptAt: Long, attempt: Int)

    @Query("UPDATE jobs SET progress=:progress, currentFile=:currentFile, bytesTransferred=:bytesTransferred, speedBytesPerSec=:speedBytesPerSec, downloadedCount=:downloadedCount, photoCount=:photoCount, videoCount=:videoCount, failedCount=:failedCount, updatedAt=:updatedAt WHERE id=:id")
    suspend fun updateProgress(id: Long, progress: Float, currentFile: String?, bytesTransferred: Long, speedBytesPerSec: Long, downloadedCount: Int, photoCount: Int, videoCount: Int, failedCount: Int, updatedAt: Long)

    @Query("UPDATE jobs SET state='QUEUED', nextAttemptAt=0, updatedAt=:now WHERE state IN ('RESOLVING','DOWNLOADING','POST_PROCESSING')")
    suspend fun recoverInterrupted(now: Long)
}
