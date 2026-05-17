package com.freespoty.app.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.freespoty.app.ui.components.MiniPlayer
import com.freespoty.app.ui.screens.downloads.DownloadsScreen
import com.freespoty.app.ui.screens.home.HomeScreen
import com.freespoty.app.ui.screens.player.PlayerScreen
import com.freespoty.app.ui.screens.playlists.PlaylistDetailScreen
import com.freespoty.app.ui.screens.playlists.PlaylistsScreen
import com.freespoty.app.ui.screens.search.SearchScreen

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    val isTopLevel = currentRoute in topLevelDestinations.map { it.route }
    val showMiniPlayer = currentRoute != Routes.PLAYER

    Scaffold(
        bottomBar = {
            Column {
                if (showMiniPlayer) {
                    MiniPlayer(onExpand = { navController.navigate(Routes.PLAYER) })
                }
                if (isTopLevel) {
                    NavigationBar {
                        topLevelDestinations.forEach { dest ->
                            NavigationBarItem(
                                selected = currentRoute == dest.route,
                                onClick = {
                                    if (currentRoute != dest.route) {
                                        navController.navigate(dest.route) {
                                            popUpTo(TopLevelDestination.Home.route) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                },
                                icon = { Icon(dest.icon, contentDescription = dest.label) },
                                label = { Text(dest.label) }
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            AppNavHost(navController = navController)
        }
    }
}

@Composable
private fun AppNavHost(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = TopLevelDestination.Home.route
    ) {
        composable(TopLevelDestination.Home.route) {
            HomeScreen(
                onTrackClick = { /* play handled inside */ },
                onOpenPlaylist = { id -> navController.navigate(Routes.playlistDetail(id)) }
            )
        }
        composable(TopLevelDestination.Playlists.route) {
            PlaylistsScreen(
                onOpenPlaylist = { id -> navController.navigate(Routes.playlistDetail(id)) }
            )
        }
        composable(TopLevelDestination.Search.route) {
            SearchScreen()
        }
        composable(TopLevelDestination.Downloads.route) {
            DownloadsScreen()
        }
        composable(
            route = Routes.PLAYLIST_DETAIL,
            arguments = listOf(navArgument("playlistId") { type = NavType.LongType })
        ) { entry ->
            val id = entry.arguments?.getLong("playlistId") ?: return@composable
            PlaylistDetailScreen(
                playlistId = id,
                onBack = { navController.popBackStack() },
                onOpenPlayer = { navController.navigate(Routes.PLAYER) }
            )
        }
        composable(Routes.PLAYER) {
            PlayerScreen(onBack = { navController.popBackStack() })
        }
    }
}
