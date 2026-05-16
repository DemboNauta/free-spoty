package com.freespoty.app.di

import android.content.Context
import androidx.room.Room
import com.freespoty.app.data.db.AppDatabase
import com.freespoty.app.data.repository.MusicRepository
import com.freespoty.app.data.scanner.LocalMusicScanner
import com.freespoty.app.player.PlayerController

class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    private val database: AppDatabase = Room.databaseBuilder(
        appContext,
        AppDatabase::class.java,
        "freespoty.db"
    ).build()

    private val scanner = LocalMusicScanner(appContext)

    val musicRepository: MusicRepository = MusicRepository(
        trackDao = database.trackDao(),
        playlistDao = database.playlistDao(),
        scanner = scanner
    )

    val playerController: PlayerController = PlayerController(appContext)
}
