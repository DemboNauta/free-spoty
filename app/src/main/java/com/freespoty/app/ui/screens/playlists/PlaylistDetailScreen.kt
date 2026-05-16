package com.freespoty.app.ui.screens.playlists

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.freespoty.app.data.db.entities.Playlist
import com.freespoty.app.ui.components.TrackItem
import com.freespoty.app.ui.rememberAppContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    playlistId: Long,
    onBack: () -> Unit,
    onOpenPlayer: () -> Unit
) {
    val container = rememberAppContainer()
    val vm: PlaylistDetailViewModel = viewModel(
        factory = PlaylistDetailViewModel.Factory(
            playlistId = playlistId,
            repository = container.musicRepository,
            playerController = container.playerController
        )
    )

    val tracks by vm.tracks.collectAsStateWithLifecycle()
    val playerState by container.playerController.state.collectAsStateWithLifecycle()
    var playlist by remember { mutableStateOf<Playlist?>(null) }

    LaunchedEffect(playlistId) { playlist = container.musicRepository.playlist(playlistId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(playlist?.name ?: "Playlist") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                    }
                }
            )
        },
        floatingActionButton = {
            if (tracks.isNotEmpty()) {
                FloatingActionButton(onClick = {
                    vm.playAll()
                    onOpenPlayer()
                }) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "Reproducir")
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (tracks.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Playlist vacía", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Añade canciones desde la pantalla de inicio.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(tracks, key = { it.id }) { track ->
                        TrackItem(
                            track = track,
                            isPlaying = playerState.currentTrack?.id == track.id,
                            onClick = { vm.playFrom(tracks.indexOf(track).coerceAtLeast(0)) }
                        )
                    }
                }
            }
        }
    }
}
