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
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PlayerUiState(
    val isPlaying: Boolean = false,
    val currentTrack: Track? = null,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false
)

/**
 * Thin wrapper around a [MediaController] that resolves [Track]s to media items and exposes
 * a Compose-friendly [StateFlow]. Held as a singleton in [com.freespoty.app.di.AppContainer].
 */
class PlayerController(private val appContext: Context) {

    private var controller: MediaController? = null
    private val trackIndex = mutableMapOf<String, Track>()

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

    /** Replaces the current queue and starts at [startIndex]. */
    fun playTracks(tracks: List<Track>, startIndex: Int = 0) {
        val c = controller ?: return
        if (tracks.isEmpty()) return
        trackIndex.clear()
        tracks.forEach { trackIndex[it.id] = it }
        val items = tracks.map { it.toMediaItem() }
        c.setMediaItems(items, startIndex, 0L)
        c.prepare()
        c.playWhenReady = true
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
        _state.value = PlayerUiState(
            isPlaying = c.isPlaying,
            currentTrack = track,
            positionMs = c.currentPosition.coerceAtLeast(0L),
            durationMs = c.duration.takeIf { it > 0 } ?: track?.durationMs ?: 0L,
            hasNext = c.hasNextMediaItem(),
            hasPrevious = c.hasPreviousMediaItem()
        )
    }

    private fun Track.toMediaItem(): MediaItem {
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
            .setUri(Uri.parse(uri))
            .setMediaMetadata(metadata)
            .build()
    }
}
