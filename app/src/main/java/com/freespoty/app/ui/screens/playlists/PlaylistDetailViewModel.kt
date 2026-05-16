package com.freespoty.app.ui.screens.playlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.freespoty.app.data.db.entities.Track
import com.freespoty.app.data.repository.MusicRepository
import com.freespoty.app.player.PlayerController
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class PlaylistDetailViewModel(
    private val playlistId: Long,
    private val repository: MusicRepository,
    private val playerController: PlayerController
) : ViewModel() {

    val tracks: StateFlow<List<Track>> = repository.observePlaylistTracks(playlistId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun playAll() {
        val list = tracks.value
        if (list.isNotEmpty()) playerController.playTracks(list, 0)
    }

    fun playFrom(index: Int) {
        val list = tracks.value
        if (list.isNotEmpty()) playerController.playTracks(list, index)
    }

    class Factory(
        private val playlistId: Long,
        private val repository: MusicRepository,
        private val playerController: PlayerController
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            PlaylistDetailViewModel(playlistId, repository, playerController) as T
    }
}
