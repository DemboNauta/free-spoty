package com.freespoty.app.di

import android.content.Context
import androidx.room.Room
import com.freespoty.app.data.db.AppDatabase
import com.freespoty.app.data.db.dao.DownloadDao
import com.freespoty.app.data.download.DownloadManager
import com.freespoty.app.data.importer.PlaylistImporter
import com.freespoty.app.data.importer.SpotifyPlaylistScraper
import com.freespoty.app.data.repository.MusicRepository
import com.freespoty.app.data.scanner.LocalMusicScanner
import com.freespoty.app.data.source.YouTubeSource
import com.freespoty.app.player.PlayerController

class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    private val database: AppDatabase = Room.databaseBuilder(
        appContext,
        AppDatabase::class.java,
        "freespoty.db"
    )
        .fallbackToDestructiveMigration()
        .build()

    private val scanner = LocalMusicScanner(appContext)

    val youtubeSource: YouTubeSource = YouTubeSource()

    val musicRepository: MusicRepository = MusicRepository(
        trackDao = database.trackDao(),
        playlistDao = database.playlistDao(),
        scanner = scanner,
        youtubeSource = youtubeSource
    )

    val downloadDao: DownloadDao = database.downloadDao()

    val downloadManager: DownloadManager = DownloadManager(appContext, downloadDao)

    val spotifyScraper: SpotifyPlaylistScraper = SpotifyPlaylistScraper()

    val playlistImporter: PlaylistImporter = PlaylistImporter(
        repository = musicRepository,
        youtube = youtubeSource,
        spotify = spotifyScraper
    )

    val playerController: PlayerController = PlayerController(appContext, musicRepository)
}
