package com.freespoty.app.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.freespoty.app.data.db.entities.DownloadStatus
import com.freespoty.app.data.db.entities.TrackSource
import com.freespoty.app.player.RepeatMode
import com.freespoty.app.ui.rememberAppContainer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(onBack: () -> Unit) {
    val container = rememberAppContainer()
    val controller = container.playerController
    val state by controller.state.collectAsStateWithLifecycle()
    val track = state.currentTrack
    val coroutineScope = rememberCoroutineScope()

    var position by remember(track?.id) { mutableLongStateOf(state.positionMs) }
    var seeking by remember { mutableStateOf(false) }
    LaunchedEffect(state.isPlaying, track?.id) {
        position = controller.currentPositionMs()
        while (state.isPlaying && !seeking) {
            delay(500)
            position = controller.currentPositionMs()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Reproduciendo") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cerrar")
                }
            }
        )
        if (track == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Nada reproduciéndose")
            }
            return@Column
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 24.dp)
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (!track.artworkUri.isNullOrBlank()) {
                    AsyncImage(
                        model = track.artworkUri,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        Icons.Outlined.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(96.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    track.title,
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center
                )
                Text(
                    track.artist ?: "Artista desconocido",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            val duration = state.durationMs.coerceAtLeast(1L)
            Slider(
                value = position.toFloat().coerceIn(0f, duration.toFloat()),
                onValueChange = { seeking = true; position = it.toLong() },
                onValueChangeFinished = {
                    controller.seekTo(position)
                    seeking = false
                },
                valueRange = 0f..duration.toFloat()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(formatTime(position), style = MaterialTheme.typography.bodySmall)
                Text(formatTime(state.durationMs), style = MaterialTheme.typography.bodySmall)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { controller.toggleShuffle() }) {
                    Icon(
                        Icons.Filled.Shuffle,
                        contentDescription = if (state.shuffleEnabled) "Aleatorio activado" else "Aleatorio desactivado",
                        tint = if (state.shuffleEnabled) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(28.dp)
                    )
                }
                IconButton(onClick = { controller.previous() }, enabled = state.hasPrevious) {
                    Icon(Icons.Filled.SkipPrevious, contentDescription = "Anterior", modifier = Modifier.size(48.dp))
                }
                IconButton(onClick = { controller.togglePlayPause() }) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (state.isPlaying) "Pausar" else "Reproducir",
                        modifier = Modifier.size(72.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = { controller.next() }, enabled = state.hasNext) {
                    Icon(Icons.Filled.SkipNext, contentDescription = "Siguiente", modifier = Modifier.size(48.dp))
                }
                IconButton(onClick = { controller.cycleRepeatMode() }) {
                    val (icon, tint, desc) = when (state.repeatMode) {
                        RepeatMode.OFF -> Triple(Icons.Filled.Repeat, MaterialTheme.colorScheme.onSurfaceVariant, "Repetición desactivada")
                        RepeatMode.ALL -> Triple(Icons.Filled.Repeat, MaterialTheme.colorScheme.primary, "Repetir playlist")
                        RepeatMode.ONE -> Triple(Icons.Filled.RepeatOne, MaterialTheme.colorScheme.primary, "Repetir canción")
                    }
                    Icon(imageVector = icon, contentDescription = desc, tint = tint, modifier = Modifier.size(28.dp))
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(onClick = { controller.toggleAutoplay() }) {
                    Icon(
                        Icons.Filled.Stars,
                        contentDescription = if (state.autoplayEnabled) "Autoplay de sugerencias activado" else "Autoplay de sugerencias desactivado",
                        tint = if (state.autoplayEnabled) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                val downloadEntry by remember(track.id) {
                    container.downloadDao.observeById(track.id)
                }.collectAsStateWithLifecycle(initialValue = null)

                val entry = downloadEntry
                when {
                    entry?.status == DownloadStatus.COMPLETED || track.source == TrackSource.DOWNLOADED ->
                        DownloadStatusRow(
                            icon = Icons.Outlined.CloudDone,
                            tint = MaterialTheme.colorScheme.primary,
                            text = "Descargada"
                        )
                    entry?.status == DownloadStatus.RUNNING ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (entry.progress in 1..99) {
                                CircularProgressIndicator(
                                    progress = { entry.progress / 100f },
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                            Text(
                                "Descargando… ${entry.progress}%",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    entry?.status == DownloadStatus.QUEUED ->
                        DownloadStatusRow(
                            icon = Icons.Outlined.HourglassEmpty,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            text = "En cola de descarga"
                        )
                    entry?.status == DownloadStatus.FAILED ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.clickable {
                                coroutineScope.launch { container.downloadManager.enqueue(track) }
                            }
                        ) {
                            Icon(
                                Icons.Outlined.CloudOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                "Fallo al descargar — toca para reintentar",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    track.source == TrackSource.REMOTE ->
                        IconButton(onClick = {
                            coroutineScope.launch {
                                container.musicRepository.saveTracks(listOf(track))
                                container.downloadManager.enqueue(track)
                            }
                        }) {
                            Icon(Icons.Outlined.Download, contentDescription = "Descargar para offline")
                        }
                }
            }
        }
    }
}

@Composable
private fun DownloadStatusRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: androidx.compose.ui.graphics.Color,
    text: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatTime(ms: Long): String {
    if (ms < 0) return "0:00"
    val totalSec = ms / 1000
    val m = totalSec / 60
    val s = totalSec % 60
    return "%d:%02d".format(m, s)
}
