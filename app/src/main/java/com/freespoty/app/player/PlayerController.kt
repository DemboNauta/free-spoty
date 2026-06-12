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

enum class RepeatMode { OFF, ALL, ONE }

data class PlayerUiState(
    val isPlaying: Boolean = false,
    val currentTrack: Track? = null,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
    val isBuffering: Boolean = false,
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val autoplayEnabled: Boolean = false,
    val errorMessage: String? = null
)

/**
 * Thin wrapper around a [MediaController] that resolves [Track]s to media items and exposes
 * a Compose-friendly [StateFlow]. Remote tracks require an async stream resolution step
 * (handled here via [MusicRepository.resolvePlayableUri]) before being passed to ExoPlayer.
 *
 * Shuffle y repeat usan los modos nativos de ExoPlayer (shuffleModeEnabled / repeatMode):
 * con REPEAT_MODE_ALL/ONE el player nunca entra en STATE_ENDED, así el reinicio de playlist
 * es gapless y no depende de re-resolver URLs (frágil con bot block).
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

    private var originalTracks = listOf<Track>()
    private var endedHandled = false
    private var suggestionArtistIndex = 0
    private val _autoplay = MutableStateFlow(false)
    // mediaId → ya reintentado con URL fresca tras error. Evita loop infinito de re-resolución.
    private val errorRetriedIds = mutableSetOf<String>()
    private var errorRecoveryJob: Job? = null

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            refreshFromPlayer()
        }

        // Si un item falla (URL stale por loop largo, bot block, 403…): primero intentar
        // re-resolver una URL fresca y reemplazar el item in-place; si ya se reintentó,
        // saltar al siguiente. Nunca pausar.
        // NO manejamos STATE_ENDED aquí: ExoPlayer transiciona auto al siguiente cuando hay,
        // y un seekToNextMediaItem extra desincroniza notification/player.
        override fun onPlayerError(error: PlaybackException) {
            val c = controller ?: return
            val failedId = c.currentMediaItem?.mediaId
            val track = failedId?.let { trackIndex[it] }
            if (track != null && errorRetriedIds.add(failedId)) {
                errorRecoveryJob?.cancel()
                errorRecoveryJob = scope.launch {
                    val fresh = withContext(Dispatchers.IO) { resolveMediaItem(track) }
                    val ctrl = controller ?: return@launch
                    val idx = ctrl.currentMediaItemIndex
                    if (ctrl.currentMediaItem?.mediaId == failedId) {
                        ctrl.replaceMediaItem(idx, fresh)
                        ctrl.prepare()
                        ctrl.play()
                    }
                }
            } else if (c.hasNextMediaItem()) {
                c.seekToNextMediaItem()
                c.prepare()
                c.play()
            } else {
                _state.value = _state.value.copy(
                    errorMessage = "No se pudo reproducir: ${error.message}"
                )
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            // Transición sana → el item anterior ya no necesita su marca de retry.
            errorRetriedIds.clear()
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
     * Replaces the current queue and starts at [startIndex]. Saves [tracks] as the canonical
     * playlist used as seed for autoplay suggestions.
     */
    fun playTracks(tracks: List<Track>, startIndex: Int = 0) {
        originalTracks = tracks
        startPlayback(tracks, startIndex)
    }

    private fun startPlayback(tracks: List<Track>, startIndex: Int) {
        if (tracks.isEmpty()) return
        val safeStart = startIndex.coerceIn(0, tracks.lastIndex)
        playJob?.cancel()
        autoQueueJob?.cancel()
        lastAutoSeedId = null
        endedHandled = false
        suggestionArtistIndex = 0
        errorRetriedIds.clear()
        trackIndex.clear()
        tracks.forEach { trackIndex[it.id] = it }
        playJob = scope.launch {
            _state.value = _state.value.copy(isBuffering = true, errorMessage = null)
            try {
                // Resolver SOLO current antes de play → arranque inmediato.
                // Resto se resuelve en bg y se append al timeline.
                val firstTrack = tracks[safeStart]
                val firstItem = withContext(Dispatchers.IO) { resolveMediaItem(firstTrack) }
                val c = controller ?: return@launch
                c.setMediaItems(listOf(firstItem), 0, 0L)
                c.prepare()
                c.playWhenReady = true

                val after = tracks.subList(safeStart + 1, tracks.size)
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
        when {
            c.isPlaying -> c.pause()
            // Cola terminada: play() solo no rearranca desde STATE_ENDED → volver al inicio.
            c.playbackState == Player.STATE_ENDED -> {
                c.seekTo(0, 0L)
                c.prepare()
                c.play()
            }
            else -> c.play()
        }
    }

    fun next() = controller?.seekToNextMediaItem()
    fun previous() = controller?.seekToPreviousMediaItem()
    fun seekTo(ms: Long) = controller?.seekTo(ms)

    fun toggleShuffle() {
        val c = controller ?: return
        c.shuffleModeEnabled = !c.shuffleModeEnabled
        refreshFromPlayer()
    }

    fun cycleRepeatMode() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        refreshFromPlayer()
    }

    fun toggleAutoplay() {
        _autoplay.value = !_autoplay.value
        val c = controller
        if (_autoplay.value && c != null) {
            // Si ya estamos al final, lanzar búsqueda inmediata.
            maybeAutoQueue(c, _state.value.currentTrack)
            if (c.playbackState == Player.STATE_ENDED && !c.hasNextMediaItem()) {
                autoQueueJob?.cancel()
                autoQueueJob = scope.launch { appendSimilarFromPlaylist() }
            }
        }
        refreshFromPlayer()
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
            shuffleEnabled = c.shuffleModeEnabled,
            repeatMode = when (c.repeatMode) {
                Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                Player.REPEAT_MODE_ONE -> RepeatMode.ONE
                else -> RepeatMode.OFF
            },
            autoplayEnabled = _autoplay.value
        )

        // Fin de cola real (solo posible con repeat OFF): si autoplay activo, buscar similares.
        // endedHandled evita relanzar en cada onEvents mientras seguimos en STATE_ENDED;
        // appendSimilarFromPlaylist ya reintenta con varios artistas internamente.
        if (c.playbackState == Player.STATE_ENDED && !c.hasNextMediaItem()) {
            if (!endedHandled && _autoplay.value) {
                endedHandled = true
                lastAutoSeedId = track?.id
                autoQueueJob?.cancel()
                autoQueueJob = scope.launch { appendSimilarFromPlaylist() }
            }
        } else if (c.playbackState != Player.STATE_ENDED) {
            endedHandled = false
        }

        maybeAutoQueue(c, track)
    }

    private fun maybeAutoQueue(c: MediaController, current: Track?) {
        if (!_autoplay.value) return
        if (current == null) return
        if (current.id == lastAutoSeedId) return
        val remaining = c.mediaItemCount - c.currentMediaItemIndex - 1
        if (remaining > 1) return
        lastAutoSeedId = current.id
        autoQueueJob?.cancel()
        autoQueueJob = scope.launch { appendSimilarFromPlaylist() }
    }

    // Rota por cada artista distinto de la playlist original en orden circular. Si un artista
    // no da resultados (red caída, bot block, sin similares) prueba con los siguientes antes
    // de rendirse, para que el fin de cola no se quede mudo por un fallo puntual.
    private suspend fun appendSimilarFromPlaylist() {
        val artists = originalTracks.mapNotNull { it.artist?.trim()?.takeIf { s -> s.isNotEmpty() } }.distinct()
        if (artists.isEmpty()) return
        val attempts = minOf(artists.size, 3)
        var similar = emptyList<Track>()
        for (attempt in 0 until attempts) {
            val excluded = trackIndex.keys.toSet()
            val artist = artists[suggestionArtistIndex % artists.size]
            suggestionArtistIndex++
            val seed = originalTracks.firstOrNull { it.artist?.trim() == artist } ?: continue
            similar = runCatching {
                repository.similarTo(seed, excluded, limit = 5)
            }.getOrNull().orEmpty()
            if (similar.isNotEmpty()) break
        }
        if (similar.isEmpty()) {
            // Permitir nuevo intento en el próximo evento de fin de cola.
            endedHandled = false
            return
        }
        for (t in similar) {
            val item = withContext(Dispatchers.IO) { resolveMediaItem(t) }
            trackIndex[t.id] = t
            val ctrl = controller ?: return
            ctrl.addMediaItem(item)
            rescueIfEnded(ctrl)
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
