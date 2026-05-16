package com.freespoty.app.ui.screens.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.freespoty.app.data.db.entities.Playlist
import com.freespoty.app.data.db.entities.Track
import com.freespoty.app.ui.components.TrackItem
import com.freespoty.app.ui.rememberAppContainer
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen() {
    val container = rememberAppContainer()
    val vm: SearchViewModel = viewModel(
        factory = SearchViewModel.Factory(
            youtube = container.youtubeSource,
            repository = container.musicRepository,
            downloads = container.downloadManager,
            player = container.playerController
        )
    )
    val query by vm.query.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val playerState by container.playerController.state.collectAsStateWithLifecycle()

    var addToPlaylistFor by remember { mutableStateOf<Track?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Buscar en YouTube") })

        OutlinedTextField(
            value = query,
            onValueChange = vm::onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("Canción, artista, álbum…") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { vm.submit() })
        )
        HorizontalDivider()

        Box(modifier = Modifier.fillMaxSize()) {
            when (val s = state) {
                SearchUiState.Idle -> EmptyHint("Escribe y pulsa buscar.")
                SearchUiState.Loading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
                is SearchUiState.Error -> EmptyHint(s.message)
                is SearchUiState.Results -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(s.tracks, key = { it.id }) { track ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            TrackItem(
                                track = track,
                                isPlaying = playerState.currentTrack?.id == track.id,
                                onClick = { vm.play(track, s.tracks) },
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { addToPlaylistFor = track }) {
                                Icon(Icons.Outlined.PlaylistAdd, contentDescription = "Añadir a playlist")
                            }
                            IconButton(onClick = { vm.download(track) }) {
                                Icon(Icons.Outlined.Download, contentDescription = "Descargar")
                            }
                        }
                    }
                }
            }
        }
    }

    addToPlaylistFor?.let { track ->
        AddToPlaylistDialog(
            track = track,
            onDismiss = { addToPlaylistFor = null }
        )
    }
}

@Composable
private fun EmptyHint(text: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AddToPlaylistDialog(track: Track, onDismiss: () -> Unit) {
    val container = rememberAppContainer()
    val scope = rememberCoroutineScope()
    val playlists by container.musicRepository.observePlaylists()
        .collectAsStateWithLifecycle(initialValue = emptyList<Playlist>())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Añadir a playlist") },
        text = {
            if (playlists.isEmpty()) {
                Text("Aún no tienes playlists. Créala primero desde la pestaña Playlists.")
            } else {
                LazyColumn {
                    items(playlists, key = { it.id }) { pl ->
                        TextButton(
                            onClick = {
                                scope.launch {
                                    container.musicRepository.saveTracks(listOf(track))
                                    container.musicRepository.addTrackToPlaylist(pl.id, track.id)
                                    onDismiss()
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(pl.name, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } }
    )
}

