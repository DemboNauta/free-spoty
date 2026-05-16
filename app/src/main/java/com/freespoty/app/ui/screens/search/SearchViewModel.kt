package com.freespoty.app.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.freespoty.app.data.db.entities.Track
import com.freespoty.app.data.download.DownloadManager
import com.freespoty.app.data.repository.MusicRepository
import com.freespoty.app.data.source.YouTubeSource
import com.freespoty.app.player.PlayerController
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface SearchUiState {
    data object Idle : SearchUiState
    data object Loading : SearchUiState
    data class Results(val tracks: List<Track>) : SearchUiState
    data class Error(val message: String) : SearchUiState
}

class SearchViewModel(
    private val youtube: YouTubeSource,
    private val repository: MusicRepository,
    private val downloads: DownloadManager,
    private val player: PlayerController
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _state = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private var searchJob: Job? = null

    fun onQueryChange(value: String) {
        _query.value = value
    }

    fun submit() {
        val q = _query.value.trim()
        if (q.isEmpty()) {
            _state.value = SearchUiState.Idle
            return
        }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _state.value = SearchUiState.Loading
            try {
                val results = youtube.search(q)
                _state.value = if (results.isEmpty()) {
                    SearchUiState.Error("Sin resultados.")
                } else {
                    SearchUiState.Results(results)
                }
            } catch (t: Throwable) {
                _state.value = SearchUiState.Error(t.message ?: "Error de búsqueda")
            }
        }
    }

    fun play(track: Track, all: List<Track>) {
        viewModelScope.launch {
            repository.saveTracks(all)
            val index = all.indexOf(track).coerceAtLeast(0)
            player.playTracks(all, index)
        }
    }

    fun download(track: Track) {
        viewModelScope.launch {
            repository.saveTracks(listOf(track))
            downloads.enqueue(track)
        }
    }

    class Factory(
        private val youtube: YouTubeSource,
        private val repository: MusicRepository,
        private val downloads: DownloadManager,
        private val player: PlayerController
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SearchViewModel(youtube, repository, downloads, player) as T
    }
}
