package com.freespoty.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.freespoty.app.data.db.entities.Playlist
import com.freespoty.app.data.db.entities.Track
import com.freespoty.app.data.repository.MusicRepository
import com.freespoty.app.data.source.DiscoverPlaylist
import com.freespoty.app.player.PlayerController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(
    private val repository: MusicRepository,
    private val playerController: PlayerController
) : ViewModel() {

    private val allTracks: StateFlow<List<Track>> = repository.observeTracks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val recent: StateFlow<List<Track>> = allTracks
        .map { list -> list.sortedByDescending { it.addedAt }.take(12) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val playlists: StateFlow<List<Playlist>> = repository.observePlaylists()
        .map { it.filter { p -> !p.isPreview }.take(10) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _discover = MutableStateFlow<List<DiscoverPlaylist>>(emptyList())
    val discover: StateFlow<List<DiscoverPlaylist>> = _discover.asStateFlow()

    private var discoverSeedId: String? = null

    init {
        viewModelScope.launch { repository.scanLocalMusic() }
        viewModelScope.launch { repository.purgePreviewPlaylists() }

        // Trigger 1: currentTrack cambia → refetch.
        viewModelScope.launch {
            playerController.state
                .map { it.currentTrack }
                .distinctUntilChanged { old, new -> old?.id == new?.id }
                .collect { current ->
                    if (current == null) return@collect
                    refreshDiscover(current)
                }
        }

        // Trigger 2: bootstrap one-shot.
        viewModelScope.launch {
            val list = allTracks.first { it.isNotEmpty() }
            if (discoverSeedId != null) return@launch
            if (playerController.state.value.currentTrack != null) return@launch
            val seed = list.maxByOrNull { it.addedAt } ?: return@launch
            refreshDiscover(seed)
        }
    }

    private suspend fun refreshDiscover(seed: Track) {
        if (seed.id == discoverSeedId) return
        discoverSeedId = seed.id
        val results = runCatching {
            repository.discoverPlaylists(seed, limit = 8)
        }.getOrDefault(emptyList())
        _discover.value = results
    }

    fun rescan() {
        viewModelScope.launch { repository.scanLocalMusic() }
    }

    fun playRecent(seed: Track) {
        val list = recent.value
        val idx = list.indexOfFirst { it.id == seed.id }.coerceAtLeast(0)
        playerController.playTracks(list, idx)
    }

    /**
     * Importa playlist remota como preview, navega a detalle vía [onReady].
     * Si falla, [onReady] no se invoca.
     */
    fun openDiscover(item: DiscoverPlaylist, onReady: (Long) -> Unit) {
        viewModelScope.launch {
            val (id, tracks) = repository.importPreviewPlaylist(item) ?: return@launch
            playerController.playTracks(tracks, 0)
            onReady(id)
        }
    }

    class Factory(
        private val repository: MusicRepository,
        private val playerController: PlayerController
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HomeViewModel(repository, playerController) as T
        }
    }
}
