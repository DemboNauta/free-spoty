package com.freespoty.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.freespoty.app.data.db.entities.Track
import com.freespoty.app.data.repository.MusicRepository
import com.freespoty.app.player.PlayerController
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(
    private val repository: MusicRepository,
    private val playerController: PlayerController
) : ViewModel() {

    val tracks: StateFlow<List<Track>> = repository.observeTracks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch { repository.scanLocalMusic() }
    }

    fun rescan() {
        viewModelScope.launch { repository.scanLocalMusic() }
    }

    fun playFromIndex(list: List<Track>, index: Int) {
        playerController.playTracks(list, index)
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
