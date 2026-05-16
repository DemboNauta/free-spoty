package com.freespoty.app.ui.screens.downloads

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.freespoty.app.data.db.entities.DownloadEntry
import com.freespoty.app.data.db.entities.DownloadStatus
import com.freespoty.app.ui.rememberAppContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen() {
    val container = rememberAppContainer()
    val vm: DownloadsViewModel = viewModel(
        factory = DownloadsViewModel.Factory(container.downloadDao, container.downloadManager)
    )
    val items by vm.downloads.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Descargas") })
        if (items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Sin descargas", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Pulsa el icono de descarga junto a un resultado de búsqueda.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(items, key = { it.trackId }) { entry ->
                    DownloadRow(
                        entry = entry,
                        onCancel = { vm.cancel(entry.trackId) },
                        onDelete = { vm.delete(entry.trackId) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadRow(
    entry: DownloadEntry,
    onCancel: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (!entry.artworkUri.isNullOrBlank()) {
                AsyncImage(model = entry.artworkUri, contentDescription = null, modifier = Modifier.size(48.dp))
            } else {
                Icon(Icons.Outlined.MusicNote, contentDescription = null)
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp)
        ) {
            Text(entry.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                entry.artist ?: "—",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            when (entry.status) {
                DownloadStatus.QUEUED -> Text("En cola", style = MaterialTheme.typography.labelMedium)
                DownloadStatus.RUNNING -> {
                    LinearProgressIndicator(
                        progress = { entry.progress / 100f },
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    )
                }
                DownloadStatus.COMPLETED -> Text(
                    "Descargada",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                DownloadStatus.FAILED -> Text(
                    "Error: ${entry.errorMessage ?: "desconocido"}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        val onAction = if (entry.status == DownloadStatus.RUNNING || entry.status == DownloadStatus.QUEUED) onCancel else onDelete
        IconButton(onClick = onAction) {
            Icon(Icons.Filled.Delete, contentDescription = "Eliminar")
        }
    }
}
