package com.freespoty.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.freespoty.app.data.db.entities.Track
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {

    @Query("SELECT * FROM tracks ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<Track>>

    @Query("SELECT * FROM tracks WHERE id = :id")
    suspend fun findById(id: String): Track?

    @Query("SELECT * FROM tracks WHERE id IN (:ids)")
    suspend fun findByIds(ids: List<String>): List<Track>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(tracks: List<Track>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(track: Track)

    // UPDATE directo: evita REPLACE → DELETE+INSERT que dispararía CASCADE
    // en playlist_tracks (FK onDelete=CASCADE) y borraría el track de todas
    // las playlists al marcarlo como descargado.
    @Query("UPDATE tracks SET uri = :uri, source = :source WHERE id = :id")
    suspend fun updateLocalSource(id: String, uri: String, source: com.freespoty.app.data.db.entities.TrackSource)

    @Query("DELETE FROM tracks WHERE id = :id")
    suspend fun deleteById(id: String)
}
