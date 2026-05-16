package com.freespoty.app.data.download

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.freespoty.app.data.db.dao.DownloadDao
import com.freespoty.app.data.db.entities.DownloadEntry
import com.freespoty.app.data.db.entities.DownloadStatus
import com.freespoty.app.data.db.entities.Track
import java.io.File

class DownloadManager(
    private val context: Context,
    private val downloadDao: DownloadDao
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
            .setInputData(Data.Builder().putString(DownloadWorker.KEY_TRACK_ID, track.id).build())
            .addTag(track.id)
            .build()

        workManager.enqueueUniqueWork(
            DownloadWorker.UNIQUE_WORK_PREFIX + track.id,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    suspend fun cancel(trackId: String) {
        workManager.cancelUniqueWork(DownloadWorker.UNIQUE_WORK_PREFIX + trackId)
        downloadDao.deleteById(trackId)
    }

    suspend fun delete(trackId: String) {
        downloadDao.findById(trackId)?.localPath?.let { File(it).delete() }
        downloadDao.deleteById(trackId)
    }
}
