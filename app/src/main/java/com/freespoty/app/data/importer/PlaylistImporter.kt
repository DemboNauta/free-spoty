package com.freespoty.app.data.importer

import android.util.Log
import com.freespoty.app.data.db.entities.Track
import com.freespoty.app.data.download.DownloadManager
import com.freespoty.app.data.repository.MusicRepository
import com.freespoty.app.data.source.YouTubeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

sealed interface ImportProgress {
    data class Working(val message: String, val done: Int = 0, val total: Int = 0) : ImportProgress
}

sealed interface ImportOutcome {
    data class Success(val playlistId: Long, val tracksAdded: Int) : ImportOutcome
    data class Failure(val message: String) : ImportOutcome
}

/**
 * Resolves a remote playlist URL (Spotify or YouTube), creates a local Playlist, and
 * populates it with tracks. For Spotify imports we return as soon as the playlist row
 * exists; YouTube matching + offline download for each track continues in [scope] so
 * the caller can open the playlist immediately and see tracks appear progressively.
 */
class PlaylistImporter(
    private val repository: MusicRepository,
    private val youtube: YouTubeSource,
    private val spotify: SpotifyPlaylistScraper,
    private val downloads: DownloadManager,
    private val scope: CoroutineScope
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
        val playlistId = repository.createPlaylist(name = meta.name, importedFrom = "Spotify")

        // Background: match each Spotify track against YouTube in parallel (bounded),
        // persist + add to the playlist + enqueue offline download. Caller can open
        // the playlist right now and watch tracks appear as they resolve.
        Log.i(TAG, "Spotify meta OK: name='${meta.name}', tracks=${meta.tracks.size}, playlistId=$playlistId")
        scope.launch {
            val limiter = Semaphore(permits = 4)
            val writeLock = Mutex()
            var ok = 0
            var fail = 0
            try {
                coroutineScope {
                    meta.tracks.mapIndexed { idx, item ->
                        async {
                            limiter.withPermit {
                                val query = listOfNotNull(item.title, item.artist).joinToString(" ")
                                val searchResult = runCatching { youtube.search(query, limit = 1) }
                                searchResult.exceptionOrNull()?.let {
                                    Log.w(TAG, "search failed idx=$idx query='$query'", it)
                                }
                                val hit = searchResult.getOrDefault(emptyList()).firstOrNull()
                                if (hit == null) {
                                    fail++
                                    Log.w(TAG, "no match idx=$idx query='$query'")
                                    return@async null
                                }
                                try {
                                    writeLock.withLock {
                                        repository.saveTracks(listOf(hit))
                                        repository.addTrackToPlaylist(playlistId, hit.id)
                                    }
                                    ok++
                                    Log.i(TAG, "added idx=$idx ${hit.id} '${hit.title}'")
                                } catch (t: Throwable) {
                                    fail++
                                    Log.e(TAG, "save/add failed idx=$idx", t)
                                }
                                hit
                            }
                        }
                    }.awaitAll()
                }
            } catch (t: Throwable) {
                Log.e(TAG, "background import scope crashed", t)
            }
            Log.i(TAG, "Spotify import done: ok=$ok fail=$fail total=${meta.tracks.size}")
        }

        return ImportOutcome.Success(playlistId, meta.tracks.size)
    }

    private fun detectSource(url: String): Source? = when {
        url.contains("spotify.com") -> Source.SPOTIFY
        url.contains("youtube.com") || url.contains("youtu.be") -> Source.YOUTUBE
        else -> null
    }

    private enum class Source { YOUTUBE, SPOTIFY }

    private companion object { const val TAG = "PlaylistImporter" }
}
