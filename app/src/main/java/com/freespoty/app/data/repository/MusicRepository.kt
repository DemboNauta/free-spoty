package com.freespoty.app.data.repository

import android.net.Uri
import com.freespoty.app.data.db.dao.PlaylistDao
import com.freespoty.app.data.db.dao.TrackDao
import com.freespoty.app.data.db.entities.Playlist
import com.freespoty.app.data.db.entities.PlaylistTrackCrossRef
import com.freespoty.app.data.db.entities.Track
import com.freespoty.app.data.db.entities.TrackSource
import com.freespoty.app.data.recommendation.RecommendationEngine
import com.freespoty.app.data.scanner.LocalMusicScanner
import com.freespoty.app.data.source.DiscoverPlaylist
import com.freespoty.app.data.source.YouTubeSource
import kotlinx.coroutines.flow.Flow
import java.io.File

class MusicRepository(
    private val trackDao: TrackDao,
    private val playlistDao: PlaylistDao,
    private val scanner: LocalMusicScanner,
    private val youtubeSource: YouTubeSource,
    private val recommender: RecommendationEngine
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

    suspend fun markPlaylistSaved(id: Long) = playlistDao.markSaved(id)

    suspend fun purgePreviewPlaylists() = playlistDao.deleteAllPreviews()

    suspend fun discoverPlaylists(seed: Track, limit: Int = 8): List<DiscoverPlaylist> =
        recommender.discoverPlaylists(seed, limit)

    /**
     * Importa una playlist remota como preview (no guardada). Fetch tracks de YouTube,
     * crea Playlist con isPreview=true. Devuelve playlistId o null si falla.
     */
    suspend fun importPreviewPlaylist(disc: DiscoverPlaylist): Pair<Long, List<Track>>? {
        val (_, tracks) = runCatching { youtubeSource.fetchPlaylist(disc.url) }
            .getOrNull() ?: return null
        if (tracks.isEmpty()) return null
        trackDao.upsertAll(tracks)
        val playlistId = playlistDao.insert(
            Playlist(
                name = disc.name,
                description = disc.uploader,
                artworkUri = disc.thumbnailUrl,
                importedFrom = "Descubre",
                isPreview = true
            )
        )
        addTracksToPlaylist(playlistId, tracks.map { it.id })
        return playlistId to tracks
    }

    /**
     * Devuelve tracks similares a [seed] basados en el artista. Los resultados se
     * persisten en la base de datos para poder reproducirlos después sin re-buscar.
     */
    suspend fun similarTo(
        seed: Track,
        exclude: Set<String> = emptySet(),
        limit: Int = 5
    ): List<Track> {
        val results = recommender.similarTo(seed, exclude, limit)
        if (results.isNotEmpty()) trackDao.upsertAll(results)
        return results
    }

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
        val localUri = Uri.fromFile(File(localPath)).toString()
        // NO usar upsert(REPLACE): cascadea sobre playlist_tracks (FK onDelete=CASCADE)
        // y borra el track de todas las playlists.
        trackDao.updateLocalSource(trackId, localUri, TrackSource.DOWNLOADED)
    }
}
