package com.freespoty.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class TopLevelDestination(val route: String, val label: String, val icon: ImageVector) {
    data object Home : TopLevelDestination("home", "Inicio", Icons.Outlined.Home)
    data object Playlists : TopLevelDestination("playlists", "Playlists", Icons.Outlined.LibraryMusic)
    data object Search : TopLevelDestination("search", "Buscar", Icons.Outlined.Search)
    data object Downloads : TopLevelDestination("downloads", "Descargas", Icons.Outlined.Download)
    data object Settings : TopLevelDestination("settings", "Ajustes", Icons.Outlined.Settings)
}

val topLevelDestinations = listOf(
    TopLevelDestination.Home,
    TopLevelDestination.Playlists,
    TopLevelDestination.Search,
    TopLevelDestination.Downloads,
    TopLevelDestination.Settings
)

object Routes {
    const val PLAYER = "player"
    const val PLAYLIST_DETAIL = "playlist/{playlistId}"
    fun playlistDetail(id: Long) = "playlist/$id"
}
