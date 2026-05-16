package com.freespoty.app.data.repository

import android.net.Uri
import com.freespoty.app.data.db.dao.PlaylistDao
import com.freespoty.app.data.db.dao.TrackDao
import com.freespoty.app.data.db.entities.Playlist
import com.freespoty.app.data.db.entities.PlaylistTrackCrossRef
import com.freespoty.app.data.db.entities.Track
import com.freespoty.app.data.db.entities.TrackSource
import com.freespoty.app.data.scanner.LocalMusicScanner
import com.freespoty.app.data.source.YouTubeSource
import kotlinx.coroutines.flow.Flow
import java.io.File

class MusicRepository(
    private val trackDao: TrackDao,
    private val playlistDao: PlaylistDao,
    private val scanner: LocalMusicScanner,
    private val youtubeSource: YouTubeSource
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

    /**
     * For tracks that originate from a remote service, the stored `uri` is the watch URL
     * (used for display / re-resolving). At playback time we need an actual audio stream
     * URL, which requires hitting the extractor again. For downloaded tracks we can play
     * the local file directly.
     */
    suspend fun resolvePlayableUri(track: Track): String = when (track.source) {
        TrackSource.LOCAL -> track.uri
        TrackSource.DOWNLOADED -> track.uri
        TrackSource.REMOTE -> {
            val remoteId = track.remoteId ?: track.id.removePrefix("yt-")
            YouTubeSource.ensureInitialized()
            youtubeSource.resolveStream(remoteId).audioUrl
        }
    }

    /** Persist a remote track as DOWNLOADED, pointing at the local file. */
    suspend fun markTrackDownloaded(trackId: String, localPath: String) {
        val existing = trackDao.findById(trackId) ?: return
        val localUri = Uri.fromFile(File(localPath)).toString()
        trackDao.upsert(
            existing.copy(
                uri = localUri,
                source = TrackSource.DOWNLOADED
            )
        )
    }
}
