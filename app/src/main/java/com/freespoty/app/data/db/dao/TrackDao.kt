package com.freespoty.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
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

    // NUNCA usar OnConflictStrategy.REPLACE en tracks: SQLite lo implementa como
    // DELETE+INSERT, lo que dispara el CASCADE de playlist_tracks (el track
    // desaparece de todas las playlists) y machaca source/uri de pistas ya
    // descargadas (DOWNLOADED → REMOTE, se pierde el archivo local).
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoreAll(tracks: List<Track>): List<Long>

    @Query(
        """
        UPDATE tracks SET
            title = :title,
            artist = :artist,
            album = :album,
            durationMs = CASE WHEN :durationMs > 0 THEN :durationMs ELSE durationMs END,
            artworkUri = COALESCE(:artworkUri, artworkUri)
        WHERE id = :id
        """
    )
    suspend fun updateMetadata(
        id: String,
        title: String,
        artist: String?,
        album: String?,
        durationMs: Long,
        artworkUri: String?
    )

    /**
     * Upsert seguro: inserta los nuevos y, para los que ya existen, actualiza solo
     * metadatos. Preserva uri/source (descargas) y addedAt, y no dispara CASCADE.
     */
    @Transaction
    suspend fun upsertAll(tracks: List<Track>) {
        val rowIds = insertIgnoreAll(tracks)
        tracks.forEachIndexed { i, t ->
            if (rowIds[i] == -1L) {
                updateMetadata(t.id, t.title, t.artist, t.album, t.durationMs, t.artworkUri)
            }
        }
    }

    suspend fun upsert(track: Track) = upsertAll(listOf(track))

    // UPDATE directo: evita REPLACE → DELETE+INSERT que dispararía CASCADE
    // en playlist_tracks (FK onDelete=CASCADE) y borraría el track de todas
    // las playlists al marcarlo como descargado.
    @Query("UPDATE tracks SET uri = :uri, source = :source WHERE id = :id")
    suspend fun updateLocalSource(id: String, uri: String, source: com.freespoty.app.data.db.entities.TrackSource)

    @Query("DELETE FROM tracks WHERE id = :id")
    suspend fun deleteById(id: String)
}
