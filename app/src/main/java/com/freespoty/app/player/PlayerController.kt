package com.freespoty.app.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.freespoty.app.data.db.entities.Track
import com.freespoty.app.data.repository.MusicRepository
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PlayerUiState(
    val isPlaying: Boolean = false,
    val currentTrack: Track? = null,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
    val isBuffering: Boolean = false,
    val errorMessage: String? = null
)

/**
 * Thin wrapper around a [MediaController] that resolves [Track]s to media items and exposes
 * a Compose-friendly [StateFlow]. Remote tracks require an async stream resolution step
 * (handled here via [MusicRepository.resolvePlayableUri]) before being passed to ExoPlayer.
 */
class PlayerController(
    private val appContext: Context,
    private val repository: MusicRepository
) {
    private var controller: MediaController? = null
    private val trackIndex = mutableMapOf<String, Track>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            refreshFromPlayer()
        }
    }

    fun connect() {
        if (controller != null) return
        val token = SessionToken(appContext, ComponentName(appContext, PlayerService::class.java))
        val future = MediaController.Builder(appContext, token).buildAsync()
        future.addListener({
            controller = future.get()
            controller?.addListener(listener)
            refreshFromPlayer()
        }, MoreExecutors.directExecutor())
    }

    fun release() {
        controller?.removeListener(listener)
        controller?.release()
        controller = null
    }

    /**
     * Replaces the current queue and starts at [startIndex]. Remote tracks have their
     * stream URLs resolved on a background thread before media items are submitted.
     * Resolution runs concurrently (capped) so a 50-track playlist doesn't serialize
     * 50 HTTP roundtrips.
     */
    fun playTracks(tracks: List<Track>, startIndex: Int = 0) {
        if (tracks.isEmpty()) return
        scope.launch {
            _state.value = _state.value.copy(isBuffering = true, errorMessage = null)
            try {
                trackIndex.clear()
                tracks.forEach { trackIndex[it.id] = it }
                val items = withContext(Dispatchers.IO) {
                    coroutineScope {
                        tracks.map { track ->
                            async {
                                val playableUri = runCatching { repository.resolvePlayableUri(track) }
                                    .getOrDefault(track.uri)
                                track.toMediaItem(playableUri)
                            }
                        }.awaitAll()
                    }
                }
                val c = controller ?: return@launch
                c.setMediaItems(items, startIndex, 0L)
                c.prepare()
                c.playWhenReady = true
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    isBuffering = false,
                    errorMessage = t.message ?: "Error al reproducir"
                )
            }
        }
    }

    fun togglePlayPause() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun next() = controller?.seekToNextMediaItem()
    fun previous() = controller?.seekToPreviousMediaItem()
    fun seekTo(ms: Long) = controller?.seekTo(ms)

    fun currentPositionMs(): Long = controller?.currentPosition ?: 0L

    private fun refreshFromPlayer() {
        val c = controller ?: return
        val item = c.currentMediaItem
        val trackId = item?.mediaId
        val track = trackId?.let { trackIndex[it] }
        _state.value = _state.value.copy(
            isPlaying = c.isPlaying,
            currentTrack = track,
            positionMs = c.currentPosition.coerceAtLeast(0L),
            durationMs = c.duration.takeIf { it > 0 } ?: track?.durationMs ?: 0L,
            hasNext = c.hasNextMediaItem(),
            hasPrevious = c.hasPreviousMediaItem(),
            isBuffering = c.playbackState == Player.STATE_BUFFERING
        )
    }

    private fun Track.toMediaItem(playableUri: String): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist ?: appContext.getString(com.freespoty.app.R.string.unknown_artist))
            .setAlbumTitle(album)
            .setArtworkUri(artworkUri?.let { Uri.parse(it) })
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .build()

        return MediaItem.Builder()
            .setMediaId(id)
            .setUri(Uri.parse(playableUri))
            .setMediaMetadata(metadata)
            .build()
    }

}
