package com.freespoty.app

import android.app.Application
import com.freespoty.app.di.AppContainer

class FreeSpotyApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.playerController.connect()
    }
}
