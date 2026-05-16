package com.freespoty.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.freespoty.app.data.db.entities.Playlist
import com.freespoty.app.data.db.entities.PlaylistTrackCrossRef
import com.freespoty.app.data.db.entities.Track
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {

    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun observePlaylists(): Flow<List<Playlist>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun findById(id: Long): Playlist?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(playlist: Playlist): Long

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addCrossRef(ref: PlaylistTrackCrossRef)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addCrossRefs(refs: List<PlaylistTrackCrossRef>)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun removeTrack(playlistId: Long, trackId: String)

    @Query("SELECT COUNT(*) FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun trackCount(playlistId: Long): Int

    @Transaction
    @Query(
        """
        SELECT t.* FROM tracks t
        INNER JOIN playlist_tracks pt ON pt.trackId = t.id
        WHERE pt.playlistId = :playlistId
        ORDER BY pt.position ASC
        """
    )
    fun observeTracksOfPlaylist(playlistId: Long): Flow<List<Track>>

    @Transaction
    @Query(
        """
        SELECT t.* FROM tracks t
        INNER JOIN playlist_tracks pt ON pt.trackId = t.id
        WHERE pt.playlistId = :playlistId
        ORDER BY pt.position ASC
        """
    )
    suspend fun tracksOfPlaylist(playlistId: Long): List<Track>
}
