package com.freespoty.app.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.freespoty.app.data.db.entities.Track
import com.freespoty.app.data.repository.MusicRepository
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
    val shuffleEnabled: Boolean = false,
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
    private var playJob: Job? = null
    private var autoQueueJob: Job? = null
    private var lastAutoSeedId: String? = null

    var autoQueueEnabled: Boolean = true

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            refreshFromPlayer()
        }

        // Si un item falla (URL stale, bot block, 403…), saltar al siguiente en vez de pausar.
        // NO manejamos STATE_ENDED aquí: ExoPlayer transiciona auto al siguiente cuando hay,
        // y un seekToNextMediaItem extra desincroniza notification/player (muestra item B
        // mientras suena C). Recovery de "current acabó sin siguiente en timeline" se hace
        // tras el addMediaItem de background en playTracks.
        override fun onPlayerError(error: PlaybackException) {
            val c = controller ?: return
            if (c.hasNextMediaItem()) {
                c.seekToNextMediaItem()
                c.prepare()
                c.play()
            }
        }
    }

    fun connect() {
        if (controller != null) return
        val token = SessionToken(appContext, ComponentName(appContext, PlayerService::class.java))
        val future = MediaController.Builder(appContext, token).buildAsync()
        future.addListener({
            try {
                controller = future.get()
                controller?.addListener(listener)
                refreshFromPlayer()
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    errorMessage = "No se pudo conectar al reproductor: ${t.message}"
                )
            }
        }, MoreExecutors.directExecutor())
    }

    fun release() {
        playJob?.cancel()
        controller?.removeListener(listener)
        controller?.release()
        controller = null
        scope.cancel()
    }

    /**
     * Replaces the current queue and starts at [startIndex]. Resolves the initial track first
     * para arrancar reproducción ya; el resto se resuelve secuencialmente en background y se
     * añade a la timeline en orden. ExoPlayer pre-bufferea el siguiente item automáticamente
     * → transición sin pausa. Secuencial evita que N resoluciones de YouTube en paralelo
     * disparen el bot block. Sin auto-descarga: el usuario decide qué guardar.
     */
    fun playTracks(tracks: List<Track>, startIndex: Int = 0) {
        if (tracks.isEmpty()) return
        val safeStart = startIndex.coerceIn(0, tracks.lastIndex)
        playJob?.cancel()
        autoQueueJob?.cancel()
        lastAutoSeedId = null
        trackIndex.clear()
        tracks.forEach { trackIndex[it.id] = it }
        playJob = scope.launch {
            _state.value = _state.value.copy(isBuffering = true, errorMessage = null)
            try {
                // Pre-resolver hasta 3 items (current + 2 siguientes) antes de arrancar:
                // garantiza que el player tiene siguiente en timeline cuando termine el actual,
                // incluso para tracks muy cortos donde la resolución background no llegaría a tiempo.
                val initialBatch = tracks.subList(safeStart, minOf(safeStart + 3, tracks.size))
                val initialItems = mutableListOf<MediaItem>()
                for (t in initialBatch) {
                    initialItems += withContext(Dispatchers.IO) { resolveMediaItem(t) }
                }
                val c = controller ?: return@launch
                c.setMediaItems(initialItems, 0, 0L)
                c.prepare()
                c.playWhenReady = true

                // Resto secuencial: primero los que van DESPUÉS del batch inicial, después los previos.
                val after = tracks.subList(safeStart + initialBatch.size, tracks.size)
                val before = tracks.subList(0, safeStart)
                for (t in after) {
                    val item = withContext(Dispatchers.IO) { resolveMediaItem(t) }
                    val ctrl = controller ?: return@launch
                    ctrl.addMediaItem(item)
                    rescueIfEnded(ctrl)
                }
                for ((i, t) in before.withIndex()) {
                    val item = withContext(Dispatchers.IO) { resolveMediaItem(t) }
                    controller?.addMediaItem(i, item) ?: return@launch
                }
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    isBuffering = false,
                    errorMessage = t.message ?: "Error al reproducir"
                )
            }
        }
    }

    // Si el current acabó (STATE_ENDED) y NO había siguiente en timeline, ExoPlayer
    // queda parado. Cuando llega el item del background, hay que arrancarlo manualmente.
    private fun rescueIfEnded(c: MediaController) {
        if (c.playbackState == Player.STATE_ENDED && c.hasNextMediaItem()) {
            c.seekToNextMediaItem()
            c.play()
        }
    }

    private suspend fun resolveMediaItem(track: Track): MediaItem {
        val uri = runCatching { repository.resolvePlayableUri(track) }.getOrDefault(track.uri)
        return track.toMediaItem(uri)
    }

    fun togglePlayPause() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun next() = controller?.seekToNextMediaItem()
    fun previous() = controller?.seekToPreviousMediaItem()
    fun seekTo(ms: Long) = controller?.seekTo(ms)

    fun toggleShuffle() {
        val c = controller ?: return
        c.shuffleModeEnabled = !c.shuffleModeEnabled
    }

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
            isBuffering = c.playbackState == Player.STATE_BUFFERING,
            shuffleEnabled = c.shuffleModeEnabled
        )
        maybeAutoQueue(c, track)
    }

    private fun maybeAutoQueue(c: MediaController, current: Track?) {
        if (!autoQueueEnabled || current == null) return
        if (current.id == lastAutoSeedId) return
        val remaining = c.mediaItemCount - c.currentMediaItemIndex - 1
        if (remaining > 1) return
        lastAutoSeedId = current.id
        autoQueueJob?.cancel()
        autoQueueJob = scope.launch { appendSimilar(current) }
    }

    private suspend fun appendSimilar(seed: Track) {
        val excluded = trackIndex.keys.toSet()
        val similar = runCatching {
            repository.similarTo(seed, excluded, limit = 5)
        }.getOrNull().orEmpty()
        if (similar.isEmpty()) return
        // Secuencial: paralelizar resolveStream dispara bot block en YouTube.
        for (t in similar) {
            val item = withContext(Dispatchers.IO) { resolveMediaItem(t) }
            trackIndex[t.id] = t
            controller?.addMediaItem(item) ?: return
        }
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
