package com.freespoty.app.ui.screens.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.freespoty.app.data.db.dao.DownloadDao
import com.freespoty.app.data.db.entities.DownloadEntry
import com.freespoty.app.data.download.DownloadManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DownloadsViewModel(
    downloadDao: DownloadDao,
    private val downloadManager: DownloadManager
) : ViewModel() {

    val downloads: StateFlow<List<DownloadEntry>> = downloadDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun cancel(trackId: String) {
        viewModelScope.launch { downloadManager.cancel(trackId) }
    }

    fun delete(trackId: String) {
        viewModelScope.launch { downloadManager.delete(trackId) }
    }

    class Factory(
        private val downloadDao: DownloadDao,
        private val downloadManager: DownloadManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            DownloadsViewModel(downloadDao, downloadManager) as T
    }
}
