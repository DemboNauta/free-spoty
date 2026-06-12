package com.freespoty.app.data.recommendation

import com.freespoty.app.data.db.entities.Track
import com.freespoty.app.data.source.DiscoverPlaylist
import com.freespoty.app.data.source.YouTubeSource

/**
 * Devuelve canciones similares a una semilla basándose únicamente en el artista
 * (el modelo Track no expone género). Sin estado: re-consulta YouTube cada vez
 * para mantener frescura.
 */
class RecommendationEngine(
    private val youtubeSource: YouTubeSource
) {

    suspend fun similarTo(
        seed: Track,
        exclude: Set<String> = emptySet(),
        limit: Int = 5
    ): List<Track> {
        if (limit <= 0) return emptyList()

        // 1º: Mix de YouTube (radio RD<videoId>) — recomendaciones del algoritmo
        // real de YouTube para la canción semilla, no solo "más del mismo artista".
        val fromMix = fromMix(seed, exclude, limit)
        if (fromMix.size >= limit) return fromMix.take(limit)

        // Fallback / relleno: búsqueda por artista.
        val fromSearch = byArtistSearch(seed, exclude + fromMix.map { it.id }.toSet(), limit - fromMix.size)
        return (fromMix + fromSearch).distinctBy { it.id }.take(limit)
    }

    private suspend fun fromMix(seed: Track, exclude: Set<String>, limit: Int): List<Track> {
        val videoId = seed.remoteId ?: return emptyList()
        YouTubeSource.ensureInitialized()
        val raw = runCatching { youtubeSource.fetchMix(videoId, limit = limit * 3) }
            .getOrDefault(emptyList())
        return raw.asSequence()
            .filter { it.id !in exclude }
            .filter { it.remoteId != seed.remoteId }
            .filter { it.durationMs in MIN_DURATION_MS..MAX_DURATION_MS }
            .take(limit)
            .toList()
    }

    private suspend fun byArtistSearch(seed: Track, exclude: Set<String>, limit: Int): List<Track> {
        if (limit <= 0) return emptyList()
        val query = buildQuery(seed) ?: return emptyList()

        YouTubeSource.ensureInitialized()
        val raw = runCatching { youtubeSource.search(query, limit = 25) }
            .getOrDefault(emptyList())

        val seedArtist = seed.artist?.trim().orEmpty()
        val seenRemoteIds = mutableSetOf<String>()
        seed.remoteId?.let(seenRemoteIds::add)
        val perArtistCount = mutableMapOf<String, Int>()

        return raw.asSequence()
            .filter { it.id !in exclude }
            .filter { it.remoteId != seed.remoteId }
            .filter { it.durationMs in MIN_DURATION_MS..MAX_DURATION_MS }
            .filter { it.remoteId?.let(seenRemoteIds::add) ?: true }
            .map { it to score(it, seedArtist) }
            .sortedByDescending { it.second }
            .map { it.first }
            // Diversidad: máximo 2 pistas por artista para no llenar la cola
            // con 5 vídeos del mismo canal.
            .filter { t ->
                val key = t.artist?.trim()?.lowercase().orEmpty()
                val n = perArtistCount.getOrDefault(key, 0)
                if (n >= MAX_PER_ARTIST) false else { perArtistCount[key] = n + 1; true }
            }
            .take(limit)
            .toList()
    }

    suspend fun discoverPlaylists(seed: Track, limit: Int = 8): List<DiscoverPlaylist> {
        if (limit <= 0) return emptyList()
        val query = buildQuery(seed) ?: return emptyList()
        YouTubeSource.ensureInitialized()

        // Primera opción siempre: el Mix de YouTube para la canción semilla —
        // playlist generada por el algoritmo de YouTube, importable como las demás.
        val mixEntry = seed.remoteId?.let { id ->
            DiscoverPlaylist(
                name = "Mix: ${seed.title}",
                url = YouTubeSource.mixUrl(id),
                thumbnailUrl = seed.artworkUri,
                uploader = "YouTube Mix",
                streamCount = 25
            )
        }

        val raw = runCatching { youtubeSource.searchPlaylists(query, limit = limit * 2) }
            .getOrDefault(emptyList())
        val searched = raw
            .filter { it.streamCount in 3..500 }
            .filter { !NOISE_REGEX.containsMatchIn(it.name) }
            .distinctBy { it.url }

        return (listOfNotNull(mixEntry) + searched).take(limit)
    }

    private fun buildQuery(seed: Track): String? {
        val artist = seed.artist?.trim().orEmpty()
        if (artist.isNotEmpty()) return artist
        val titleFallback = seed.title.split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .take(3)
            .joinToString(" ")
        return titleFallback.takeIf { it.isNotBlank() }
    }

    private fun score(candidate: Track, seedArtist: String): Int {
        var s = 0
        val candArtist = candidate.artist?.trim().orEmpty()
        if (seedArtist.isNotEmpty()) {
            if (candArtist.equals(seedArtist, ignoreCase = true)) s += 3
            else if (candArtist.contains(seedArtist, ignoreCase = true)) s += 1
        }
        if (NOISE_REGEX.containsMatchIn(candidate.title)) s -= 1
        return s
    }

    companion object {
        private const val MIN_DURATION_MS = 30_000L
        private const val MAX_DURATION_MS = 15 * 60 * 1000L
        private const val MAX_PER_ARTIST = 2
        private val NOISE_REGEX = Regex(
            "(?i)\\b(mix|compilation|full album|\\d+\\s*hour[s]?|playlist)\\b"
        )
    }
}
