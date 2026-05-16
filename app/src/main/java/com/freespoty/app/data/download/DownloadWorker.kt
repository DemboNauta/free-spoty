package com.freespoty.app.data.download

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.freespoty.app.FreeSpotyApp
import com.freespoty.app.data.db.entities.DownloadStatus
import com.freespoty.app.data.db.entities.TrackSource
import com.freespoty.app.data.source.YouTubeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Downloads a single track to app-private storage. Re-runs from scratch on retry so
 * partial files are overwritten safely. The track id and stream URL are passed in via
 * the worker [inputData].
 */
class DownloadWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val trackId = inputData.getString(KEY_TRACK_ID) ?: return@withContext Result.failure()
        val app = applicationContext as FreeSpotyApp
        val repo = app.container.musicRepository
        val downloadDao = app.container.downloadDao
        val youtube = app.container.youtubeSource

        val track = repo.track(trackId)
            ?: return@withContext failWith(downloadDao, trackId, "Pista no encontrada")

        downloadDao.updateProgress(trackId, DownloadStatus.RUNNING, 0)

        try {
            val streamUrl = when (track.source) {
                TrackSource.REMOTE -> {
                    val remoteId = track.remoteId
                        ?: return@withContext failWith(downloadDao, trackId, "Sin ID remoto")
                    YouTubeSource.ensureInitialized()
                    youtube.resolveStream(remoteId).audioUrl
                }
                TrackSource.LOCAL, TrackSource.DOWNLOADED ->
                    return@withContext failWith(downloadDao, trackId, "La pista ya es local")
            }

            val targetDir = File(applicationContext.filesDir, "downloads").apply { mkdirs() }
            val targetFile = File(targetDir, "$trackId.m4a")
            val tmpFile = File(targetDir, "$trackId.part")

            val client = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build()
            val request = Request.Builder().url(streamUrl).build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext failWith(downloadDao, trackId, "HTTP ${response.code}")
                }
                val body = response.body ?: return@withContext failWith(downloadDao, trackId, "Cuerpo vacío")
                val totalBytes = body.contentLength()
                body.byteStream().use { input ->
                    tmpFile.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var read: Int
                        var downloaded = 0L
                        var lastReportedProgress = 0
                        while (input.read(buffer).also { read = it } != -1) {
                            if (isStopped) {
                                tmpFile.delete()
                                downloadDao.markFailed(trackId, error = "Cancelada")
                                return@withContext Result.failure()
                            }
                            output.write(buffer, 0, read)
                            downloaded += read
                            if (totalBytes > 0) {
                                val pct = ((downloaded * 100) / totalBytes).toInt().coerceIn(0, 99)
                                if (pct - lastReportedProgress >= 5) {
                                    downloadDao.updateProgress(trackId, DownloadStatus.RUNNING, pct)
                                    lastReportedProgress = pct
                                }
                            }
                        }
                    }
                }
            }

            if (!tmpFile.renameTo(targetFile)) {
                tmpFile.delete()
                return@withContext failWith(downloadDao, trackId, "Fallo al guardar el archivo")
            }

            // Update the Track row so it points at the local file, and the player will
            // use it transparently from now on.
            repo.markTrackDownloaded(trackId, targetFile.absolutePath)
            downloadDao.markCompleted(trackId, targetFile.absolutePath)
            Result.success()
        } catch (t: Throwable) {
            failWith(downloadDao, trackId, t.message ?: "Error desconocido")
        }
    }

    private suspend fun failWith(
        dao: com.freespoty.app.data.db.dao.DownloadDao,
        trackId: String,
        message: String
    ): Result {
        dao.markFailed(trackId, error = message)
        return Result.failure()
    }

    companion object {
        const val KEY_TRACK_ID = "track_id"
        const val UNIQUE_WORK_PREFIX = "download-"
    }
}
