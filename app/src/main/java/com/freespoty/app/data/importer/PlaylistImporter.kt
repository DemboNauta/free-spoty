package com.freespoty.app.data.importer

import com.freespoty.app.data.db.entities.Track
import com.freespoty.app.data.repository.MusicRepository
import com.freespoty.app.data.source.YouTubeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface ImportProgress {
    data class Working(val message: String, val done: Int = 0, val total: Int = 0) : ImportProgress
}

sealed interface ImportOutcome {
    data class Success(val playlistId: Long, val tracksAdded: Int) : ImportOutcome
    data class Failure(val message: String) : ImportOutcome
}

/**
 * Resolves a remote playlist URL (Spotify or YouTube), creates a local Playlist,
 * and adds the resolved tracks to it.
 *
 *  - YouTube playlists are read directly via NewPipeExtractor.
 *  - Spotify playlists only expose track metadata publicly (title/artist) via the embed
 *    page; each track is then matched against YouTube to produce a playable stream.
 */
class PlaylistImporter(
    private val repository: MusicRepository,
    private val youtube: YouTubeSource,
    private val spotify: SpotifyPlaylistScraper
) {
    suspend fun import(
        rawUrl: String,
        onProgress: (ImportProgress) -> Unit = {}
    ): ImportOutcome = withContext(Dispatchers.IO) {
        val url = rawUrl.trim()
        try {
            YouTubeSource.ensureInitialized()
            val source = detectSource(url) ?: return@withContext ImportOutcome.Failure(
                "URL no reconocida. Usa una URL pública de Spotify o YouTube."
            )

            when (source) {
                Source.YOUTUBE -> importYouTube(url, onProgress)
                Source.SPOTIFY -> importSpotify(url, onProgress)
            }
        } catch (t: Throwable) {
            ImportOutcome.Failure(t.message ?: "Error al importar")
        }
    }

    private suspend fun importYouTube(
        url: String,
        onProgress: (ImportProgress) -> Unit
    ): ImportOutcome {
        onProgress(ImportProgress.Working("Leyendo playlist de YouTube…"))
        val (name, tracks) = youtube.fetchPlaylist(url)
        if (tracks.isEmpty()) return ImportOutcome.Failure("La playlist no tiene pistas accesibles.")
        repository.saveTracks(tracks)
        val playlistId = repository.createPlaylist(name = name, importedFrom = "YouTube")
        repository.addTracksToPlaylist(playlistId, tracks.map { it.id })
        return ImportOutcome.Success(playlistId, tracks.size)
    }

    private suspend fun importSpotify(
        url: String,
        onProgress: (ImportProgress) -> Unit
    ): ImportOutcome {
        onProgress(ImportProgress.Working("Leyendo metadatos de Spotify…"))
        val meta = spotify.fetch(url)
        if (meta.tracks.isEmpty()) {
            return ImportOutcome.Failure("No se pudieron leer las pistas. ¿La playlist es pública?")
        }
        val resolved = mutableListOf<Track>()
        meta.tracks.forEachIndexed { index, item ->
            onProgress(
                ImportProgress.Working(
                    message = "Buscando \"${item.title}\" en YouTube",
                    done = index,
                    total = meta.tracks.size
                )
            )
            val query = listOfNotNull(item.title, item.artist).joinToString(" ")
            val results = runCatching { youtube.search(query, limit = 1) }.getOrDefault(emptyList())
            results.firstOrNull()?.let { resolved += it }
        }
        if (resolved.isEmpty()) {
            return ImportOutcome.Failure("No se pudo emparejar ninguna pista en YouTube.")
        }
        repository.saveTracks(resolved)
        val playlistId = repository.createPlaylist(name = meta.name, importedFrom = "Spotify")
        repository.addTracksToPlaylist(playlistId, resolved.map { it.id })
        return ImportOutcome.Success(playlistId, resolved.size)
    }

    private fun detectSource(url: String): Source? = when {
        url.contains("spotify.com") -> Source.SPOTIFY
        url.contains("youtube.com") || url.contains("youtu.be") -> Source.YOUTUBE
        else -> null
    }

    private enum class Source { YOUTUBE, SPOTIFY }
}
