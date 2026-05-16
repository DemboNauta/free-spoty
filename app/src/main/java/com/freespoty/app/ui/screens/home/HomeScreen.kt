package com.freespoty.app.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.freespoty.app.ui.components.TrackItem
import com.freespoty.app.ui.rememberAppContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onTrackClick: () -> Unit
) {
    val container = rememberAppContainer()
    val vm: HomeViewModel = viewModel(
        factory = HomeViewModel.Factory(container.musicRepository, container.playerController)
    )
    val tracks by vm.tracks.collectAsStateWithLifecycle()
    val playerState by container.playerController.state.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Tu música") },
            actions = {
                IconButton(onClick = vm::rescan) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "Re-escanear")
                }
            }
        )
        if (tracks.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("No hay canciones todavía", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Concede permisos de audio o pulsa actualizar.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(top = 4.dp)) {
                items(tracks, key = { it.id }) { track ->
                    TrackItem(
                        track = track,
                        isPlaying = playerState.currentTrack?.id == track.id,
                        onClick = {
                            val index = tracks.indexOf(track).coerceAtLeast(0)
                            vm.playFromIndex(tracks, index)
                            onTrackClick()
                        }
                    )
                }
            }
        }
    }
}
