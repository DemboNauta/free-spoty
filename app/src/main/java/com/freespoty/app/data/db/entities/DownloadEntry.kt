package com.freespoty.app.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class DownloadStatus { QUEUED, RUNNING, COMPLETED, FAILED }

@Entity(tableName = "downloads")
data class DownloadEntry(
    @PrimaryKey val trackId: String,
    val title: String,
    val artist: String?,
    val artworkUri: String?,
    val remoteId: String?,
    val localPath: String?,
    val status: DownloadStatus,
    val progress: Int = 0,
    val errorMessage: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)
