package com.freespoty.app.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.MusicNote
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.freespoty.app.R
import com.freespoty.app.data.db.entities.Playlist
import com.freespoty.app.data.db.entities.Track
import com.freespoty.app.data.source.DiscoverPlaylist
import com.freespoty.app.ui.rememberAppContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onTrackClick: () -> Unit,
    onOpenPlaylist: (Long) -> Unit
) {
    val container = rememberAppContainer()
    val vm: HomeViewModel = viewModel(
        factory = HomeViewModel.Factory(container.musicRepository, container.playerController)
    )
    val recent by vm.recent.collectAsStateWithLifecycle()
    val discover by vm.discover.collectAsStateWithLifecycle()
    val playlists by vm.playlists.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.app_name)) },
            actions = {
                IconButton(onClick = vm::rescan) {
                    Icon(Icons.Outlined.Refresh, contentDescription = stringResource(R.string.action_rescan))
                }
            }
        )
        if (recent.isEmpty() && discover.isEmpty() && playlists.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(stringResource(R.string.empty_library_title), style = MaterialTheme.typography.titleLarge)
                    Text(
                        stringResource(R.string.empty_library_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                item("playlists-header") { SectionHeader(stringResource(R.string.home_playlists_section)) }
                item("playlists-row") {
                    if (playlists.isEmpty()) {
                        Text(
                            text = stringResource(R.string.home_no_playlists),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    } else {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(playlists, key = { it.id }) { pl ->
                                PlaylistCard(pl) { onOpenPlaylist(pl.id) }
                            }
                        }
                    }
                }

                if (discover.isNotEmpty()) {
                    item("discover-header") { SectionHeader(stringResource(R.string.discover_section)) }
                    item("discover-row") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(discover, key = { it.url }) { disc ->
                                DiscoverPlaylistCard(disc) {
                                    vm.openDiscover(disc) { id -> onOpenPlaylist(id) }
                                }
                            }
                        }
                    }
                }

                if (recent.isNotEmpty()) {
                    item("recent-header") { SectionHeader(stringResource(R.string.home_recent_section)) }
                    item("recent-row") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(recent, key = { it.id }) { track ->
                                TrackCard(track) { vm.playRecent(track); onTrackClick() }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun PlaylistCard(playlist: Playlist, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(132.dp)
            .clickable(onClick = onClick)
            .padding(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(124.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (!playlist.artworkUri.isNullOrBlank()) {
                AsyncImage(
                    model = playlist.artworkUri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.LibraryMusic,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            text = playlist.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp)
        )
        playlist.importedFrom?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DiscoverPlaylistCard(disc: DiscoverPlaylist, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(148.dp)
            .clickable(onClick = onClick)
            .padding(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(140.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (!disc.thumbnailUrl.isNullOrBlank()) {
                AsyncImage(
                    model = disc.thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.LibraryMusic,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            text = disc.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp)
        )
        Text(
            text = listOfNotNull(
                disc.uploader,
                if (disc.streamCount > 0) "${disc.streamCount} pistas" else null
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun TrackCard(track: Track, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(132.dp)
            .clickable(onClick = onClick)
            .padding(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(124.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (!track.artworkUri.isNullOrBlank()) {
                AsyncImage(
                    model = track.artworkUri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            text = track.title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp)
        )
        Text(
            text = track.artist ?: stringResource(R.string.unknown_artist),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
