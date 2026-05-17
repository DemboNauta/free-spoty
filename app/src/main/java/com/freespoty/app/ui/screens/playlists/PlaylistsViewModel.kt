package com.freespoty.app.ui.screens.playlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.freespoty.app.data.db.entities.Playlist
import com.freespoty.app.data.importer.ImportOutcome
import com.freespoty.app.data.importer.ImportProgress
import com.freespoty.app.data.importer.PlaylistImporter
import com.freespoty.app.data.repository.MusicRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface ImportUiState {
    data object Idle : ImportUiState
    data class Working(val message: String) : ImportUiState
    data class Success(val playlistId: Long, val count: Int) : ImportUiState
    data class Failure(val message: String) : ImportUiState
}

class PlaylistsViewModel(
    private val repository: MusicRepository,
    private val importer: PlaylistImporter
) : ViewModel() {

    val playlists: StateFlow<List<Playlist>> = repository.observePlaylists()
        .map { list -> list.filter { !it.isPreview } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _importState = MutableStateFlow<ImportUiState>(ImportUiState.Idle)
    val importState: StateFlow<ImportUiState> = _importState.asStateFlow()

    fun create(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { repository.createPlaylist(name.trim()) }
    }

    fun delete(id: Long) {
        viewModelScope.launch { repository.deletePlaylist(id) }
    }

    fun importFromUrl(url: String) {
        if (url.isBlank()) return
        viewModelScope.launch {
            _importState.value = ImportUiState.Working("Resolviendo URL…")
            val outcome = importer.import(url) { progress ->
                if (progress is ImportProgress.Working) {
                    val suffix = if (progress.total > 0) " (${progress.done}/${progress.total})" else ""
                    _importState.value = ImportUiState.Working(progress.message + suffix)
                }
            }
            _importState.value = when (outcome) {
                is ImportOutcome.Success -> ImportUiState.Success(outcome.playlistId, outcome.tracksAdded)
                is ImportOutcome.Failure -> ImportUiState.Failure(outcome.message)
            }
        }
    }

    fun resetImport() { _importState.value = ImportUiState.Idle }

    class Factory(
        private val repository: MusicRepository,
        private val importer: PlaylistImporter
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            PlaylistsViewModel(repository, importer) as T
    }
}
