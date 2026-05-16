package com.freespoty.app.ui.screens.playlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.freespoty.app.data.db.entities.Playlist
import com.freespoty.app.data.repository.MusicRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PlaylistsViewModel(
    private val repository: MusicRepository
) : ViewModel() {

    val playlists: StateFlow<List<Playlist>> = repository.observePlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun create(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { repository.createPlaylist(name.trim()) }
    }

    fun delete(id: Long) {
        viewModelScope.launch { repository.deletePlaylist(id) }
    }

    class Factory(private val repository: MusicRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            PlaylistsViewModel(repository) as T
    }
}
