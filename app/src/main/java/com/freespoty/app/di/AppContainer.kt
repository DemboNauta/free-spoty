package com.freespoty.app.di

import android.content.Context
import androidx.room.Room
import com.freespoty.app.data.db.AppDatabase
import com.freespoty.app.data.db.dao.DownloadDao
import com.freespoty.app.data.download.DownloadManager
import com.freespoty.app.data.importer.PlaylistImporter
import com.freespoty.app.data.importer.SpotifyPlaylistScraper
import com.freespoty.app.data.preferences.AppPreferences
import com.freespoty.app.data.recommendation.RecommendationEngine
import com.freespoty.app.data.repository.MusicRepository
import com.freespoty.app.data.scanner.LocalMusicScanner
import com.freespoty.app.data.source.YouTubeSource
import com.freespoty.app.network.NewPipeDownloader
import com.freespoty.app.player.PlayerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val appPreferences: AppPreferences = AppPreferences(appContext)

    private val database: AppDatabase = Room.databaseBuilder(
        appContext,
        AppDatabase::class.java,
        "freespoty.db"
    )
        .fallbackToDestructiveMigration()
        .build()

    private val scanner = LocalMusicScanner(appContext)

    val youtubeSource: YouTubeSource = YouTubeSource()

    val recommendationEngine: RecommendationEngine = RecommendationEngine(youtubeSource)

    val musicRepository: MusicRepository = MusicRepository(
        trackDao = database.trackDao(),
        playlistDao = database.playlistDao(),
        scanner = scanner,
        youtubeSource = youtubeSource,
        recommender = recommendationEngine
    )

    val downloadDao: DownloadDao = database.downloadDao()

    val downloadManager: DownloadManager = DownloadManager(appContext, downloadDao)

    val spotifyScraper: SpotifyPlaylistScraper = SpotifyPlaylistScraper()

    val playlistImporter: PlaylistImporter = PlaylistImporter(
        repository = musicRepository,
        youtube = youtubeSource,
        spotify = spotifyScraper,
        downloads = downloadManager,
        scope = appScope
    )

    val playerController: PlayerController = PlayerController(appContext, musicRepository)

    init {
        appScope.launch {
            appPreferences.kidsModeFlow.collect { enabled ->
                NewPipeDownloader.restrictedMode = enabled
            }
        }
    }
}
