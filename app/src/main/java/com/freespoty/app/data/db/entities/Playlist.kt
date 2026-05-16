package com.freespoty.app.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playlists")
data class Playlist(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String? = null,
    val artworkUri: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val importedFrom: String? = null
)
