package com.freespoty.app.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Source of a track: local file from device, remote stream (e.g. YouTube), or downloaded copy.
 */
enum class TrackSource { LOCAL, REMOTE, DOWNLOADED }

@Entity(tableName = "tracks")
data class Track(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String?,
    val album: String?,
    val durationMs: Long,
    val uri: String,
    val artworkUri: String?,
    val source: TrackSource,
    val remoteId: String? = null,
    val addedAt: Long = System.currentTimeMillis()
)
