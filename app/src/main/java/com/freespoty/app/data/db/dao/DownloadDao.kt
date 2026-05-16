package com.freespoty.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.freespoty.app.data.db.entities.DownloadEntry
import com.freespoty.app.data.db.entities.DownloadStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {

    @Query("SELECT * FROM downloads ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<DownloadEntry>>

    @Query("SELECT * FROM downloads WHERE trackId = :trackId")
    suspend fun findById(trackId: String): DownloadEntry?

    @Query("SELECT * FROM downloads WHERE status = :status")
    suspend fun byStatus(status: DownloadStatus): List<DownloadEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: DownloadEntry)

    @Query("UPDATE downloads SET status = :status, progress = :progress, updatedAt = :now WHERE trackId = :trackId")
    suspend fun updateProgress(trackId: String, status: DownloadStatus, progress: Int, now: Long = System.currentTimeMillis())

    @Query("UPDATE downloads SET status = :status, errorMessage = :error, updatedAt = :now WHERE trackId = :trackId")
    suspend fun markFailed(trackId: String, status: DownloadStatus = DownloadStatus.FAILED, error: String?, now: Long = System.currentTimeMillis())

    @Query("UPDATE downloads SET status = :status, localPath = :localPath, progress = 100, updatedAt = :now WHERE trackId = :trackId")
    suspend fun markCompleted(trackId: String, localPath: String, status: DownloadStatus = DownloadStatus.COMPLETED, now: Long = System.currentTimeMillis())

    @Query("DELETE FROM downloads WHERE trackId = :trackId")
    suspend fun deleteById(trackId: String)
}
