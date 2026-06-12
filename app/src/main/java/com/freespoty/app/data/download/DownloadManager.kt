package com.freespoty.app.data.download

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import com.freespoty.app.data.db.dao.DownloadDao
import com.freespoty.app.data.db.entities.DownloadEntry
import com.freespoty.app.data.db.entities.DownloadStatus
import com.freespoty.app.data.db.entities.Track
import com.freespoty.app.data.repository.MusicRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class DownloadManager(
    private val context: Context,
    private val downloadDao: DownloadDao,
    private val repository: MusicRepository
) {
    private val workManager get() = WorkManager.getInstance(context)

    suspend fun enqueue(track: Track) {
        downloadDao.upsert(
            DownloadEntry(
                trackId = track.id,
                title = track.title,
                artist = track.artist,
                artworkUri = track.artworkUri,
                remoteId = track.remoteId,
                localPath = null,
                status = DownloadStatus.QUEUED,
                progress = 0
            )
        )
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .setInputData(Data.Builder().putString(DownloadWorker.KEY_TRACK_ID, track.id).build())
            .addTag(track.id)
            .build()

        // Serialise downloads behind a single queue: YouTube's anti-bot trips when many
        // stream resolutions hit it in parallel. APPEND_OR_REPLACE chains the new request
        // after any pending one under the same unique name, so workers run one at a time.
        workManager.enqueueUniqueWork(
            DOWNLOAD_QUEUE_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request
        )
    }

    suspend fun enqueueAll(tracks: List<Track>) {
        tracks.forEach { enqueue(it) }
    }

    suspend fun cancel(trackId: String) {
        workManager.cancelAllWorkByTag(trackId)
        downloadDao.deleteById(trackId)
    }

    suspend fun delete(trackId: String) {
        val entry = downloadDao.findById(trackId)
        withContext(Dispatchers.IO) {
            entry?.localPath?.let { File(it).delete() }
        }
        downloadDao.deleteById(trackId)
        // El track sigue en playlists: devolverlo a streaming para que siga sonando.
        repository.revertTrackToRemote(trackId, entry?.remoteId)
    }

    private companion object {
        const val DOWNLOAD_QUEUE_NAME = "freespoty-download-queue"
    }
}
