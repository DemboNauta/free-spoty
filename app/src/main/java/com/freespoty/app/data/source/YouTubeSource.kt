package com.freespoty.app.data.source

import com.freespoty.app.data.db.entities.Track
import com.freespoty.app.data.db.entities.TrackSource
import com.freespoty.app.network.NewPipeDownloader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.StreamExtractor
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.Locale

data class StreamUrls(val audioUrl: String, val mimeType: String?)

data class DiscoverPlaylist(
    val name: String,
    val url: String,
    val thumbnailUrl: String?,
    val uploader: String?,
    val streamCount: Long
)

/**
 * Wrapper around NewPipeExtractor's YouTube service. All methods are suspending and
 * dispatch to IO; never call from the main thread.
 */
class YouTubeSource {

    private val youtube get() = ServiceList.YouTube

    suspend fun search(query: String, limit: Int = 25): List<Track> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        ensureInitialized()
        val extractor = youtube.getSearchExtractor(query)
        extractor.fetchPage()
        val items = extractor.initialPage.items
            .filterIsInstance<StreamInfoItem>()
            .take(limit)
        items.map { it.toTrack() }
    }

    suspend fun searchPlaylists(query: String, limit: Int = 10): List<DiscoverPlaylist> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        ensureInitialized()
        val qh = youtube.searchQHFactory.fromQuery(
            query,
            listOf(YoutubeSearchQueryHandlerFactory.PLAYLISTS),
            ""
        )
        val extractor = youtube.getSearchExtractor(qh)
        extractor.fetchPage()
        extractor.initialPage.items
            .filterIsInstance<PlaylistInfoItem>()
            .take(limit)
            .map {
                DiscoverPlaylist(
                    name = it.name ?: "Playlist",
                    url = it.url,
                    thumbnailUrl = it.thumbnails?.maxByOrNull { t -> t.width }?.url,
                    uploader = it.uploaderName,
                    streamCount = it.streamCount.coerceAtLeast(0L)
                )
            }
    }

    suspend fun resolveStream(remoteId: String): StreamUrls = withContext(Dispatchers.IO) {
        ensureInitialized()
        val url = remoteId.toYouTubeUrl()
        val extractor: StreamExtractor = youtube.getStreamExtractor(url)
        extractor.fetchPage()
        val streams = extractor.audioStreams
        // Prefer the highest bitrate m4a/mp4 stream — best compatibility with ExoPlayer.
        val best = streams
            .filter { it.url.isNullOrBlank().not() }
            .maxByOrNull { it.averageBitrate.takeIf { b -> b > 0 } ?: it.bitrate }
            ?: error("No audio streams available")
        StreamUrls(best.url!!, best.format?.mimeType)
    }

    /** Fetch a YouTube playlist (regular playlist URL with list= param). */
    suspend fun fetchPlaylist(url: String): Pair<String, List<Track>> = withContext(Dispatchers.IO) {
        ensureInitialized()
        val extractor = youtube.getPlaylistExtractor(url)
        extractor.fetchPage()
        val name = extractor.name ?: "Playlist de YouTube"
        val items = mutableListOf<StreamInfoItem>()
        items += extractor.initialPage.items.filterIsInstance<StreamInfoItem>()
        var nextPage = extractor.initialPage.nextPage
        var safety = 5
        while (nextPage != null && safety-- > 0) {
            val page = extractor.getPage(nextPage)
            items += page.items.filterIsInstance<StreamInfoItem>()
            nextPage = page.nextPage
        }
        name to items.map { it.toTrack() }
    }

    private fun StreamInfoItem.toTrack(): Track {
        val videoId = extractVideoId(url)
        return Track(
            id = "yt-$videoId",
            title = name ?: "Sin título",
            artist = uploaderName,
            album = null,
            durationMs = (duration.coerceAtLeast(0L)) * 1000L,
            uri = url, // remote watch URL; resolved to a real stream URL on play
            artworkUri = thumbnails?.maxByOrNull { it.width }?.url,
            source = TrackSource.REMOTE,
            remoteId = videoId
        )
    }

    private fun extractVideoId(watchUrl: String): String {
        val v = Regex("[?&]v=([A-Za-z0-9_-]{11})").find(watchUrl)?.groupValues?.get(1)
        return v ?: watchUrl.substringAfterLast("/")
    }

    private fun String.toYouTubeUrl(): String =
        if (startsWith("http")) this else "https://www.youtube.com/watch?v=$this"

    companion object {
        @Volatile private var initialized = false

        fun ensureInitialized() {
            if (initialized) return
            synchronized(this) {
                if (initialized) return
                NewPipe.init(NewPipeDownloader(), Localization.fromLocale(Locale.getDefault()))
                initialized = true
            }
        }
    }
}
