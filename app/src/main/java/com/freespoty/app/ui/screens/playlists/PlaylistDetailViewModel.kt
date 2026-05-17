package com.freespoty.app.ui.screens.playlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.freespoty.app.data.db.dao.DownloadDao
import com.freespoty.app.data.db.entities.DownloadStatus
import com.freespoty.app.data.db.entities.Track
import com.freespoty.app.data.db.entities.TrackSource
import com.freespoty.app.data.download.DownloadManager
import com.freespoty.app.data.repository.MusicRepository
import com.freespoty.app.player.PlayerController
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class DownloadInfo(val status: DownloadStatus, val progress: Int)

class PlaylistDetailViewModel(
    private val playlistId: Long,
    private val repository: MusicRepository,
    private val playerController: PlayerController,
    private val downloadDao: DownloadDao,
    private val downloadManager: DownloadManager
) : ViewModel() {

    val tracks: StateFlow<List<Track>> = repository.observePlaylistTracks(playlistId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val downloads: StateFlow<Map<String, DownloadInfo>> = downloadDao.observeAll()
        .map { list -> list.associate { it.trackId to DownloadInfo(it.status, it.progress) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    fun shufflePlay() {
        val list = tracks.value
        if (list.isEmpty()) return
        val shuffled = list.shuffled()
        playerController.playTracks(shuffled, 0)
    }

    fun downloadAll() {
        val current = tracks.value
        val downloadMap = downloads.value
        val pending = current.filter { t ->
            t.source == TrackSource.REMOTE &&
                downloadMap[t.id]?.status != DownloadStatus.COMPLETED &&
                downloadMap[t.id]?.status != DownloadStatus.RUNNING &&
                downloadMap[t.id]?.status != DownloadStatus.QUEUED
        }
        if (pending.isEmpty()) return
        viewModelScope.launch { downloadManager.enqueueAll(pending) }
    }

    fun playAll() {
        val list = tracks.value
        if (list.isNotEmpty()) playerController.playTracks(list, 0)
    }

    fun playFrom(index: Int) {
        val list = tracks.value
        if (list.isNotEmpty()) playerController.playTracks(list, index)
    }

    fun markSaved(onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repository.markPlaylistSaved(playlistId)
            onDone()
        }
    }

    class Factory(
        private val playlistId: Long,
        private val repository: MusicRepository,
        private val playerController: PlayerController,
        private val downloadDao: DownloadDao,
        private val downloadManager: DownloadManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            PlaylistDetailViewModel(playlistId, repository, playerController, downloadDao, downloadManager) as T
    }
}
