package com.freespoty.app.data.repository

import com.freespoty.app.data.db.dao.PlaylistDao
import com.freespoty.app.data.db.dao.TrackDao
import com.freespoty.app.data.db.entities.Playlist
import com.freespoty.app.data.db.entities.PlaylistTrackCrossRef
import com.freespoty.app.data.db.entities.Track
import com.freespoty.app.data.scanner.LocalMusicScanner
import kotlinx.coroutines.flow.Flow

class MusicRepository(
    private val trackDao: TrackDao,
    private val playlistDao: PlaylistDao,
    private val scanner: LocalMusicScanner
) {
    fun observeTracks(): Flow<List<Track>> = trackDao.observeAll()

    fun observePlaylists(): Flow<List<Playlist>> = playlistDao.observePlaylists()

    fun observePlaylistTracks(playlistId: Long): Flow<List<Track>> =
        playlistDao.observeTracksOfPlaylist(playlistId)

    suspend fun playlist(id: Long): Playlist? = playlistDao.findById(id)

    suspend fun playlistTracks(id: Long): List<Track> = playlistDao.tracksOfPlaylist(id)

    suspend fun track(id: String): Track? = trackDao.findById(id)

    suspend fun scanLocalMusic() {
        val found = scanner.scan()
        if (found.isNotEmpty()) trackDao.upsertAll(found)
    }

    suspend fun createPlaylist(name: String, description: String? = null, importedFrom: String? = null): Long {
        return playlistDao.insert(Playlist(name = name, description = description, importedFrom = importedFrom))
    }

    suspend fun deletePlaylist(id: Long) = playlistDao.deleteById(id)

    suspend fun addTrackToPlaylist(playlistId: Long, trackId: String) {
        val pos = playlistDao.trackCount(playlistId)
        playlistDao.addCrossRef(PlaylistTrackCrossRef(playlistId, trackId, pos))
    }

    suspend fun addTracksToPlaylist(playlistId: Long, trackIds: List<String>) {
        var pos = playlistDao.trackCount(playlistId)
        val refs = trackIds.map { id ->
            PlaylistTrackCrossRef(playlistId, id, pos++)
        }
        playlistDao.addCrossRefs(refs)
    }

    suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: String) =
        playlistDao.removeTrack(playlistId, trackId)

    suspend fun saveTracks(tracks: List<Track>) = trackDao.upsertAll(tracks)
}
