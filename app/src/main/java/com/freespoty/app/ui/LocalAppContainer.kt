package com.freespoty.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.freespoty.app.FreeSpotyApp
import com.freespoty.app.di.AppContainer

@Composable
fun rememberAppContainer(): AppContainer {
    val ctx = LocalContext.current.applicationContext as FreeSpotyApp
    return ctx.container
}
